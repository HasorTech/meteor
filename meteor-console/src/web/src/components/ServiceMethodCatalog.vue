<script setup>
import { t } from '../i18n';
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue';
import ServiceMethods from './ServiceMethods.vue';
import Feedback from './Feedback.vue';
const props = defineProps({ scope: Object, revision: Number });
const api = inject('api');
const emit = defineEmits(['debug']);
const methodName = ref('');
const definitions = ref([]);
const listingSupported = ref(true);
const busy = ref(false);
const error = ref('');
let generation = 0;
const filteredDefinitions = computed(() => {
  const keyword = methodName.value.trim().toLowerCase();
  if (!listingSupported.value || !keyword) return definitions.value;
  return definitions.value
    .map((definition) => ({
      ...definition,
      methods: (definition.methods || []).filter((method) =>
        [method.name, method.javaName].some((name) => name?.toLowerCase().includes(keyword))
      )
    }))
    .filter((definition) => definition.methods.length);
});
const emptyText = computed(() => {
  if (busy.value) return t('ui.loading-methods');
  if (error.value) return t('ui.the-method-list-did-not-load');
  if (!listingSupported.value) return t('ui.enter-the-full-method-name');
  return methodName.value.trim() ? t('ui.no-matching-methods') : t('ui.no-reported-methods');
});
async function load() {
  const requestGeneration = ++generation;
  const queryName = listingSupported.value ? undefined : methodName.value.trim();
  busy.value = true;
  error.value = '';
  definitions.value = [];
  try {
    const loaded = [];
    let cursor;
    const cursors = new Set();
    do {
      const page = await api('service/methods', {
        ...props.scope,
        methodName: queryName,
        cursor
      });
      if (requestGeneration !== generation) return;
      listingSupported.value = page.listingSupported;
      loaded.push(...page.definitions);
      cursor = page.nextCursor;
      if (cursor && cursors.has(cursor)) throw new Error(t('ui.method-pagination-did-not-advance-refresh'));
      if (cursor) cursors.add(cursor);
    } while (cursor);
    // 全部分页成功后才展示，避免把不完整列表当成完整的本地搜索结果。
    definitions.value = loaded;
  } catch (failure) {
    if (requestGeneration !== generation) return;
    error.value = failure.message || t('ui.failed-to-load-methods');
  } finally {
    if (requestGeneration === generation) busy.value = false;
  }
}
onBeforeUnmount(() => generation++);
watch(
  () => [props.scope.clusterId, props.scope.serviceId, props.revision],
  (current, previous) => {
    if (!previous || current[0] !== previous[0] || current[1] !== previous[1]) {
      methodName.value = '';
      listingSupported.value = true;
    }
    load();
  },
  { immediate: true }
);
</script>

<template>
  <ServiceMethods
    :definitions="filteredDefinitions"
    debuggable
    @debug="emit('debug', $event)"
    :empty-text="emptyText"
  >
    <template #tools>
      <form
        class="actions"
        :aria-label="$t('ui.search-methods')"
        @submit.prevent="!listingSupported && load()"
      >
        <input
          v-model="methodName"
          :aria-label="$t('ui.method-name')"
          :placeholder="listingSupported ? $t('ui.search-method-names') : $t('ui.full-method-name')"
          :disabled="busy"
        />
        <button
          v-if="!listingSupported"
          :disabled="busy"
          type="submit"
        >
          {{ $t('ui.find') }}
        </button>
        <span
          v-if="!listingSupported"
          class="muted"
        >
          {{ $t('ui.this-registry-supports-search-by-full') }}
        </span>
      </form>
    </template>
    <template #feedback>
      <Feedback :error="error" />
    </template>
  </ServiceMethods>
</template>
