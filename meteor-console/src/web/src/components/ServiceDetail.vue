<script setup>
import BreadcrumbTrail from './BreadcrumbTrail.vue';
import { usePageTab } from '../navigation';
import { computed, inject, onMounted, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import Feedback from './Feedback.vue';
import ServiceAccess from './ServiceAccess.vue';
import ServiceMethods from './ServiceMethods.vue';
import ServiceMethodCatalog from './ServiceMethodCatalog.vue';
import ServiceDebug from './ServiceDebug.vue';
import MemberTable from './MemberTable.vue';
import RuleEditor from './RuleEditor.vue';
import RouteEditor from './RouteEditor.vue';
import MetadataEditor from './MetadataEditor.vue';
import StatusBadge from './StatusBadge.vue';
const props = defineProps({ scope: Object });
const api = inject('api');
const route = inject('route');
const navigate = inject('navigate');
const data = ref(null);
const scopedToInstance = computed(() => !!props.scope.instanceId || props.scope.mode === 'direct');
const instance = ref(null);
const catalogRevision = ref(0);
const allowedTabs = scopedToInstance.value
  ? ['info', 'debug', 'flow', 'route', 'metadata']
  : ['info', 'debug', 'providers', 'consumers', 'flow', 'route', 'metadata'];
const { selected: selectedTab, select } = usePageTab('info', allowedTabs);
const tab = computed(() =>
  selectedTab.value === 'debug' && !route.value.method ? 'info' : selectedTab.value
);
const { busy, error, success, run } = useFeedback();
const tabs = computed(() =>
  [
    ['info', '服务信息'],
    ['providers', '提供者'],
    ['consumers', '消费者'],
    ['flow', '流量控制'],
    ['route', '路由策略'],
    ['metadata', '元信息']
  ].filter(([key]) => allowedTabs.includes(key))
);
const debugMethodName = computed(() => (route.value.method || '').split('(')[0]);
function serviceInformation() {
  navigate({ ...props.scope, view: 'service', tab: 'info' });
}
const query = computed(() => ({ ...props.scope, scope: 'service' }));
const info = computed(() => data.value?.info);
const serviceRole = computed(() => ({ Provider: '提供者', Consumer: '消费者' })[info.value?.role] || '—');
function debug(method) {
  navigate({
    ...props.scope,
    view: 'service',
    tab: 'debug',
    method: method.javaSignature
  });
}
async function load() {
  const [service, snapshot] = await Promise.all([
    api('service', props.scope),
    scopedToInstance.value ? api('instance', props.scope) : Promise.resolve(null)
  ]);
  data.value = service;
  catalogRevision.value++;
  instance.value = snapshot?.instance;
}
function toggle() {
  run(async () => {
    const enabled = !data.value.enabled;
    await api('service/enabled', props.scope, 'PUT', { enabled });
    await load();
  }, '已更新');
}
onMounted(() => run(load));
</script>
<template>
  <BreadcrumbTrail>
    <template v-if="scope.mode === 'registry'">
      <button
        class="text-button"
        @click="navigate({ view: 'services', clusterId: scope.clusterId })"
      >
        服务目录
      </button>
      <span>/</span>
      <template v-if="scopedToInstance">
        <button
          class="text-button"
          :title="scope.serviceId"
          @click="navigate({ view: 'service', clusterId: scope.clusterId, serviceId: scope.serviceId })"
        >
          {{ info?.serviceId || scope.serviceId }}
        </button>
        <span>/</span>
        <component
          :is="tab === 'debug' ? 'button' : 'strong'"
          :class="{ 'text-button': tab === 'debug' }"
          @click="tab === 'debug' && serviceInformation()"
          class="instance-primary"
          :title="scope.instanceId"
        >
          {{ instance?.ipAddress || '—' }} ({{ instance?.processId ?? '—' }})
        </component>
      </template>
      <component
        :is="tab === 'debug' ? 'button' : 'strong'"
        :class="{ 'text-button': tab === 'debug' }"
        @click="tab === 'debug' && serviceInformation()"
        v-else
        :title="scope.serviceId"
      >
        {{ info?.serviceId || scope.serviceId }}
      </component>
    </template>
    <template v-else>
      <button
        class="text-button"
        @click="navigate({ view: 'direct' })"
      >
        直连实例
      </button>
      <span>/</span>
      <button
        class="text-button instance-primary"
        :title="scope.instanceId"
        @click="
          navigate({
            view: 'instance',
            target: scope.target,
            instanceId: scope.instanceId || instance?.instanceId,
            tab: 'services'
          })
        "
      >
        {{ instance?.ipAddress || '—' }} ({{ instance?.processId ?? '—' }})
      </button>
      <span>/</span>
      <component
        :is="tab === 'debug' ? 'button' : 'strong'"
        :class="{ 'text-button': tab === 'debug' }"
        @click="tab === 'debug' && serviceInformation()"
        :title="scope.serviceId"
      >
        {{ info?.serviceId || scope.serviceId }}
      </component>
      <span class="badge">直连</span>
    </template>
    <template v-if="tab === 'debug'">
      <span>/</span>
      <strong>调试({{ debugMethodName }})</strong>
    </template>
  </BreadcrumbTrail>
  <div class="tabbar detail-tabbar page-actionbar">
    <nav
      class="tabs"
      aria-label="服务详情"
    >
      <button
        v-for="[key, name] in tabs"
        :key="key"
        :class="{ active: tab === key }"
        @click="select(key)"
      >
        {{ name }}
      </button>
    </nav>
    <div
      class="toolbar"
      id="page-actions"
    >
      <button
        v-if="tab === 'info'"
        :disabled="busy"
        @click="run(load)"
      >
        刷新
      </button>
    </div>
  </div>
  <Feedback
    :error="error"
    :success="success"
  />
  <template v-if="data">
    <template v-if="tab === 'info'">
      <section
        class="info-panel service-info-panel"
        :class="{
          'instance-service-summary': scopedToInstance,
          'catalog-service-summary': !scopedToInstance
        }"
      >
        <dl class="details-grid service-info-fields">
          <div
            :class="{
              'service-primary-field': scopedToInstance,
              'service-id-only': scopedToInstance && !data.description?.javaType
            }"
          >
            <dt>服务 ID</dt>
            <dd>{{ info.serviceId }}</dd>
          </div>
          <div
            class="service-primary-field"
            v-if="scopedToInstance && data.description?.javaType"
          >
            <dt>接口</dt>
            <dd>
              <code>{{ data.description.javaType }}</code>
            </dd>
          </div>
          <div>
            <dt>分组</dt>
            <dd>{{ info.group || '—' }}</dd>
          </div>
          <div>
            <dt>名称</dt>
            <dd>{{ info.name }}</dd>
          </div>
          <div>
            <dt>版本</dt>
            <dd>{{ info.version || '—' }}</dd>
          </div>
          <div v-if="scopedToInstance">
            <dt>身份</dt>
            <dd>{{ serviceRole }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd><StatusBadge :enabled="data.enabled" /></dd>
          </div>
          <div v-if="scopedToInstance">
            <dt>启用服务</dt>
            <dd>
              <button
                class="switch"
                role="switch"
                aria-label="启用服务"
                :class="{ checked: data.enabled }"
                :aria-checked="data.enabled"
                :disabled="busy"
                @click="toggle"
              >
                <span />
              </button>
            </dd>
          </div>
        </dl>
        <div
          v-if="!scopedToInstance"
          class="service-control-panel"
          role="group"
          aria-label="服务启用操作"
        >
          <div class="actions governance-actions">
            <label class="switch-label">
              <span>{{ data.enabled ? '已启用' : '已禁用' }}</span>
              <button
                class="switch"
                role="switch"
                aria-label="启用服务"
                :class="{ checked: data.enabled }"
                :aria-checked="data.enabled"
                :disabled="busy"
                @click="toggle"
              >
                <span />
              </button>
            </label>
          </div>
        </div>
      </section>
      <section
        class="service-overview-access"
        role="region"
        :aria-label="scopedToInstance ? '服务接入点' : '服务示例'"
      >
        <ServiceAccess
          :scope="scope"
          :endpoints="scopedToInstance ? data.endpoints : []"
          :protocols="info.protocols"
        />
      </section>
      <ServiceMethods
        v-if="scopedToInstance"
        :description="data.description"
        :debuggable="data.enabled && info.role !== 'Consumer'"
        @debug="debug"
      />
      <ServiceMethodCatalog
        v-else
        :scope="scope"
        :revision="catalogRevision"
        @debug="debug"
      />
    </template>
    <ServiceDebug
      v-else-if="tab === 'debug' && (!scopedToInstance || info.role === 'Provider')"
      :key="route.method"
      :scope="scope"
      :instance="instance"
    />
    <MemberTable
      v-else-if="tab === 'providers' || tab === 'consumers'"
      :key="tab"
      :scope="query"
      :role="tab"
    />
    <MetadataEditor
      v-else-if="tab === 'metadata'"
      :scope="query"
    />
    <RuleEditor
      v-else-if="tab === 'flow'"
      type="FLOW_CONTROL"
      :scope="query"
    />
    <RouteEditor
      v-else-if="tab === 'route'"
      :scope="query"
    />
  </template>
  <p
    v-else-if="busy"
    class="empty"
  >
    正在读取…
  </p>
</template>
