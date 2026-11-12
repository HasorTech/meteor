<script setup>
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
const documents = ref([]);
const language = ref(languageId(props.request.language || 'java'));
const role = ref('caller');
const roles = [
  { id: 'provider', label: '提供者' },
  { id: 'caller', label: '消费者' }
];
const targetWarnings = ref([]);
const renderWarnings = ref([]);
const loadingTargets = ref(false);
const loadingDocument = ref(false);
const targetError = ref('');
const renderError = ref('');
const selectedDocument = computed(() => {
  const available = documents.value.filter((item) => languageId(item.language) === language.value);
  if (role.value === 'provider') return available.find((item) => item.role === 'provider');
  return (
    available.find((item) => item.role === 'direct') || available.find((item) => item.role === 'consumer')
  );
});
const warnings = computed(() => [...new Set([...targetWarnings.value, ...renderWarnings.value])]);
const error = computed(() => targetError.value || renderError.value);
let active = true;
let targetsSequence = 0;
let renderSequence = 0;

function languageId(value) {
  const id = value.toLowerCase();
  return id === 'node' || id === 'node.js' ? 'javascript' : id;
}
function supportsRole(value) {
  const available = documents.value.filter((item) => languageId(item.language) === language.value);
  return available.length
    ? available.some((item) =>
        value === 'provider' ? item.role === 'provider' : ['direct', 'consumer'].includes(item.role)
      )
    : value === 'caller';
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
  documents.value = [];
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
  documents.value = [];
  renderError.value = '';
  renderWarnings.value = [];
  loadingDocument.value = false;
  if (!selected) return;
  loadingDocument.value = true;
  try {
    const result = await api('examples/render', exampleParams(selected));
    if (!active || sequence !== renderSequence) return;
    documents.value = result.documents || [];
    if (props.showLanguages && !supportsLanguage(language.value)) {
      language.value = orderedLanguages.value.find((item) => supportsLanguage(item.id))?.id || 'java';
    }
    renderWarnings.value = result.warnings || [];
    if (!supportsRole(role.value)) {
      role.value = roles.find((item) => supportsRole(item.id))?.id || 'caller';
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
      正在读取可用服务…
    </p>
    <template v-else>
      <div
        v-if="targetError"
        class="actions"
      >
        <button @click="loadTargets">重试</button>
      </div>
      <p
        v-else-if="!target"
        class="empty"
      >
        没有可生成示例的服务提供者
      </p>
      <template v-else>
        <p
          v-if="loadingDocument"
          class="empty"
          role="status"
        >
          正在生成示例…
        </p>
        <div
          v-else-if="renderError"
          class="actions"
        >
          <button @click="loadDocument">重试生成</button>
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
              aria-label="示例语言"
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
                :title="supportsLanguage(item.id) ? undefined : '当前服务暂无此语言示例'"
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
              aria-label="调用角色"
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
                :title="supportsRole(item.id) ? undefined : `当前语言暂无${item.label}示例`"
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
            aria-label="调用示例文档"
            tabindex="0"
          >
            <MarkdownDocument
              v-if="selectedDocument"
              :key="selectedDocument.id"
              :markdown="selectedDocument.markdown"
            />
            <p
              v-else
              class="empty"
            >
              当前语言的示例待补充
            </p>
          </section>
        </template>
      </template>
    </template>
  </div>
</template>
