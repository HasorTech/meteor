<script setup>
import BreadcrumbTrail from '../components/BreadcrumbTrail.vue';
import {inject, onMounted, ref} from 'vue';
import {useDirectory} from '../useDirectory';
import DirectoryPager from '../components/DirectoryPager.vue';
import {instanceRole} from '../display';
import Feedback from '../components/Feedback.vue';
import WorkspaceTabs from '../components/WorkspaceTabs.vue';
import ViewToggle from '../components/ViewToggle.vue';
import ProtocolSummary from '../components/ProtocolSummary.vue';
import InstanceStatus from '../components/InstanceStatus.vue';

const props = defineProps({scope: Object, application: String});
const api = inject('api');
const navigate = inject('navigate');
const search = ref('');
const unit = ref('');
const display = ref(
    localStorage.getItem(props.application ? 'meteor.instances.view' : 'meteor.applications.view') ||
    (props.application ? 'list' : 'cards')
);
const units = ref([]);
const directory = useDirectory(
    () => (props.application ? 'instances' : 'applications'),
    () => ({...props.scope, applicationName: props.application, filter: search.value, unitName: unit.value})
);
const {items, busy, error, refresh} = directory;
const applications = items;
const filtered = items;

async function loadUnits() {
  if (props.application) {
    try {
      units.value = await api('units', {...props.scope, applicationName: props.application});
    } catch (failure) {
      error.value = failure.message;
    }
  }
}

function viewMode(value) {
  display.value = value;
  localStorage.setItem(props.application ? 'meteor.instances.view' : 'meteor.applications.view', value);
}

function openApp(name) {
  navigate({view: 'application', clusterId: props.scope.clusterId, application: name});
}

function instanceProtocols(instance) {
  return (instance.endpoints || []).map((endpoint) => endpoint.protocolName);
}

function openInstance(instance) {
  navigate({
    view: 'instance',
    clusterId: props.scope.clusterId,
    instanceId: instance.instanceId,
    application: props.application
  });
}

onMounted(loadUnits);
</script>
<template>
  <BreadcrumbTrail v-if="application">
    <button
        class="text-button"
        @click="navigate({ view: 'applications', clusterId: scope.clusterId })"
    >
      {{ $t('ui.applications') }}
    </button>
    <span>/</span>
    <strong>{{ application }}</strong>
  </BreadcrumbTrail>
  <WorkspaceTabs>
    <select
        v-if="application"
        v-model="unit"
        :aria-label="$t('ui.filter-by-unit')"
    >
      <option value="">{{ $t('ui.all-units') }}</option>
      <option
          v-for="item in units"
          :key="item"
      >
        {{ item }}
      </option>
    </select>
    <input
        v-model="search"
        type="search"
        :aria-label="application ? $t('ui.search-ip-process-id-or-instance') : $t('ui.search-application-name')"
        :placeholder="
        application ? $t('ui.search-ip-process-id-or-instance') : $t('ui.search-application-name')
      "
    />
    <button
        :disabled="busy"
        @click="refresh"
    >
      {{ $t('ui.refresh') }}
    </button>
    <ViewToggle
        :model-value="display"
        @update:model-value="viewMode"
    />
  </WorkspaceTabs>
  <Feedback :error="error"/>
  <template v-if="!application">
    <div
        v-if="display === 'cards'"
        class="cards"
    >
      <button
          type="button"
          class="card application-card"
          v-for="app in applications"
          :key="app.name"
          :aria-label="app.name"
          @click="openApp(app.name)"
      >
        <strong
            class="application-title"
            :title="app.name"
        >
          {{ app.name }}
        </strong>
      </button>
    </div>
    <div
        v-else
        class="table-scroll"
    >
      <table>
        <thead>
        <tr>
          <th>{{ $t('ui.application-name') }}</th>
          <th class="operation">{{ $t('ui.actions') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr
            v-for="app in applications"
            :key="app.name"
        >
          <td>
            <button
                class="text-button dark"
                @click="openApp(app.name)"
            >
              {{ app.name }}
            </button>
          </td>
          <td>
            <button
                class="text-button"
                @click="openApp(app.name)"
            >
              {{ $t('ui.details') }}
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <p
        v-if="!applications.length"
        class="empty"
    >
      {{ busy ? $t('ui.loading') : $t('ui.no-matching-applications') }}
    </p>
  </template>
  <template v-else>
    <div
        v-if="display === 'cards'"
        class="cards"
    >
      <article
          class="card instance-card"
          v-for="instance in filtered"
          :key="instance.instanceId"
          @click="openInstance(instance)"
      >
        <span class="card-heading">
          <span class="instance-identity">
            <button
                class="text-button dark instance-primary"
                @click.stop="openInstance(instance)"
            >
              {{ instance.ipAddress || '—' }}
            </button>
            <span class="instance-process">PID {{ instance.processId ?? '—' }}</span>
          </span>
          <InstanceStatus
              :online="instance.online"
              :blocked="instance.block"
          />
        </span>
        <span class="card-detail">
          <span class="muted">{{ $t('ui.unit') }}</span>
          <span>{{ instance.unitName || '—' }}</span>
        </span>
        <span class="card-actions">
          <ProtocolSummary
              v-if="instance.endpoints?.length"
              :protocols="instanceProtocols(instance)"
          />
          <span
              v-if="instance.roles?.length"
              class="badge instance-role"
          >
            {{ instanceRole(instance) }}
          </span>
        </span>
      </article>
    </div>
    <div
        v-else
        class="table-scroll"
    >
      <table class="instance-directory-table">
        <thead>
        <tr>
          <th>{{ $t('ui.instance') }}</th>
          <th>PID</th>
          <th>{{ $t('ui.status') }}</th>
          <th>{{ $t('ui.unit') }}</th>
          <th>{{ $t('ui.protocol-endpoints') }}</th>
          <th>{{ $t('ui.role-2') }}</th>
          <th class="operation">{{ $t('ui.actions') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr
            v-for="instance in filtered"
            :key="instance.instanceId"
        >
          <td>
              <span class="instance-identity">
                <button
                    class="text-button dark instance-primary"
                    @click="openInstance(instance)"
                >
                  {{ instance.ipAddress || '—' }}
                </button>
              </span>
          </td>
          <td class="instance-pid">{{ instance.processId ?? '—' }}</td>
          <td>
            <!-- 实例档案持久保留，当前会话决定在线状态。 -->
            <InstanceStatus
                :online="instance.online"
                :blocked="instance.block"
            />
          </td>
          <td>{{ instance.unitName || '—' }}</td>
          <td>
            <ProtocolSummary :protocols="instanceProtocols(instance)"/>
          </td>
          <td>
              <span
                  v-if="instance.roles?.length"
                  class="badge instance-role"
              >
                {{ instanceRole(instance) }}
              </span>
          </td>
          <td>
            <button
                class="text-button"
                @click="openInstance(instance)"
            >
              {{ $t('ui.details') }}
            </button>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <p
        v-if="!filtered.length"
        class="empty"
    >
      {{ busy ? $t('ui.loading') : $t('ui.no-matching-instances') }}
    </p>
  </template>
  <DirectoryPager
      :count="items.length"
      :label="application ? $t('ui.instances-count-label') : $t('ui.applications-count-label')"
      :busy="busy"
      :previous="directory.history.value.length > 0"
      :next="!!directory.nextCursor.value"
      @previous="directory.previous"
      @next="directory.next"
  />
</template>
