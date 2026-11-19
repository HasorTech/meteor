<script setup>
import {onBeforeUnmount, onMounted, ref} from 'vue';
import InvocationExample from './InvocationExample.vue';

const props = defineProps({request: {type: Object, required: true}});
const emit = defineEmits(['close']);
const dialog = ref(null);
let previousFocus;
let previousOverflow;
onMounted(() => {
  previousFocus = document.activeElement;
  previousOverflow = document.body.style.overflow;
  document.body.style.overflow = 'hidden';
  dialog.value.showModal();
});
onBeforeUnmount(() => {
  dialog.value?.close();
  document.body.style.overflow = previousOverflow;
  if (previousFocus?.isConnected) previousFocus.focus();
});
</script>
<template>
  <dialog
      ref="dialog"
      class="examples-drawer"
      aria-labelledby="examples-title"
      @cancel.prevent="emit('close')"
      @click="$event.target === dialog && emit('close')"
  >
    <section class="examples-panel">
      <header class="examples-header">
        <h2
            id="examples-title"
            class="examples-title"
        >
          <span class="badge">{{ request.protocol }}</span>
          <span class="examples-service-id">{{ request.serviceId }}</span>
        </h2>
        <button
            type="button"
            :aria-label="$t('ui.close-protocol-examples')"
            autofocus
            @click="emit('close')"
        >
          {{ $t('ui.close') }}
        </button>
      </header>
      <div class="examples-body">
        <InvocationExample :request="props.request"/>
      </div>
    </section>
  </dialog>
</template>
