<script setup>
import { computed, inject } from 'vue';
const route = inject('route');
const navigate = inject('navigate');
const entries = [
  ['applications', '应用列表'],
  ['services', '服务目录'],
  ['global', '全局配置']
];
const active = computed(() => {
  if (['services', 'service'].includes(route.value.view)) return 'services';
  if (route.value.view === 'global') return 'global';
  return 'applications';
});
function target(view) {
  return { view, clusterId: route.value.clusterId };
}
function href(view) {
  return `#${new URLSearchParams(target(view))}`;
}
</script>
<template>
  <aside class="workspace-sidebar">
    <nav aria-label="服务治理">
      <a
        v-for="[key, title] in entries"
        :key="key"
        :href="href(key)"
        :class="{ active: active === key }"
        :aria-current="active === key ? 'page' : undefined"
        @click.prevent="navigate(target(key))"
      >
        {{ title }}
      </a>
    </nav>
  </aside>
</template>
