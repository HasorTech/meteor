<script setup>
import { t } from '../i18n';
import { computed, inject, onBeforeUnmount, ref, useId, watch } from 'vue';
import Feedback from './Feedback.vue';
import MarkdownDocument from './MarkdownDocument.vue';
const props = defineProps({
  request: { type: Object, required: true },
  languages: { type: Array, default: () => [] },
  showLanguages: Boolean
});
const api = inject('api');
const panelId = useId();
// 固定语言位置便于比较各协议能力；不支持的语言保留位置并禁用。
const languageOrder = ['java', 'java-sdk', 'python', 'javascript', 'go', 'c', 'cpp', 'browser', 'shell'];
const orderedLanguages = computed(() =>
  languageOrder.map((id) => props.languages.find((item) => item.id === id)).filter(Boolean)
);
const target = ref(null);
const rendered = ref(null);
const documents = computed(() => rendered.value?.documents || []);
const language = ref(languageId(props.request.language || 'java'));
const role = ref('caller');
const languageDocuments = computed(() =>
  documents.value.filter((item) => languageId(item.language) === language.value)
);
const roleDocuments = computed(() => ({
  definition: sharedDocument('definition', rendered.value?.interfaceDefinition),
  descriptor: sharedDocument('descriptor', rendered.value?.interfaceDescriptor),
  provider: languageDocuments.value.find((item) => item.role === 'provider'),
  caller:
    languageDocuments.value.find((item) => item.role === 'direct') ||
    languageDocuments.value.find((item) => item.role === 'consumer'),
  registry: languageDocuments.value.find((item) => item.role === 'registry'),
  files: languageDocuments.value.find((item) => item.role === 'files')
}));
const roles = computed(() => {
  const tabs = [];
  if (roleDocuments.value.definition) {
    tabs.push({ id: 'definition', label: t('ui.interface-definition') });
  }
  if (roleDocuments.value.descriptor) {
    tabs.push({ id: 'descriptor', label: t('ui.interface-descriptor') });
  }
  tabs.push(
    { id: 'provider', label: t('ui.providers') },
    { id: 'caller', label: t('ui.consumers') }
  );
  if (roleDocuments.value.registry) {
    tabs.push({ id: 'registry', label: t('ui.using-a-registry') });
  }
  if (roleDocuments.value.files) {
    tabs.push({ id: 'files', label: t('ui.example-files') });
  }
  return tabs;
});
const targetWarnings = ref([]);
const renderWarnings = ref([]);
const loadingTargets = ref(false);
const loadingDocument = ref(false);
const targetError = ref('');
const renderError = ref('');
const selectedDocument = computed(() => roleDocuments.value[role.value]);
const warnings = computed(() => [...new Set([...targetWarnings.value, ...renderWarnings.value])]);
const error = computed(() => targetError.value || renderError.value);
let active = true;
let targetsSequence = 0;
let renderSequence = 0;

function languageId(value) {
  const id = value.toLowerCase();
  return id === 'node' || id === 'node.js' ? 'javascript' : id;
}
function sharedDocument(id, markdown) {
  if (markdown) return { id, markdown };
}
function supportsRole(value) {
  return !!roleDocuments.value[value] || (!languageDocuments.value.length && value === 'caller');
}
function selectRole(value) {
  role.value = value;
}
function moveTab(event) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  const buttons = [
    ...event.currentTarget.closest('[role="tablist"]').querySelectorAll('button:not(:disabled)')
  ];
  const index = buttons.indexOf(event.currentTarget);
  let next = index;
  if (event.key === 'Home') next = 0;
  if (event.key === 'End') next = buttons.length - 1;
  if (event.key === 'ArrowLeft') next = (index + buttons.length - 1) % buttons.length;
  if (event.key === 'ArrowRight') next = (index + 1) % buttons.length;
  event.preventDefault();
  buttons[next].click();
  buttons[next].focus();
}
function exampleParams(selected) {
  return {
    ...props.request,
    serviceId: selected.serviceId,
    instanceId: selected.instanceId,
    endpointAddress: selected.address,
    contextPath: selected.endpoint.contextPath
  };
}
async function loadTargets() {
  const sequence = ++targetsSequence;
  ++renderSequence;
  loadingTargets.value = true;
  loadingDocument.value = false;
  targetError.value = '';
  renderError.value = '';
  rendered.value = null;
  target.value = null;
  targetWarnings.value = [];
  renderWarnings.value = [];
  try {
    const result = await api('examples/targets', props.request);
    if (!active || sequence !== targetsSequence) return;
    const available = result.targets || [];
    target.value = available.find((item) => item.serviceId === props.request.serviceId) || null;
    targetWarnings.value = result.warnings || [];
    if (target.value) loadDocument();
  } catch (failure) {
    if (active && sequence === targetsSequence) targetError.value = failure.message;
  } finally {
    if (active && sequence === targetsSequence) loadingTargets.value = false;
  }
}
async function loadDocument() {
  const selected = target.value;
  const sequence = ++renderSequence;
  rendered.value = null;
  renderError.value = '';
  renderWarnings.value = [];
  loadingDocument.value = false;
  if (!selected) return;
  loadingDocument.value = true;
  try {
    const result = await api('examples/render', exampleParams(selected));
    if (!active || sequence !== renderSequence) return;
    rendered.value = result;
    if (props.showLanguages && !supportsLanguage(language.value)) {
      language.value = orderedLanguages.value.find((item) => supportsLanguage(item.id))?.id || 'java';
    }
    renderWarnings.value = result.warnings || [];
    if (!supportsRole(role.value)) {
      role.value = roles.value.find((item) => supportsRole(item.id))?.id || 'caller';
    }
  } catch (failure) {
    if (active && sequence === renderSequence) renderError.value = failure.message;
  } finally {
    if (active && sequence === renderSequence) loadingDocument.value = false;
  }
}

