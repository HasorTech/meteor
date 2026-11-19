<script setup>
import {t} from '../i18n';
import BreadcrumbTrail from './BreadcrumbTrail.vue';
import {usePageTab} from '../navigation';
import {computed, inject, onMounted, ref} from 'vue';
import {useFeedback} from '../useFeedback';
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
    ['info', t('ui.service-information')],
    ['providers', t('ui.providers')],
    ['consumers', t('ui.consumers')],
    ['flow', t('ui.traffic-control')],
    ['route', t('ui.routing-policies')],
    ['metadata', t('ui.metadata')]
  ].filter(([key]) => allowedTabs.includes(key))
);
const debugMethodName = computed(() => (route.value.method || '').split('(')[0]);
function serviceInformation() {
  navigate({ ...props.scope, view: 'service', tab: 'info' });
}
const query = computed(() => ({ ...props.scope, scope: 'service' }));
const info = computed(() => data.value?.info);
const serviceRole = computed(
  () => ({ Provider: t('ui.providers'), Consumer: t('ui.consumers') })[info.value?.role] || '—'
);
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
  }, t('ui.updated'));
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
        {{ $t('ui.service-directory') }}
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
        {{ $t('ui.direct-connection') }}
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
      <span class="badge">{{ $t('ui.direct-connection-2') }}</span>
    </template>
    <template v-if="tab === 'debug'">
      <span>/</span>
      <strong>{{ $t('ui.debug-method', debugMethodName) }}</strong>
    </template>
  </BreadcrumbTrail>
  <div class="tabbar detail-tabbar page-actionbar">
    <nav
      class="tabs"
      :aria-label="$t('ui.service-details')"
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
        {{ $t('ui.refresh') }}
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
            <dt>{{ $t('ui.service-id') }}</dt>
            <dd>{{ info.serviceId }}</dd>
          </div>
          <div
            class="service-primary-field"
            v-if="scopedToInstance && data.description?.javaType"
          >
            <dt>{{ $t('ui.interface') }}</dt>
            <dd>
              <code>{{ data.description.javaType }}</code>
            </dd>
          </div>
          <div>
            <dt>{{ $t('ui.group') }}</dt>
            <dd>{{ info.group || '—' }}</dd>
          </div>
          <div>
            <dt>{{ $t('ui.name') }}</dt>
            <dd>{{ info.name }}</dd>
          </div>
          <div>
            <dt>{{ $t('ui.version') }}</dt>
            <dd>{{ info.version || '—' }}</dd>
          </div>
          <div v-if="scopedToInstance">
            <dt>{{ $t('ui.role-2') }}</dt>
            <dd>{{ serviceRole }}</dd>
          </div>
          <div>
            <dt>{{ $t('ui.status') }}</dt>
            <dd><StatusBadge :enabled="data.enabled" /></dd>
          </div>
          <div v-if="scopedToInstance">
            <dt>{{ $t('ui.enable-service') }}</dt>
            <dd>
              <button
                class="switch"
                role="switch"
                :aria-label="$t('ui.enable-service')"
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
          :aria-label="$t('ui.service-status-controls')"
        >
          <div class="actions governance-actions">
            <label class="switch-label">
              <span>{{ data.enabled ? $t('ui.enabled') : $t('ui.disabled') }}</span>
              <button
                class="switch"
                role="switch"
                :aria-label="$t('ui.enable-service')"
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
        :aria-label="scopedToInstance ? $t('ui.service-endpoints') : $t('ui.service-examples')"
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
    {{ $t('ui.loading') }}
  </p>
</template>
