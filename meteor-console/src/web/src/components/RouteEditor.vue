<script setup>
import { usePageTab } from '../navigation';
import RuleEditor from './RuleEditor.vue';
const props = defineProps({ scope: Object });
const { selected: level, select } = usePageTab(
  'SERVICE_ROUTE',
  ['SERVICE_ROUTE', 'METHOD_ROUTE', 'ARGS_ROUTE'],
  'level'
);
const levels = [
  ['SERVICE_ROUTE', '服务级'],
  ['METHOD_ROUTE', '方法级'],
  ['ARGS_ROUTE', '参数级']
];
</script>
<template>
  <RuleEditor
    :scope="scope"
    :type="level"
  >
    <template #navigation="{ busy }">
      <nav
        class="route-tabs"
        aria-label="路由级别"
      >
        <button
          v-for="[key, name] in levels"
          :key="key"
          :class="{ active: level === key }"
          :disabled="busy"
          @click="select(key)"
        >
          {{ name }}
        </button>
      </nav>
    </template>
  </RuleEditor>
</template>
