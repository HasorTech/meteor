<script setup>
import {computed} from 'vue';
import {t} from '../i18n';
import {usePageTab} from '../navigation';
import RuleEditor from './RuleEditor.vue';

const props = defineProps({ scope: Object });
const { selected: level, select } = usePageTab(
  'SERVICE_ROUTE',
  ['SERVICE_ROUTE', 'METHOD_ROUTE', 'ARGS_ROUTE'],
  'level'
);
const levels = computed(() => [
  ['SERVICE_ROUTE', t('ui.service-level')],
  ['METHOD_ROUTE', t('ui.method-level')],
  ['ARGS_ROUTE', t('ui.argument-level')]
]);
</script>
<template>
  <RuleEditor
    :scope="scope"
    :type="level"
  >
    <template #navigation="{ busy }">
      <nav
        class="route-tabs"
        :aria-label="$t('ui.routing-level')"
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
