<script setup>
import { ref } from 'vue';
import { useDirectory } from '../useDirectory';
import DirectoryPager from './DirectoryPager.vue';
import Feedback from './Feedback.vue';
import ProtocolSummary from './ProtocolSummary.vue';
import StatusBadge from './StatusBadge.vue';
const props = defineProps({ scope: Object, role: String });
const search = ref('');
const directory = useDirectory(
  () => 'service/members',
  () => ({
    ...props.scope,
    role: props.role,
    field: 'ip',
    filter: search.value
  })
);
const { items: members, busy, error, refresh } = directory;
const filtered = members;
function memberProtocols(member) {
  if (props.role === 'consumers') return member.usedProtocols || [];
  return member.endpoints.map((endpoint) => endpoint.protocolName);
}
function serviceHref(instance) {
  const route = {
    view: 'service',
    serviceId: props.scope.serviceId,
    clusterId: props.scope.clusterId,
    instanceId: instance.instanceId,
    application: instance.applicationName,
    target: props.scope.target
  };
  const params = new URLSearchParams(
    Object.entries(route).filter(([, value]) => value !== undefined && value !== null && value !== '')
  );
  return `#${params}`;
}
</script>
<template>
  <Teleport to="#page-actions">
    <input
      v-model="search"
      type="search"
      aria-label="搜索成员"
      placeholder="搜索 IP"
    />
    <button
      :disabled="busy"
      @click="refresh"
    >
      刷新
    </button>
  </Teleport>
  <Feedback :error="error" />
  <div class="table-scroll">
    <table class="member-table">
      <thead>
        <tr>
          <th>实例</th>
          <th>进程</th>
          <th>应用</th>
          <th>单元</th>
          <th>{{ role === 'consumers' ? '使用协议' : '支持协议' }}</th>
          <th>状态</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="member in filtered"
          :key="member.instance.instanceId"
        >
          <td>
            <a
              class="instance-link"
              :href="serviceHref(member.instance)"
            >
              <span class="instance-primary">{{ member.instance.ipAddress || '—' }}</span>
            </a>
          </td>
          <td class="instance-pid">{{ member.instance.processId ?? '—' }}</td>
          <td>{{ member.instance.applicationName }}</td>
          <td>{{ member.instance.unitName || '—' }}</td>
          <td>
            <ProtocolSummary :protocols="memberProtocols(member)" />
          </td>
          <td><StatusBadge :enabled="member.enabled" /></td>
        </tr>
      </tbody>
    </table>
  </div>
  <p
    v-if="!filtered.length"
    class="empty"
  >
    {{ busy ? '正在读取…' : '没有匹配的成员' }}
  </p>
  <DirectoryPager
    :count="filtered.length"
    :label="role === 'providers' ? '提供者' : '消费者'"
    :busy="busy"
    :previous="directory.history.value.length > 0"
    :next="!!directory.nextCursor.value"
    @previous="directory.previous"
    @next="directory.next"
  />
</template>
