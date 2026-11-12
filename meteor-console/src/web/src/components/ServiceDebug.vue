<script setup>
import { computed, nextTick, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import Feedback from './Feedback.vue';
import JsonParameterEditor from './JsonParameterEditor.vue';
import DirectoryPager from './DirectoryPager.vue';
import { useDirectory } from '../useDirectory';
import chevronUp from '../assets/icons/chevron-up.svg';

const props = defineProps({ scope: Object, instance: Object });
const api = inject('api');
const settings = inject('consoleSettings');
const route = inject('route');
const data = ref(null);
const endpointIndex = ref('0');
const selectedSignature = route.value.method || '';
const chooseProvider = !props.scope.instanceId && props.scope.mode !== 'direct';
const providerId = ref(props.scope.instanceId || props.instance?.instanceId || '');
const providerFilter = ref('');
const selectedProviders = ref({});
const providerSelectionMode = ref('single');
const onlySelectedProviders = ref(false);
const selectedProviderFilter = ref('');
const lastSelectedProviderId = ref('');
const providerSearch = computed({
  get: () => (onlySelectedProviders.value ? selectedProviderFilter.value : providerFilter.value),
  set: (value) => {
    if (onlySelectedProviders.value) selectedProviderFilter.value = value;
    else providerFilter.value = value;
  }
});
const results = ref([]);
const resultExpanded = ref(true);
const activeResultId = ref('');
const resultTabs = ref(null);
const activeResult = computed(
  () => results.value.find((row) => row.instance.instanceId === activeResultId.value) || results.value[0]
);
const resultCounts = computed(() => {
  const counts = { success: 0, failure: 0, pending: 0 };
  for (const row of results.value) {
    if (row.state === '成功') counts.success++;
    else if (row.state === '失败') counts.failure++;
    else counts.pending++;
  }
  return counts;
});
watch(results, (rows) => {
  activeResultId.value = rows[0]?.instance.instanceId || '';
});
const requestExpanded = ref(true);
let initializedProviders = false;
const providers = useDirectory(
  () => 'service/members',
  () => ({
    ...props.scope,
    scope: 'service',
    role: 'providers',
    field: 'instance',
    filter: providerFilter.value
  }),
  () => chooseProvider && !!selectedSignature
);
const selectedProviderCount = computed(() => Object.keys(selectedProviders.value).length);
const providerChoices = computed(() => {
  if (!chooseProvider) return props.instance ? [{ instance: props.instance, enabled: true }] : [];
  if (!onlySelectedProviders.value) return providers.items.value;
  const filter = selectedProviderFilter.value.trim().toLowerCase();
  return Object.values(selectedProviders.value).filter(({ instance }) =>
    `${instance.ipAddress || ''} ${instance.processId ?? ''}`.toLowerCase().includes(filter)
  );
});
const unavailableSelection = computed(() =>
  Object.values(selectedProviders.value).some((member) => !providerAvailable(member))
);
function providerAvailable(member) {
  return member.enabled && member.instance.online;
}
function providerStatus(member) {
  if (!member.instance.online) return '离线';
  return member.enabled ? '在线' : '禁用';
}
const targetScope = computed(() => ({
  ...props.scope,
  instanceId: providerId.value || props.scope.instanceId
}));
watch(providers.items, (items) => {
  const selected = { ...selectedProviders.value };
  for (const member of items) {
    if (selected[member.instance.instanceId]) selected[member.instance.instanceId] = member;
  }
  selectedProviders.value = selected;
  if (!initializedProviders) {
    const first = items.find(providerAvailable);
    if (first) {
      initializedProviders = true;
      toggleProvider(first);
    }
  }
});
function toggleProvider(member) {
  if (!chooseProvider || sending.value || (!onlySelectedProviders.value && providers.busy.value)) return;
  const id = member.instance.instanceId;
  const selected = { ...selectedProviders.value };
  if (!providerAvailable(member) && !selected[id]) return;
  if (providerSelectionMode.value === 'single') {
    if (selected[id]) return;
    selectedProviders.value = { [id]: member };
  } else {
    if (selected[id]) delete selected[id];
    else selected[id] = member;
    selectedProviders.value = selected;
  }
  lastSelectedProviderId.value = selectedProviders.value[id]
    ? id
    : Object.keys(selectedProviders.value).at(-1) || '';
  results.value = [];
  // 提供者勾选只决定调用目标，工作区契约由首次选中的实例提供预览。
  if (!providerId.value) providerId.value = id;
}
watch(providerSelectionMode, (mode) => {
  if (mode === 'single') {
    const current =
      selectedProviders.value[lastSelectedProviderId.value] || Object.values(selectedProviders.value)[0];
    selectedProviders.value = current ? { [current.instance.instanceId]: current } : {};
  }
  results.value = [];
});
function clearProviders() {
  selectedProviders.value = {};
  lastSelectedProviderId.value = '';
  results.value = [];
}
function providerRowClick(event, member) {
  if (!event.target.closest('input')) toggleProvider(member);
}
function resultStatus(row) {
  return row.state === '失败' ? 'status negative' : row.state === '成功' ? 'status positive' : 'muted';
}
function resultKeydown(event, index) {
  const offsets = { ArrowDown: 1, ArrowUp: -1, Home: -index, End: results.value.length - 1 - index };
  if (!(event.key in offsets)) return;
  event.preventDefault();
  const next = (index + offsets[event.key] + results.value.length) % results.value.length;
  activeResultId.value = results.value[next].instance.instanceId;
  nextTick(() => resultTabs.value?.querySelectorAll('[role="tab"]')[next]?.focus());
}
watch(providerId, load);
const parameters = ref([]);
const timeout = ref(5000);
const loading = ref(true);
const sending = ref(false);
const error = ref('');
const singleResult = computed(() => (results.value.length === 1 ? results.value[0] : null));
let active = true;
let generation = 0;
const endpoint = computed(() => data.value?.endpoints?.[Number(endpointIndex.value)]);
function protocolAvailable(item) {
  return (settings.value?.features.debugProtocols || []).includes(item?.endpoint.protocolName);
}
const protocolOptions = computed(() => {
  const protocols = new Set();
  return (data.value?.endpoints || []).flatMap((item, index) => {
    if (protocols.has(item.endpoint.protocolName)) return [];
    protocols.add(item.endpoint.protocolName);
    return [{ item, index }];
  });
});
const description = computed(() => endpoint.value?.description || data.value?.description);
const methods = computed(() => description.value?.methods || []);
const method = computed(() => methods.value.find((item) => item.javaSignature === selectedSignature));

function sample(reference, visited = new Set()) {
  if (visited.has(reference)) return null;
  const type = description.value?.types?.[reference] || { javaType: reference };
  if (type.kind === 'array' || type.kind === 'collection') return [];
  if (type.kind === 'map') return {};
  if (type.kind === 'enum') return type.values?.[0]?.javaName || type.values?.[0]?.name || '';
  if (type.kind === 'object') {
    const seen = new Set([...visited, reference]);
    return Object.fromEntries(
      (type.properties || []).map((field) => [field.javaName || field.name, sample(field.type, seen)])
    );
  }
  if (['boolean', 'java.lang.Boolean'].includes(type.javaType)) return false;
  if (['java.lang.String', 'char', 'java.lang.Character'].includes(type.javaType)) return '';
  if (
    [
      'byte',
      'short',
      'int',
      'long',
      'float',
      'double',
      'java.lang.Byte',
      'java.lang.Short',
      'java.lang.Integer',
      'java.lang.Long',
      'java.lang.Float',
      'java.lang.Double'
    ].includes(type.javaType)
  )
    return 0;
  return null;
}

const numericTypes = new Set([
  'byte',
  'short',
  'int',
  'long',
  'float',
  'double',
  'java.lang.Byte',
  'java.lang.Short',
  'java.lang.Integer',
  'java.lang.Long',
  'java.lang.Float',
  'java.lang.Double',
  'java.math.BigDecimal',
  'java.math.BigInteger'
]);
const temporalTypes = new Set([
  'java.time.LocalDate',
  'java.time.LocalTime',
  'java.time.LocalDateTime',
  'java.time.Instant',
  'java.util.Date'
]);
const argumentsForm = computed(() =>
  (method.value?.parameters || []).map((field, index) => {
    const type = description.value?.types?.[field.type];
    const javaType = type?.javaType || field.type;
    const structured = ['object', 'array', 'collection', 'map'].includes(type?.kind);
    let kind = structured || !type ? 'json' : 'text';
    if (numericTypes.has(javaType)) kind = 'number';
    else if (['boolean', 'java.lang.Boolean'].includes(javaType)) kind = 'boolean';
    else if (
      temporalTypes.has(javaType) ||
      type?.kind === 'enum' ||
      type?.kind === 'scalar' ||
      ['java.lang.String', 'char', 'java.lang.Character'].includes(javaType)
    )
      kind = 'text';
    const mapping =
      endpoint.value?.parameterTypes?.[javaType] || endpoint.value?.parameterTypes?.[`@${type?.kind}`];
    const fallbackComponent = kind === 'json' || javaType === 'java.lang.String' ? 'textarea' : 'input';
    const component = ['input', 'textarea', 'json'].includes(mapping?.component)
      ? mapping.component
      : fallbackComponent;
    return {
      name: field.javaName || field.name || `arg${index + 1}`,
      reference: field.type,
      javaType,
      kind,
      component,
      typeLabel: mapping?.type || (kind === 'json' ? 'JSON' : javaType.replace('java.lang.', '')),
      defaultValue: mapping?.defaultValue
    };
  })
);

function reset() {
  results.value = [];
  parameters.value = argumentsForm.value.map((field) => {
    if (field.defaultValue != null) return field.defaultValue;
    if (field.kind === 'number') return '0';
    if (field.kind === 'boolean') return 'false';
    const value = sample(field.reference);
    if (field.kind === 'json') return JSON.stringify(value, null, 2);
    return value == null ? '' : String(value);
  });
}
// 相同参数结构切换协议时保留输入；参数结构变化时重新生成表单。
watch(() => JSON.stringify(argumentsForm.value), reset);

function argumentValue(field, index) {
  const value = parameters.value[index];
  try {
    if (field.kind === 'text') return value;
    if (field.kind === 'boolean') {
      if (value === 'true') return true;
      if (value === 'false') return false;
      throw new Error('请输入 true 或 false');
    }
    if (field.kind === 'number') {
      if (!value?.trim() || !Number.isFinite(Number(value))) throw new Error('请输入有效数字');
      const number = Number(value);
      if (
        !['float', 'double', 'java.lang.Float', 'java.lang.Double', 'java.math.BigDecimal'].includes(
          field.javaType
        ) &&
        !Number.isInteger(number)
      )
        throw new Error('请输入整数');
      return number;
    }
    return JSON.parse(value);
  } catch (failure) {
    throw new Error(`${field.name}：${failure.message}`);
  }
}

function selectEndpoint(index) {
  results.value = [];
  if (index !== undefined) endpointIndex.value = String(index);
  if (endpoint.value && !protocolAvailable(endpoint.value)) {
    error.value = `当前控制台未安装 ${endpoint.value.endpoint.protocolName} 协议插件，无法调试。`;
  } else {
    error.value = method.value ? '' : `当前提供者的所选协议不支持方法 ${selectedSignature}，请选择其他协议。`;
  }
}

async function load() {
  const current = ++generation;
  if (!selectedSignature || (chooseProvider && !providerId.value)) {
    loading.value = false;
    return;
  }
  loading.value = true;
  error.value = '';
  const previousProtocol = endpoint.value?.endpoint.protocolName;
  const initialized = !!data.value;
  try {
    const result = await api('service/debug', targetScope.value);
    if (!active || current !== generation) return;
    data.value = result;
    const previous = (result.endpoints || []).findIndex(
      (item) => item.endpoint.protocolName === previousProtocol && protocolAvailable(item)
    );
    const compatible = (result.endpoints || []).findIndex(
      (item) =>
        protocolAvailable(item) &&
        (item.description || result.description)?.methods?.some(
          (candidate) => candidate.javaSignature === selectedSignature
        )
    );
    selectEndpoint(previous >= 0 ? previous : Math.max(compatible, 0));
    if (!initialized) reset();
    if (!result.endpoints?.length) error.value = '当前提供者没有可调试的在线接入点。';
  } catch (failure) {
    if (active && current === generation) error.value = failure.message;
  } finally {
    if (active && current === generation) loading.value = false;
  }
}

async function refresh() {
  await Promise.all([load(), chooseProvider ? providers.refresh() : Promise.resolve()]);
}

async function invoke() {
  if (sending.value || loading.value) return;
  if (!protocolAvailable(endpoint.value)) {
    error.value = '当前控制台未安装所选协议插件，无法调试。';
    return;
  }
  error.value = '';
  results.value = [];
  let argumentsValue;
  try {
    argumentsValue = argumentsForm.value.map(argumentValue);
  } catch (failure) {
    requestExpanded.value = true;
    error.value = `请求参数无效：${failure.message}`;
    return;
  }
  const protocol = endpoint.value.endpoint.protocolName;
  const requestTimeout = Number(timeout.value);
  const targets = chooseProvider
    ? Object.values(selectedProviders.value).map((member) => member.instance)
    : [props.instance || { instanceId: props.scope.instanceId }];
  if (!targets.length) {
    error.value = '请选择提供者。';
    return;
  }
  if (unavailableSelection.value) {
    error.value = '已选提供者中有离线或禁用实例，请先取消选择。';
    return;
  }
  sending.value = true;
  results.value = targets.map((instance) => ({
    instance,
    state: '等待中',
    response: null,
    error: ''
  }));
  let next = 0;
  async function worker() {
    while (active && next < targets.length) {
      const index = next++;
      const row = results.value[index];
      row.state = '调用中';
      const scope = { ...props.scope, instanceId: targets[index].instanceId };
      try {
        // 每个实例读取自己的契约和地址，不借用其他实例的协议支持或服务绑定。
        const contract = chooseProvider ? await api('service/debug', scope) : data.value;
        if (!active) return;
        const target = (contract.endpoints || []).find(
          (item) =>
            item.endpoint.protocolName === protocol &&
            (item.description || contract.description)?.methods?.some(
              (candidate) => candidate.javaSignature === selectedSignature
            )
        );
        if (!target) throw new Error('当前提供者的所选协议不支持此方法。');
        const result = await api(
          'service/debug',
          scope,
          'POST',
          {
            protocol,
            address: target.address,
            contextPath: target.endpoint.contextPath,
            method: selectedSignature,
            arguments: argumentsValue,
            timeout: requestTimeout
          },
          { timeout: 40000 }
        );
        if (!active) return;
        row.response = result;
        row.state = result.success ? '成功' : '失败';
      } catch (failure) {
        if (!active) return;
        row.error = failure.message || '调用失败';
        row.state = '失败';
      }
    }
  }
  try {
    // 五个 worker 同时处理队列；一个实例失败不影响其余实例。
    await Promise.all(Array.from({ length: Math.min(5, targets.length) }, () => worker()));
  } finally {
    if (active) sending.value = false;
  }
}

onMounted(load);
onBeforeUnmount(() => {
  active = false;
});
</script>
<template>
  <Teleport to="#page-actions">
    <button
      :disabled="loading || sending || providers.busy.value"
      @click="refresh"
    >
      刷新
    </button>
  </Teleport>
  <section
    v-if="selectedSignature && (chooseProvider || data || instance)"
    class="debug-summary"
    :class="{ 'debug-with-providers': chooseProvider || instance }"
  >
    <section aria-label="基本信息">
      <dl class="debug-facts">
        <div>
          <dt>服务 ID</dt>
          <dd>{{ scope.serviceId }}</dd>
        </div>
        <div>
          <dt>调用方法</dt>
          <dd>
            <code>{{ selectedSignature }}</code>
          </dd>
        </div>
      </dl>
    </section>
    <section
      v-if="chooseProvider || instance"
      class="debug-providers"
      aria-label="提供者选择"
    >
      <div class="debug-provider-heading">
        <div class="debug-provider-title">
          <h2>提供者</h2>
          <div
            v-if="chooseProvider"
            class="switch-label debug-provider-mode"
          >
            <span :class="{ 'mode-active': providerSelectionMode === 'single' }">单选</span>
            <button
              type="button"
              role="switch"
              class="switch"
              :class="{ checked: providerSelectionMode === 'multiple' }"
              aria-label="多选提供者"
              :aria-checked="providerSelectionMode === 'multiple'"
              :disabled="sending"
              @click="providerSelectionMode = providerSelectionMode === 'single' ? 'multiple' : 'single'"
            >
              <span />
            </button>
            <span :class="{ 'mode-active': providerSelectionMode === 'multiple' }">多选</span>
          </div>
        </div>
        <div
          v-if="chooseProvider"
          class="debug-provider-actions"
        >
          <input
            v-model="providerSearch"
            aria-label="搜索调试提供者"
            placeholder="搜索 IP / PID"
            :disabled="sending"
          />
        </div>
      </div>
      <div
        class="debug-provider-list"
        role="region"
        aria-label="提供者列表"
        :aria-busy="!onlySelectedProviders && providers.busy.value"
        tabindex="0"
      >
        <table
          class="debug-provider-table"
          aria-label="调试提供者"
        >
          <colgroup>
            <col class="debug-provider-select" />
            <col class="debug-provider-ip" />
            <col class="debug-provider-pid" />
            <col />
            <col class="debug-provider-status" />
          </colgroup>
          <thead>
            <tr>
              <th><span class="sr-only">选择</span></th>
              <th>实例 IP</th>
              <th>进程 PID</th>
              <th>单元</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="member in providerChoices"
              :key="member.instance.instanceId"
              :class="{
                selected: !chooseProvider || !!selectedProviders[member.instance.instanceId],
                unavailable: !providerAvailable(member),
                fixed: !chooseProvider
              }"
              :title="member.instance.instanceId"
              @click="providerRowClick($event, member)"
            >
              <td>
                <input
                  :type="providerSelectionMode === 'multiple' ? 'checkbox' : 'radio'"
                  :name="providerSelectionMode === 'single' ? 'debug-provider' : undefined"
                  :aria-label="`选择 ${member.instance.ipAddress || '—'} (${member.instance.processId ?? '—'})`"
                  :checked="!chooseProvider || !!selectedProviders[member.instance.instanceId]"
                  :disabled="
                    !chooseProvider ||
                    sending ||
                    (!onlySelectedProviders && providers.busy.value) ||
                    (!providerAvailable(member) && !selectedProviders[member.instance.instanceId])
                  "
                  @change="toggleProvider(member)"
                />
              </td>
              <td>
                <code>{{ member.instance.ipAddress || '—' }}</code>
              </td>
              <td>{{ member.instance.processId ?? '—' }}</td>
              <td>{{ member.instance.unitName || '—' }}</td>
              <td>
                <span :class="providerAvailable(member) ? 'enabled' : 'muted'">
                  {{ providerStatus(member) }}
                </span>
              </td>
            </tr>
            <tr v-if="!providerChoices.length">
              <td
                colspan="5"
                class="debug-provider-empty"
              >
                {{
                  onlySelectedProviders
                    ? selectedProviderCount
                      ? '没有匹配的已选提供者。'
                      : '尚未选择提供者。'
                    : providers.busy.value
                      ? '正在读取提供者…'
                      : '暂无匹配的提供者。'
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <DirectoryPager
        v-if="chooseProvider"
        :count="providerChoices.length"
        label="提供者"
        :busy="providers.busy.value || sending"
        :previous="!!providers.history.value.length"
        :next="!!providers.nextCursor.value"
        :show-controls="
          !onlySelectedProviders && (!!providers.history.value.length || !!providers.nextCursor.value)
        "
        @previous="providers.previous"
        @next="providers.next"
      >
        <span>
          {{ onlySelectedProviders ? '当前显示' : '本页' }} {{ providerChoices.length }} 个提供者，已选
          {{ selectedProviderCount }} 个
        </span>
        <button
          type="button"
          class="text-button"
          :aria-pressed="onlySelectedProviders"
          :disabled="sending"
          @click="onlySelectedProviders = !onlySelectedProviders"
        >
          {{ onlySelectedProviders ? '返回全部' : '仅看已选' }}
        </button>
        <button
          type="button"
          class="text-button"
          :disabled="sending || !selectedProviderCount"
          @click="clearProviders"
        >
          清空
        </button>
        <span class="small debug-provider-hint">
          {{
            unavailableSelection
              ? '已选提供者中有离线或禁用实例，请先取消选择。'
              : '筛选不清空已选；仅调用已选目标。'
          }}
        </span>
      </DirectoryPager>
      <p
        v-else
        class="muted debug-provider-summary"
      >
        本页 {{ providerChoices.length }} 个提供者，已选 1 个
      </p>
    </section>
  </section>
  <Feedback :error="error || providers.error.value" />
  <p
    v-if="loading && !data"
    class="muted"
  >
    正在读取提供者契约…
  </p>
  <template v-else-if="data">
    <form
      class="debug-workbench"
      @submit.prevent="invoke"
    >
      <div class="debug-workspace">
        <aside
          class="debug-methods-panel"
          aria-label="调用设置"
        >
          <h2>协议选择</h2>
          <div
            class="debug-protocols"
            role="group"
            aria-label="调试协议"
          >
            <button
              v-for="{ item, index } in protocolOptions"
              :key="item.id"
              type="button"
              :class="{ selected: endpointIndex === String(index) }"
              :aria-pressed="endpointIndex === String(index)"
              :disabled="sending || !protocolAvailable(item)"
              :title="
                protocolAvailable(item)
                  ? undefined
                  : `当前控制台未安装 ${item.endpoint.protocolName} 协议插件`
              "
              @click="selectEndpoint(index)"
            >
              {{ item.endpoint.protocolName }}
            </button>
          </div>
          <label class="debug-timeout">
            超时（毫秒）
            <input
              v-model="timeout"
              aria-label="调试超时"
              type="number"
              min="100"
              max="30000"
              required
              :disabled="sending"
            />
          </label>
        </aside>
        <div class="debug-exchange">
          <section
            class="debug-request"
            :class="{ 'is-collapsed': !requestExpanded }"
            aria-label="请求编辑"
          >
            <div class="debug-request-heading">
              <div class="debug-request-title">
                <h2>
                  <button
                    type="button"
                    class="text-button debug-section-toggle debug-request-toggle"
                    :aria-label="requestExpanded ? '收起请求参数' : '展开请求参数'"
                    :aria-expanded="requestExpanded"
                    aria-controls="debug-request-arguments"
                    @click="requestExpanded = !requestExpanded"
                  >
                    <img
                      :src="chevronUp"
                      alt=""
                      :class="{ collapsed: !requestExpanded }"
                    />
                    请求参数
                  </button>
                </h2>
                <code>{{ selectedSignature }}</code>
              </div>
              <span
                v-if="chooseProvider && providerSelectionMode === 'multiple'"
                class="muted small debug-concurrency"
              >
                最大并发 5
              </span>
              <button
                type="submit"
                class="primary"
                :disabled="
                  sending ||
                  loading ||
                  !endpoint ||
                  !protocolAvailable(endpoint) ||
                  !method ||
                  (chooseProvider && (!selectedProviderCount || unavailableSelection))
                "
              >
                {{
                  sending ? '调用中…' : chooseProvider ? `调用 ${selectedProviderCount} 个提供者` : '发起调用'
                }}
              </button>
            </div>
            <div
              v-show="requestExpanded"
              id="debug-request-arguments"
              class="debug-arguments"
            >
              <div
                v-for="(field, index) in argumentsForm"
                :key="`${field.name}-${index}`"
                class="debug-argument"
              >
                <div class="debug-argument-label">
                  <label :for="`debug-argument-${index}`">{{ field.name }}</label>
                  <span
                    class="muted debug-argument-type"
                    :title="field.javaType"
                  >
                    {{ field.typeLabel }}
                  </span>
                </div>
                <div class="debug-argument-editor">
                  <JsonParameterEditor
                    v-if="field.component === 'json'"
                    :id="`debug-argument-${index}`"
                    v-model="parameters[index]"
                    :disabled="sending"
                    @error="error = $event ? `${field.name}：${$event}` : ''"
                  />
                  <textarea
                    v-else-if="field.component !== 'input'"
                    :id="`debug-argument-${index}`"
                    v-model="parameters[index]"
                    :rows="Math.min(12, Math.max(2, (parameters[index] || '').split('\n').length))"
                    class="debug-parameters debug-argument-control"
                    :class="{ 'code-editor': field.component === 'json' || field.kind === 'json' }"
                    spellcheck="false"
                    :disabled="sending"
                  />
                  <input
                    v-else
                    :id="`debug-argument-${index}`"
                    v-model="parameters[index]"
                    class="debug-argument-control"
                    type="text"
                    :inputmode="field.kind === 'number' ? 'decimal' : 'text'"
                    :placeholder="field.kind === 'boolean' ? 'true / false' : ''"
                    :disabled="sending"
                  />
                </div>
              </div>
              <p
                v-if="method && !argumentsForm.length"
                class="muted"
              >
                此方法无需参数。
              </p>
            </div>
          </section>
          <section
            class="debug-result"
            aria-label="响应结果"
            aria-live="polite"
          >
            <div class="debug-result-heading">
              <h2>
                <button
                  type="button"
                  class="text-button debug-section-toggle debug-result-toggle"
                  :aria-label="resultExpanded ? '收起响应结果' : '展开响应结果'"
                  :aria-expanded="resultExpanded"
                  aria-controls="debug-result-panels"
                  @click="resultExpanded = !resultExpanded"
                >
                  <img
                    :src="chevronUp"
                    alt=""
                    :class="{ collapsed: !resultExpanded }"
                  />
                  响应结果
                </button>
              </h2>
              <span
                v-if="singleResult"
                :class="resultStatus(singleResult)"
              >
                {{ singleResult.state }}
                <template v-if="singleResult.response">
                  · {{ singleResult.response.elapsedMillis }} ms
                </template>
              </span>
              <div
                v-else-if="results.length"
                class="debug-result-summary small"
              >
                <span class="muted">{{ results.length }} 个提供者</span>
                <span class="status positive">{{ resultCounts.success }} 成功</span>
                <span class="status negative">{{ resultCounts.failure }} 失败</span>
                <span
                  v-if="resultCounts.pending"
                  class="muted"
                >
                  {{ resultCounts.pending }} 进行中
                </span>
              </div>
            </div>
            <div
              v-show="resultExpanded"
              id="debug-result-panels"
            >
              <div
                v-if="results.length"
                class="debug-batch-results"
                :class="{ 'debug-single-result': singleResult }"
              >
                <div
                  v-if="!singleResult"
                  ref="resultTabs"
                  role="tablist"
                  aria-label="提供者响应"
                  aria-orientation="vertical"
                  class="debug-result-list"
                >
                  <button
                    v-for="(row, index) in results"
                    :id="`debug-result-tab-${index}`"
                    :key="row.instance.instanceId"
                    type="button"
                    role="tab"
                    :aria-selected="activeResult === row"
                    aria-controls="debug-result-content"
                    :tabindex="activeResult === row ? 0 : -1"
                    class="debug-provider-result"
                    :class="{ selected: activeResult === row }"
                    :title="row.instance.instanceId"
                    @click="activeResultId = row.instance.instanceId"
                    @keydown="resultKeydown($event, index)"
                  >
                    <span class="debug-result-identity">
                      <strong>{{ row.instance.ipAddress || row.instance.instanceId }}</strong>
                      <span class="muted small">PID {{ row.instance.processId ?? '—' }}</span>
                    </span>
                    <span class="debug-result-outcome">
                      <span :class="resultStatus(row)">{{ row.state }}</span>
                      <span
                        v-if="row.response"
                        class="muted small"
                      >
                        {{ row.response.elapsedMillis }} ms
                      </span>
                    </span>
                  </button>
                </div>
                <article
                  v-if="activeResult"
                  id="debug-result-content"
                  class="debug-result-detail"
                  :role="singleResult ? undefined : 'tabpanel'"
                  :aria-labelledby="
                    singleResult ? undefined : `debug-result-tab-${results.indexOf(activeResult)}`
                  "
                  :tabindex="singleResult ? undefined : 0"
                >
                  <p
                    v-if="!singleResult"
                    class="muted small debug-result-target"
                    :title="activeResult.instance.instanceId"
                  >
                    {{ activeResult.instance.ipAddress || activeResult.instance.instanceId }} · PID
                    {{ activeResult.instance.processId ?? '—' }}
                  </p>
                  <div class="debug-result-body">
                    <pre
                      v-if="activeResult.response"
                      class="debug-response"
                    ><code>{{ JSON.stringify(activeResult.response.success ? activeResult.response.value : activeResult.response.errors, null, 2) ?? '无返回内容' }}</code></pre>
                    <p
                      v-else-if="activeResult.error"
                      class="status negative debug-result-error"
                    >
                      {{ activeResult.error }}
                    </p>
                    <p
                      v-else
                      class="muted"
                    >
                      正在等待该提供者响应…
                    </p>
                  </div>
                </article>
              </div>
              <p
                v-else
                class="muted"
              >
                {{ sending ? '正在等待提供者响应…' : '展示结果或异常' }}
              </p>
            </div>
          </section>
        </div>
      </div>
    </form>
  </template>
</template>
