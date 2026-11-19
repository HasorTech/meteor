<script setup>
import { t } from '../i18n';
import { computed, inject } from 'vue';
const route = inject('route');
const navigate = inject('navigate');
const entries = computed(() => [
  ['applications', t('ui.applications')],
  ['services', t('ui.service-directory')],
  ['global', t('ui.global-settings')]
]);
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
    <nav :aria-label="$t('ui.service-governance')">
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
