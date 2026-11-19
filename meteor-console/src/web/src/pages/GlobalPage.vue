<script setup>
import {computed} from 'vue';
import {t} from '../i18n';
import {usePageTab} from '../navigation';
import RuleEditor from '../components/RuleEditor.vue';
import RouteEditor from '../components/RouteEditor.vue';
import MetadataEditor from '../components/MetadataEditor.vue';

const props = defineProps({ scope: Object });
const { selected: tab, select } = usePageTab('flow', ['flow', 'route', 'metadata']);
const tabs = computed(() => [
  ['flow', t('ui.traffic-control')],
  ['route', t('ui.routing-policies')],
  ['metadata', t('ui.metadata')]
]);
</script>
<template>
  <div class="tabbar detail-tabbar page-actionbar">
    <nav
      class="tabs"
      :aria-label="$t('ui.global-settings')"
    >
      <button
        v-for="[key, name] in tabs"
        :key="key"
        :class="{ active: tab === key }"
        @click="select(key)"
      >
        {{ name }}
      </button>
    </nav>
    <div
      class="toolbar"
      id="global-actions"
    />
  </div>
  <MetadataEditor
    toolbar="#global-actions"
    v-if="tab === 'metadata'"
    :scope="{ ...scope, scope: 'cluster' }"
  />
  <RuleEditor
    v-else-if="tab === 'flow'"
    :scope="{ ...scope, scope: 'cluster' }"
    type="FLOW_CONTROL"
  />
  <RouteEditor
    v-else
    :scope="{ ...scope, scope: 'cluster' }"
  />
</template>
