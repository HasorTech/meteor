<script setup>
const props = defineProps({ id: String, modelValue: String, disabled: Boolean });
const emit = defineEmits(['update:modelValue', 'error']);

function format() {
  try {
    emit('update:modelValue', JSON.stringify(JSON.parse(props.modelValue), null, 2));
    emit('error', '');
  } catch (failure) {
    emit('error', failure.message);
  }
}
</script>

<template>
  <div class="json-parameter-editor">
    <textarea
      :id="id"
      :value="modelValue"
      :rows="Math.min(12, Math.max(2, (modelValue || '').split('\n').length))"
      class="debug-parameters debug-argument-control code-editor"
      spellcheck="false"
      :disabled="disabled"
      @input="emit('update:modelValue', $event.target.value)"
    />
    <button
      class="json-format-button"
      type="button"
      aria-label="格式化"
      title="格式化 JSON"
      :disabled="disabled"
      @click="format"
    >
      <svg
        viewBox="0 0 24 24"
        width="16"
        height="16"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
      >
        <path d="m14 3 7 7-10 10H6l-4-4L14 3Z" />
        <path d="m8 9 7 7M11 20h10" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.json-parameter-editor {
  position: relative;
}
.json-parameter-editor textarea {
  display: block;
  padding-right: 44px;
}
.json-format-button {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  min-height: 0;
  padding: 0;
  color: var(--muted);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
}
.json-format-button:hover:not(:disabled) {
  color: #2563eb;
  background: #e8efff;
  border-color: #c5d5fa;
}
</style>
