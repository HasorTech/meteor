<script setup>
import { t } from '../i18n';
import { computed, inject, ref } from 'vue';
import Feedback from './Feedback.vue';
const props = defineProps({ addresses: { type: Object, default: () => ({}) } });
const endpointTypes = inject('endpointTypes');
const copied = ref('');
const copyError = ref('');
const details = computed(() => {
  const types = endpointTypes.value;
  const rank = (key) => {
    if (key === 'endpoint') return -1;
    const index = types.findIndex((item) => item.key === key);
    return index < 0 ? types.length : index;
  };
  return Object.entries(props.addresses)
    .map(([type, target]) => ({
      type,
      target,
      label: types.find((item) => item.key === type)?.label || type
    }))
    .sort((a, b) => rank(a.type) - rank(b.type) || a.type.localeCompare(b.type));
});
function safeHttpAddress(address) {
  try {
    return ['http:', 'https:'].includes(new URL(address).protocol);
  } catch {
    return false;
  }
}
async function copyAddress(address) {
  copied.value = '';
  copyError.value = '';
  try {
    await navigator.clipboard.writeText(address);
    copied.value = address;
  } catch {
    copyError.value = t('ui.automatic-copy-failed-select-the-address');
  }
}
</script>
<template>
  <div class="endpoint-content">
    <div
      v-for="{ type, target, label } in details"
      :key="type"
      class="endpoint-line endpoint-entry"
      :class="{ 'endpoint-address': type === 'endpoint' }"
      :data-endpoint-type="type"
    >
      <span
        class="badge"
        :title="label"
      >
        {{ label }}
      </span>
      <a
        v-if="type === 'schema' && target && safeHttpAddress(target)"
        :href="target"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="$t('ui.open-new-tab', label)"
      >
        <code :title="target">
          {{ target }}
          <svg
            class="endpoint-schema-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M15 3h6v6M10 14 21 3" />
            <path d="M21 14v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5" />
          </svg>
        </code>
      </a>
      <button
        v-else-if="target"
        type="button"
        class="endpoint-copy"
        :title="copied === target ? $t('ui.address-copied') : $t('ui.click-to-copy-the-address')"
        :aria-label="$t('ui.copy-address', label)"
        @click="copyAddress(target)"
      >
        <code>{{ target }}</code>
      </button>
      <span
        v-else
        class="muted"
      >
        —
      </span>
    </div>
    <span
      v-if="!details.length"
      class="muted"
    >
      {{ $t('ui.no-endpoints-available') }}
    </span>
    <span
      class="sr-only"
      role="status"
    >
      {{ copied ? $t('ui.address-copied-to-the-clipboard') : '' }}
    </span>
    <Feedback :error="copyError" />
  </div>
</template>
