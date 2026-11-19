<script setup>
import {t} from '../i18n';
import {computed} from 'vue';

const props = defineProps({ online: Boolean, blocked: Boolean });
const status = computed(() => {
  if (!props.online) return { label: t('ui.offline'), color: 'negative' };
  if (props.blocked) return { label: t('ui.block'), color: 'warning' };
  return { label: t('ui.online'), color: 'positive' };
});
</script>

<template>
  <span
    class="status"
    :class="status.color"
  >
    {{ status.label }}
  </span>
</template>
