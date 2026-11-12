<script setup>
defineProps({
  count: Number,
  label: String,
  busy: Boolean,
  previous: Boolean,
  next: Boolean,
  showControls: { type: Boolean, default: true }
});
defineEmits(['previous', 'next']);
</script>
<template>
  <div class="directory-pager">
    <span class="muted">
      <slot>本页 {{ count }} 个{{ label }}</slot>
    </span>
    <div
      v-if="showControls"
      class="toolbar"
    >
      <button
        :disabled="busy || !previous"
        @click="$emit('previous')"
      >
        上一页
      </button>
      <button
        :disabled="busy || !next"
        @click="$emit('next')"
      >
        下一页
      </button>
    </div>
  </div>
</template>
