<script setup>
defineProps({ modelValue: String });
defineEmits(['update:modelValue']);
</script>
<template>
  <button
    class="icon-button"
    :aria-label="modelValue === 'list' ? $t('ui.switch-to-cards') : $t('ui.switch-to-list')"
    :title="modelValue === 'list' ? $t('ui.switch-to-cards') : $t('ui.switch-to-list')"
    @click="$emit('update:modelValue', modelValue === 'list' ? 'cards' : 'list')"
  >
    <span
      :class="modelValue === 'list' ? 'grid-icon' : 'list-icon'"
      aria-hidden="true"
    >
      <i
        v-for="n in 4"
        :key="n"
      />
    </span>
  </button>
</template>
