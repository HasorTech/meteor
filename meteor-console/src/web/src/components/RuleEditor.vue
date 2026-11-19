<script setup>
import {t} from '../i18n';
import {computed, inject, onBeforeUnmount, ref, watch} from 'vue';
import {useFeedback} from '../useFeedback';
import {trackDraft} from '../useNavigationGuard';
import Feedback from './Feedback.vue';

const props = defineProps({ scope: Object, type: String });
const api = inject('api');
const { busy, error, success, run } = useFeedback();
const loaded = ref(null);
const loading = ref(false);
const ready = ref(false);
let loadSequence = 0;
let active = true;
const rule = ref('');
const original = ref('');
const dirty = computed(() => rule.value !== original.value);
trackDraft(() => dirty.value);
const query = computed(() => ({ ...props.scope, type: props.type }));
async function load() {
  const sequence = ++loadSequence;
  loading.value = true;
  ready.value = false;
  rule.value = '';
  original.value = '';
  error.value = '';
  success.value = '';
  try {
    const result = await api('rule', query.value);
    if (!active || sequence !== loadSequence) return;
    loaded.value = result;
    rule.value = result.rule ?? '';
    original.value = rule.value;
    ready.value = true;
  } catch (failure) {
    if (active && sequence === loadSequence) error.value = failure.message || t('ui.failed-to-read-the-rule');
  } finally {
    if (active && sequence === loadSequence) loading.value = false;
  }
}
function save() {
  if (!ready.value) return;
  const sequence = loadSequence;
  run(async () => {
    await api('rule', query.value, 'PUT', { rule: rule.value });
    if (!active || sequence !== loadSequence) return;
    original.value = rule.value;
    success.value = t('ui.saved');
  });
}
function deleteRule() {
  if (!ready.value) return;
  if (!window.confirm(t('ui.delete-the-current-rule-this-clears'))) return;
  const sequence = loadSequence;
  run(async () => {
    await api('rule', query.value, 'DELETE');
    if (!active || sequence !== loadSequence) return;
    await load();
    success.value = t('ui.deleted');
  });
}
function toggle() {
  if (!ready.value) return;
  const sequence = loadSequence;
  run(async () => {
    await api('rule/enabled', query.value, 'PUT', { enabled: !loaded.value.enabled });
    if (!active || sequence !== loadSequence) return;
    loaded.value.enabled = !loaded.value.enabled;
    success.value = loaded.value.enabled ? t('ui.enabled') : t('ui.disabled');
  });
}
watch(query, load, { immediate: true });
onBeforeUnmount(() => {
  active = false;
  loadSequence++;
});
</script>
<template>
  <section class="rule-editor">
    <Feedback
      :error="error"
      :success="success"
    />
    <div
      class="rule-script"
      :aria-busy="loading"
    >
      <div class="rule-script-toolbar">
        <div class="rule-script-title">
          <strong>{{ $t('ui.rule-script') }}</strong>
          <span
            v-if="loading"
            class="muted small"
            role="status"
          >
            {{ $t('ui.loading') }}
          </span>
          <button
            v-else-if="!ready"
            class="text-button"
            @click="load"
          >
            {{ $t('ui.reload') }}
          </button>
          <span
            v-if="dirty"
            class="muted small"
          >
            {{ $t('ui.unsaved') }}
          </span>
        </div>
        <div class="rule-script-controls">
          <label
            v-if="loaded?.switchSupported"
            class="switch-label"
          >
            <span>{{ loaded.enabled ? $t('ui.enabled') : $t('ui.disabled') }}</span>
            <button
              class="switch"
              role="switch"
              :aria-label="$t('ui.enable-rule')"
              :aria-checked="loaded.enabled"
              :class="{ checked: loaded.enabled }"
              :disabled="busy || !ready"
              @click="toggle"
            >
              <span />
            </button>
          </label>
        </div>
      </div>
      <div
        class="rule-script-body"
        :class="{ 'route-layout': $slots.navigation }"
      >
        <slot
          name="navigation"
          :busy="busy"
        />
        <div class="rule-script-content">
          <textarea
            v-model="rule"
            :aria-label="$t('ui.rule-content')"
            class="code-editor"
            spellcheck="false"
            :disabled="busy"
            :readonly="!ready"
            :placeholder="
              !ready
                ? ''
                : type === 'FLOW_CONTROL'
                  ? '<controlSet/>'
                  : `<route>\n  <return value=&quot;addresses&quot;/>\n</route>`
            "
          />
          <div class="actions governance-actions">
            <button
              class="primary"
              :disabled="busy || !ready"
              @click="save"
            >
              {{ $t('ui.save') }}
            </button>
            <button
              class="danger rule-delete"
              :disabled="busy || !ready"
              @click="deleteRule"
            >
              {{ $t('ui.delete') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
