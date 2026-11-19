<script setup>
defineProps({ enabled: Boolean, on: String, off: String });
</script>
<template>
  <span
    class="status"
    :class="enabled ? 'positive' : 'negative'"
  >
    {{ enabled ? (on ?? $t('ui.enable')) : (off ?? $t('ui.disable')) }}
  </span>
</template>
