<script setup>
import { t } from '../i18n';
import BreadcrumbTrail from './BreadcrumbTrail.vue';
import { usePageTab } from '../navigation';
import { computed, inject, onMounted, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import { useDirectory } from '../useDirectory';
import DirectoryPager from './DirectoryPager.vue';
import Feedback from './Feedback.vue';
import RuleEditor from './RuleEditor.vue';
import RouteEditor from './RouteEditor.vue';
import EndpointTable from './EndpointTable.vue';
import ProtocolSummary from './ProtocolSummary.vue';
import StatusBadge from './StatusBadge.vue';
const props = defineProps({ scope: Object });
const api = inject('api');
const navigate = inject('navigate');
const data = ref(null);
const { selected: tab, select } = usePageTab('instance', ['instance', 'services', 'flow', 'route']);
const search = ref('');
const field = ref('id');
const { busy, error, success, run } = useFeedback();
const tabs = computed(() => [
  ['instance', t('ui.instance')],
  ['services', t('ui.services')],
  ['flow', t('ui.traffic-control')],
  ['route', t('ui.routing-policies')]
]);
const instance = computed(() => data.value?.instance);
const managementEndpoints = computed(() => [
  ...new Set(
    (instance.value?.endpoints || []).map((endpoint) => endpoint.endpoints?.management).filter(Boolean)
  )
]);
const query = computed(() => ({ ...props.scope, scope: 'instance' }));
const directory = useDirectory(
  () => 'services',
  () => ({
    ...props.scope,
    filter: search.value,
    field: field.value
  }),
  () => tab.value === 'services'
);
const { items: filtered } = directory;
async function load() {
  try {
    const result = await api('instance', props.scope);
    // 离线后保留上次读取的身份信息，在线状态以本次查询为准。
    if (!result.online) {
      result.instance = { ...data.value?.instance, ...result.instance };
    }
    data.value = result;
  } catch (failure) {
    if (data.value) data.value.online = null;
    throw failure;
  }
}
function toggle() {
  run(async () => {
    await api('instance/block', props.scope, 'PUT', { blocked: !data.value.blocked });
    await load();
  }, t('ui.updated'));
}
function service(info) {
  navigate({
    view: 'service',
    ...props.scope,
    serviceId: info.serviceId,
    application: instance.value.applicationName
  });
}
onMounted(() => run(load));
</script>
<template>
  <BreadcrumbTrail>
    <template v-if="scope.mode === 'registry'">
      <button
        class="text-button"
        @click="navigate({ view: 'applications', clusterId: scope.clusterId })"
      >
        {{ $t('ui.applications') }}
      </button>
      <span>/</span>
      <button
        class="text-button"
        @click="
          navigate({
            view: 'application',
            clusterId: scope.clusterId,
            application: instance?.applicationName
          })
        "
      >
        {{ instance?.applicationName || $t('ui.application') }}
      </button>
    </template>
    <button
      v-else
      class="text-button"
      @click="navigate({ view: 'direct' })"
    >
      {{ $t('ui.direct-connection') }}
    </button>
    <span>/</span>
    <strong
      v-if="instance"
      class="instance-primary"
    >
      {{ instance.ipAddress || '—' }} ({{ instance.processId ?? '—' }})
    </strong>
    <span
      v-if="scope.mode === 'direct'"
      class="badge"
    >
      {{ $t('ui.direct-connection-2') }}
    </span>
  </BreadcrumbTrail>
  <div class="tabbar detail-tabbar page-actionbar">
    <nav
      class="tabs"
      :aria-label="$t('ui.instance-details')"
    >
      <button
        v-for="[key, name] in tabs"
        :key="key"
        :class="{ active: tab === key }"
        :disabled="!data"
        @click="select(key)"
      >
        {{ name }}
      </button>
    </nav>
    <div
      class="toolbar"
      id="page-actions"
    >
      <template v-if="tab === 'services'">
        <select
          v-model="field"
          :aria-label="$t('ui.search-by')"
        >
          <option value="id">{{ $t('ui.service-id') }}</option>
          <option value="role">{{ $t('ui.role') }}</option>
          <option value="protocol">{{ $t('ui.protocol') }}</option>
        </select>
        <input
          v-model="search"
          :aria-label="$t('ui.search-services')"
          type="search"
          :placeholder="$t('ui.search')"
        />
      </template>
      <button
        v-if="tab === 'services' || tab === 'instance'"
        :disabled="busy"
        @click="tab === 'services' ? directory.refresh() : run(load)"
      >
        {{ $t('ui.refresh') }}
      </button>
    </div>
  </div>
  <Feedback
    :error="error || directory.error.value"
    :success="success"
  />
  <template v-if="data">
    <template v-if="tab === 'instance'">
      <section class="instance-info-panel">
        <aside
          class="instance-sidebar"
          :aria-label="$t('ui.instance-information')"
        >
          <dl class="details-grid instance-facts">
            <div>
              <dt>{{ $t('ui.instance-ip') }}</dt>
              <dd>{{ instance.ipAddress || $t('ui.not-reported') }}</dd>
            </div>
            <div>
              <dt>{{ $t('ui.process-id') }}</dt>
              <dd>{{ instance.processId ?? $t('ui.not-reported') }}</dd>
            </div>
            <div>
              <dt>{{ $t('ui.application') }}</dt>
              <dd>{{ instance.applicationName }}</dd>
            </div>
            <div>
              <dt>{{ $t('ui.unit') }}</dt>
              <dd>{{ instance.unitName || '—' }}</dd>
            </div>
            <div>
              <dt>{{ $t('ui.instance-id') }}</dt>
              <dd>{{ instance.instanceId }}</dd>
            </div>
            <div>
              <dt>{{ $t('ui.online-status') }}</dt>
              <dd>
                <StatusBadge
                  v-if="data.online !== null"
                  :enabled="data.online"
                  :on="$t('ui.online')"
                  :off="$t('ui.offline')"
                />
                <span v-else>{{ $t('ui.unknown') }}</span>
              </dd>
            </div>
            <div>
              <dt>{{ $t('ui.management-endpoint') }}</dt>
              <dd>
                <template v-if="managementEndpoints.length">
                  <div
                    v-for="address in managementEndpoints"
                    :key="address"
                  >
                    <code>{{ address }}</code>
                  </div>
                </template>
                <span v-else>—</span>
              </dd>
            </div>
          </dl>
          <div
            v-if="scope.mode === 'registry' || data.online"
            class="instance-control"
          >
            <span>{{ $t('ui.block-calls') }}</span>
            <label class="switch-label">
              <span>{{ data.blocked ? $t('ui.blocked') : $t('ui.allowed') }}</span>
              <button
                role="switch"
                :aria-label="$t('ui.block-calls')"
                class="switch"
                :class="{ checked: data.blocked }"
                :aria-checked="data.blocked"
                :disabled="busy || (scope.mode === 'direct' && data.online !== true)"
                @click="toggle"
              >
                <span />
              </button>
            </label>
          </div>
        </aside>
        <div
          v-if="data.online"
          class="endpoint-section"
          role="region"
          :aria-label="$t('ui.instance-endpoints')"
        >
          <EndpointTable :endpoints="instance.endpoints || []" />
          <p
            v-if="!instance.endpoints?.length"
            class="empty"
          >
            {{ $t('ui.this-instance-only-makes-outbound-calls') }}
          </p>
        </div>
        <div
          v-else
          class="instance-endpoints-empty"
          role="status"
        >
          {{
            data.online === false
              ? $t('ui.the-instance-is-offline-its-endpoints')
              : $t('ui.could-not-load-the-instance-endpoints')
          }}
        </div>
      </section>
    </template>
    <template v-else-if="tab === 'services'">
      <div class="table-scroll">
        <table class="instance-services-table">
          <thead>
            <tr>
              <th>{{ $t('ui.service-id') }}</th>
              <th>{{ $t('ui.supported-protocols') }}</th>
              <th>{{ $t('ui.role-2') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="info in filtered"
              :key="info.serviceId"
            >
              <td>
                <button
                  class="text-button"
                  @click="service(info)"
                >
                  <code>{{ info.serviceId }}</code>
                </button>
              </td>
              <td>
                <ProtocolSummary :protocols="info.protocols" />
              </td>
              <td>{{ info.role === 'Provider' ? $t('ui.providers') : $t('ui.consumers') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p
        v-if="!filtered.length"
        class="empty"
      >
        {{ $t('ui.no-matching-services') }}
      </p>
      <DirectoryPager
        :count="filtered.length"
        :label="$t('ui.services-count-label')"
        :busy="directory.busy.value"
        :previous="directory.history.value.length > 0"
        :next="!!directory.nextCursor.value"
        @previous="directory.previous"
        @next="directory.next"
      />
    </template>
    <RuleEditor
      v-else-if="tab === 'flow'"
      :scope="query"
      type="FLOW_CONTROL"
    />
    <RouteEditor
      v-else
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
