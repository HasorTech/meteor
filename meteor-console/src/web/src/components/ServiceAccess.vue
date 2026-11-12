<script setup>
import { computed, inject, ref, watch } from 'vue';
import EndpointAddresses from './EndpointAddresses.vue';
import Feedback from './Feedback.vue';
import { useFeedback } from '../useFeedback';
const props = defineProps({ scope: Object, endpoints: Array, protocols: Array });
const api = inject('api');
const openExamples = inject('openExamples');
const selected = ref('');
const languages = ref([]);
const languageOrder = ['java', 'java-sdk', 'python', 'javascript', 'go', 'c', 'cpp', 'browser', 'shell'];
const orderedLanguages = computed(() =>
  languageOrder.map((id) => languages.value.find((item) => item.id === id)).filter(Boolean)
);
const { error, run } = useFeedback();
const protocols = computed(() =>
  [...new Set([...(props.protocols || []), ...(props.endpoints || []).map((item) => item.protocolName)])]
    .map((name) => ({
      name,
      isDefault: props.endpoints?.some((item) => item.protocolName === name && item.defaultProtocol)
    }))
    .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.name.localeCompare(b.name))
);
watch(
  protocols,
  (items) => {
    if (!items.some((item) => item.name === selected.value)) selected.value = items[0]?.name || '';
  },
  { immediate: true }
);
const endpoints = computed(() =>
  (props.endpoints || []).filter((item) => item.protocolName === selected.value)
);
const request = computed(() => {
  const endpoint = endpoints.value[0];
  const target = { ...props.scope, protocol: selected.value };
  // 仅实例服务携带实际目标地址，目录级示例沿用地址占位符。
  if (endpoint) {
    target.address = endpoint.publishedAddress || endpoint.listenAddress;
    target.endpointAddress = target.address;
    target.contextPath = endpoint.contextPath;
  }
  return target;
});
async function loadLanguages() {
  languages.value = await api('examples/languages');
}
run(loadLanguages);
function moveProtocol(event) {
  if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  const tablist = event.currentTarget.closest('[role="tablist"]');
  const buttons = [...tablist.querySelectorAll('button')];
  const columns = getComputedStyle(tablist).gridTemplateColumns.split(' ').length;
  const index = buttons.indexOf(event.currentTarget);
  let next = index;
  if (event.key === 'Home') next = 0;
  if (event.key === 'End') next = buttons.length - 1;
  if (event.key === 'ArrowLeft') next = Math.max(0, index - 1);
  if (event.key === 'ArrowRight') next = Math.min(buttons.length - 1, index + 1);
  if (event.key === 'ArrowUp') next = Math.max(0, index - columns);
  if (event.key === 'ArrowDown') next = Math.min(buttons.length - 1, index + columns);
  event.preventDefault();
  buttons[next].click();
  buttons[next].focus();
}
</script>
<template>
  <div
    v-if="protocols.length"
    class="service-access"
  >
    <aside class="access-sidebar">
      <h2>协议支持</h2>
      <nav
        class="access-protocols"
        role="tablist"
        aria-label="服务协议"
        aria-orientation="vertical"
      >
        <button
          v-for="protocol in protocols"
          :key="protocol.name"
          type="button"
          role="tab"
          :class="{ active: selected === protocol.name }"
          :aria-selected="selected === protocol.name"
          :tabindex="selected === protocol.name ? 0 : -1"
          aria-controls="access-content"
          @click="selected = protocol.name"
          @keydown="moveProtocol"
        >
          {{ protocol.name }}
          <span
            v-if="protocol.isDefault"
            class="muted"
          >
            （默认）
          </span>
        </button>
      </nav>
    </aside>
    <section
      id="access-content"
      class="access-content"
      role="tabpanel"
      :aria-label="`${selected} 接入点与示例`"
      tabindex="0"
    >
      <section
        v-if="endpoints.length"
        class="access-endpoints"
        aria-label="当前协议接入点"
      >
        <h2>接入点</h2>
        <div class="access-section-body">
          <template
            v-for="(endpoint, index) in endpoints"
            :key="index"
          >
            <EndpointAddresses
              v-for="(addresses, scheme) in endpoint.endpoints"
              :key="scheme"
              :addresses="addresses"
            />
          </template>
        </div>
      </section>
      <section
        class="access-examples"
        aria-label="当前协议调用示例"
      >
        <h2>示例</h2>
        <div class="access-section-body">
          <Feedback :error="error" />
          <button
            v-if="error"
            @click="run(loadLanguages)"
          >
            重试读取语言
          </button>
          <div class="example-language-links">
            <button
              v-for="language in orderedLanguages"
              :key="language.id"
              type="button"
              class="text-button"
              :disabled="!language.protocols?.includes(selected)"
              @click="openExamples({ ...request, language: language.id })"
            >
              {{ language.label }}
            </button>
          </div>
        </div>
      </section>
    </section>
  </div>
  <p
    v-else
    class="empty"
  >
    暂无服务接入点与示例
  </p>
</template>
