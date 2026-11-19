<script setup>
import { inject, ref } from 'vue';
import WorkspaceTabs from '../components/WorkspaceTabs.vue';
import ProtocolSummary from '../components/ProtocolSummary.vue';
import StatusBadge from '../components/StatusBadge.vue';
import Feedback from '../components/Feedback.vue';
import { useDirectory } from '../useDirectory';
import DirectoryPager from '../components/DirectoryPager.vue';
const props = defineProps({ scope: Object });
const navigate = inject('navigate');
const search = ref('');
const directory = useDirectory(
  () => 'services',
  () => ({ ...props.scope, filter: search.value })
);
const { items: filtered, busy, error, refresh } = directory;
function open(service) {
  navigate({ view: 'service', clusterId: props.scope.clusterId, serviceId: service.info.serviceId });
}
</script>
<template>
  <WorkspaceTabs active="services">
    <input
      v-model="search"
      type="search"
      :aria-label="$t('ui.search-service-id')"
      :placeholder="$t('ui.search-service-id')"
    />
    <button
      :disabled="busy"
      @click="refresh"
    >
      {{ $t('ui.refresh') }}
    </button>
  </WorkspaceTabs>
  <Feedback :error="error" />
  <div class="table-scroll">
    <table class="service-directory-table">
      <thead>
        <tr>
          <th>{{ $t('ui.service-id') }}</th>
          <th>{{ $t('ui.supported-protocols') }}</th>
          <th>{{ $t('ui.providers') }}</th>
          <th>{{ $t('ui.consumers') }}</th>
          <th>{{ $t('ui.status') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="service in filtered"
          :key="service.info.serviceId"
        >
          <td>
            <button
              class="text-button"
              @click="open(service)"
            >
              <code>{{ service.info.serviceId }}</code>
            </button>
          </td>
          <td>
            <ProtocolSummary :protocols="service.info.protocols" />
          </td>
          <td>{{ service.info.providerCount }}</td>
          <td>{{ service.info.consumerCount }}</td>
          <td><StatusBadge :enabled="service.enabled" /></td>
        </tr>
      </tbody>
    </table>
  </div>
  <p
    v-if="!filtered.length"
    class="empty"
  >
    {{ busy ? $t('ui.loading') : $t('ui.no-matching-services') }}
  </p>
  <DirectoryPager
    :count="filtered.length"
    :label="$t('ui.services-count-label')"
    :busy="busy"
    :previous="directory.history.value.length > 0"
    :next="!!directory.nextCursor.value"
    @previous="directory.previous"
    @next="directory.next"
  />
</template>
