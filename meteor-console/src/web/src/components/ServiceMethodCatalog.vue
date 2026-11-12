<script setup>
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
  if (busy.value) return '正在加载方法…';
  if (error.value) return '方法未完整加载，请刷新重试。';
  if (!listingSupported.value) return '请输入完整方法名查询';
  return methodName.value.trim() ? '暂无匹配的方法' : '暂无已上报的方法';
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
      if (cursor && cursors.has(cursor)) throw new Error('方法分页未推进，请刷新重试。');
      if (cursor) cursors.add(cursor);
    } while (cursor);
    // 全部分页成功后才展示，避免把不完整列表当成完整的本地搜索结果。
    definitions.value = loaded;
  } catch (failure) {
    if (requestGeneration !== generation) return;
    error.value = failure.message || '方法加载失败';
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
        aria-label="方法查询"
        @submit.prevent="!listingSupported && load()"
      >
        <input
          v-model="methodName"
          aria-label="方法名"
          :placeholder="listingSupported ? '搜索方法名' : '完整方法名'"
          :disabled="busy"
        />
        <button
          v-if="!listingSupported"
          :disabled="busy"
          type="submit"
        >
          查询
        </button>
        <span
          v-if="!listingSupported"
          class="muted"
        >
          此注册中心支持按完整方法名查询。
        </span>
      </form>
    </template>
    <template #feedback>
      <Feedback :error="error" />
    </template>
  </ServiceMethods>
</template>