function supportsLanguage(id) {
  return documents.value.some((item) => languageId(item.language) === id);
}
function selectLanguage(id) {
  language.value = id;
  if (!supportsRole(role.value)) role.value = supportsRole('caller') ? 'caller' : 'provider';
}
watch(
  () => props.request,
  () => {
    loadTargets();
  },
  { immediate: true }
);
onBeforeUnmount(() => {
  active = false;
  ++targetsSequence;
  ++renderSequence;
});
</script>
<template>
  <div class="invocation-example">
    <Feedback :error="error" />
    <p
      v-for="warning in warnings"
      :key="warning"
      class="notice warning"
    >
      {{ warning }}
    </p>
    <p
      v-if="loadingTargets"
      class="empty"
      role="status"
    >
      {{ $t('ui.loading-available-services') }}
    </p>
    <template v-else>
      <div
        v-if="targetError"
        class="actions"
      >
        <button @click="loadTargets">{{ $t('ui.retry') }}</button>
      </div>
      <p
        v-else-if="!target"
        class="empty"
      >
        {{ $t('ui.no-service-providers-available-for-generating') }}
      </p>
      <template v-else>
        <p
          v-if="loadingDocument"
          class="empty"
          role="status"
        >
          {{ $t('ui.generating-example') }}
        </p>
        <div
          v-else-if="renderError"
          class="actions"
        >
          <button @click="loadDocument">{{ $t('ui.regenerate') }}</button>
        </div>
        <template v-else>
          <div
            class="examples-tabbar"
            :class="{ 'with-languages': showLanguages }"
          >
            <div
              v-if="showLanguages"
              class="tabs examples-language-tabs"
              role="tablist"
              :aria-label="$t('ui.example-language')"
            >
              <button
                v-for="item in orderedLanguages"
                :key="item.id"
                type="button"
                role="tab"
                :class="{ active: language === item.id }"
                :aria-selected="language === item.id"
                :tabindex="language === item.id ? 0 : -1"
                :disabled="!supportsLanguage(item.id)"
                :title="supportsLanguage(item.id) ? undefined : $t('ui.no-examples-in-this-language-for')"
                :aria-controls="panelId"
                @click="selectLanguage(item.id)"
                @keydown="moveTab"
              >
                {{ item.label }}
              </button>
            </div>
            <div
              class="tabs examples-role-tabs"
              role="tablist"
              :aria-label="$t('ui.invocation-role')"
            >
              <button
                v-for="item in roles"
                :id="`${panelId}-role-${item.id}`"
                :key="item.id"
                type="button"
                role="tab"
                :class="{ active: role === item.id }"
                :aria-selected="role === item.id"
                :tabindex="role === item.id ? 0 : -1"
                :disabled="!supportsRole(item.id)"
                :title="
                  supportsRole(item.id) ? undefined : $t('ui.no-example-in-the-current-language', item.label)
                "
                :aria-controls="panelId"
                @click="selectRole(item.id)"
                @keydown="moveTab"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
          <section
            :id="panelId"
            role="tabpanel"
            :aria-label="$t('ui.invocation-example-document')"
            tabindex="0"
          >
            <MarkdownDocument
              v-if="selectedDocument"
              :key="selectedDocument.id"
              :markdown="selectedDocument.markdown"
              :java-description="target?.description"
            />
            <p
              v-else
              class="empty"
            >
              {{ $t('ui.examples-in-this-language-are-pending') }}
            </p>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>
