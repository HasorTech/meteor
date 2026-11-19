<script setup>
defineProps({
  count: Number,
  label: String,
  busy: Boolean,
  previous: Boolean,
  next: Boolean,
  showControls: {type: Boolean, default: true}
});
defineEmits(['previous', 'next']);
</script>
<template>
  <div class="directory-pager">
    <span class="muted">
      <slot>{{ $t('ui.directory-page-count', count, label) }}</slot>
    </span>
    <div
        v-if="showControls"
        class="toolbar"
    >
      <button
          :disabled="busy || !previous"
          @click="$emit('previous')"
      >
        {{ $t('ui.previous') }}
      </button>
      <button
          :disabled="busy || !next"
          @click="$emit('next')"
      >
        {{ $t('ui.next') }}
      </button>
    </div>
  </div>
</template>
