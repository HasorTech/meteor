<script setup>
import {computed, onBeforeUnmount, ref, useId} from 'vue';
import ProtocolBadges from './ProtocolBadges.vue';

const props = defineProps({
  protocols: {type: Array, default: () => []}
});
const protocols = computed(() => [...new Set(props.protocols)]);
const trigger = ref(null);
const panel = ref(null);
const panelId = useId();
const expanded = ref(false);
let closeTimer;

function cancelClose() {
  clearTimeout(closeTimer);
}

function close() {
  cancelClose();
  panel.value?.hidePopover();
}

function deferClose() {
  cancelClose();
  closeTimer = setTimeout(() => {
    if (!panel.value?.contains(document.activeElement) && document.activeElement !== trigger.value) close();
  }, 150);
}

function show() {
  cancelClose();
  const element = panel.value;
  element.showPopover();
  const anchor = trigger.value.getBoundingClientRect();
  const bounds = element.getBoundingClientRect();
  element.style.left = `${Math.max(8, Math.min(anchor.left, innerWidth - bounds.width - 8))}px`;
  element.style.top = `${anchor.bottom + bounds.height + 8 <= innerHeight ? anchor.bottom + 6 : Math.max(8, anchor.top - bounds.height - 6)}px`;
}

function focusOut(event) {
  if (!panel.value?.contains(event.relatedTarget) && event.relatedTarget !== trigger.value) close();
}

function toggle(event) {
  expanded.value = event.newState === 'open';
  if (expanded.value) {
    window.addEventListener('resize', close);
    window.addEventListener('scroll', scroll, true);
  } else {
    window.removeEventListener('resize', close);
    window.removeEventListener('scroll', scroll, true);
  }
}

function scroll(event) {
  if (!panel.value?.contains(event.target)) close();
}

onBeforeUnmount(() => {
  cancelClose();
  window.removeEventListener('resize', close);
  window.removeEventListener('scroll', scroll, true);
});
</script>

<template>
  <template v-if="protocols.length">
    <button
        ref="trigger"
        type="button"
        class="badge protocol-summary"
        :aria-label="$t('ui.view-all-protocols', protocols.length)"
        :aria-expanded="expanded"
        :aria-controls="panelId"
        @mouseenter="show"
        @mouseleave="deferClose"
        @focus="show"
        @blur="focusOut"
        @click.stop="show"
        @keydown.esc="close"
    >
      +{{ protocols.length }}
    </button>
    <div
        :id="panelId"
        ref="panel"
        popover="auto"
        class="protocol-popover"
        role="region"
        :aria-label="$t('ui.supported-protocols')"
        @toggle="toggle"
        @mouseenter="cancelClose"
        @mouseleave="deferClose"
        @focusout="focusOut"
    >
      <div class="protocol-popover-heading">
        <strong>{{ $t('ui.supported-protocols') }}</strong>
        <span class="muted">{{ $t('ui.protocol-count', protocols.length) }}</span>
      </div>
      <ProtocolBadges :protocols="protocols"/>
    </div>
  </template>
  <span
      v-else
      class="muted"
  >
    —
  </span>
</template>
