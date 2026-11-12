<script setup>
defineProps({ protocols: { type: Array, default: () => [] } });
</script>
<template>
  <span
    class="protocols"
    @click.stop
  >
    <span
      v-for="protocol in [...new Set(protocols)]"
      :key="protocol"
      class="badge"
    >
      {{ protocol }}
    </span>
    <span
      v-if="!protocols.length"
      class="muted"
    >
      —
    </span>
  </span>
</template>
