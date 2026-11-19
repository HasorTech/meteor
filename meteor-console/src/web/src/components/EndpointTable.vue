<script setup>
import { computed, inject } from 'vue';
import ProtocolBadges from './ProtocolBadges.vue';
const props = defineProps({ endpoints: Array, service: Boolean });
const endpointTypes = inject('endpointTypes');
const definitions = computed(() => new Map(endpointTypes.value.map((type) => [type.key, type.label])));
const endpointRows = computed(() =>
  (props.endpoints || [])
    .flatMap((endpoint) => {
      const groups = props.service
        ? Object.entries(endpoint.endpoints || {})
        : [['', endpoint.endpoints || {}]];
      if (!groups.length) groups.push(['', {}]);
      return groups.map(([scheme, addresses]) => ({
        protocolName: endpoint.protocolName,
        defaultProtocol: endpoint.defaultProtocol === true,
        protocolFamilies: endpoint.protocolFamilies || [],
        scheme,
        address: addresses.endpoint || '',
        location: props.service ? null : protocolLocation(addresses.endpoint),
        details: Object.entries(addresses)
          .filter(([type]) => props.service || type === 'endpoint')
          .map(([type, target]) => ({ type, target, label: definitions.value.get(type) || type }))
          .sort((left, right) => {
            const keys = [...definitions.value.keys()];
            // Meteor 接入点始终排在第一位，其余类型沿用注册中心定义顺序。
            const rank = (type) =>
              type === 'endpoint' ? -1 : keys.includes(type) ? keys.indexOf(type) : keys.length;
            return rank(left.type) - rank(right.type) || left.type.localeCompare(right.type);
          })
      }));
    })
    .sort(
      (left, right) =>
        Number(right.defaultProtocol) - Number(left.defaultProtocol) ||
        left.protocolName.localeCompare(right.protocolName)
    )
);
function protocolLocation(address) {
  try {
    const target = new URL(address);
    const defaultPorts = { 'http:': '80', 'https:': '443', 'ws:': '80', 'wss:': '443' };
    return { port: target.port || defaultPorts[target.protocol] || '—', contextPath: target.pathname || '—' };
  } catch {
    return { port: '—', contextPath: '—' };
  }
}
function safeHttpAddress(address) {
  try {
    return ['http:', 'https:'].includes(new URL(address).protocol) ? address : null;
  } catch {
    return null;
  }
}
</script>
<template>
  <div class="table-scroll">
    <table
      class="endpoint-table"
      :class="{ 'instance-protocol-table': !service }"
    >
      <thead>
        <tr>
          <th class="endpoint-protocol-column">{{ $t('ui.protocol') }}</th>
          <th v-if="service">{{ $t('ui.endpoints') }}</th>
          <template v-else>
            <th class="endpoint-family-column">{{ $t('ui.family') }}</th>
            <th class="endpoint-port-column">{{ $t('ui.port') }}</th>
            <th title="Context Path">Context Path</th>
          </template>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="{
            protocolName,
            defaultProtocol,
            protocolFamilies,
            scheme,
            address,
            location,
            details
          } in endpointRows"
          :key="JSON.stringify([protocolName, scheme, address, details])"
        >
          <td>
            <div class="endpoint-content">
              <div class="endpoint-line endpoint-protocol">
                <ProtocolBadges :protocols="[protocolName]" />
                <span
                  v-if="defaultProtocol"
                  class="endpoint-default muted"
                >
                  {{ $t('ui.default') }}
                </span>
              </div>
            </div>
          </td>
          <td v-if="service">
            <div class="endpoint-content">
              <div
                v-for="{ type, target, label } in details"
                :key="type"
                class="endpoint-line endpoint-entry"
                :class="{ 'endpoint-address': type === 'endpoint' }"
                :data-endpoint-type="type"
              >
                <span
                  class="badge"
                  :title="label"
                >
                  {{ label }}
                </span>
                <a
                  v-if="target && safeHttpAddress(target)"
                  :href="target"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <code :title="target">{{ target }}</code>
                </a>
                <code
                  v-else-if="target"
                  :title="target"
                >
                  {{ target }}
                </code>
              </div>
              <span
                v-if="!details.length"
                class="muted"
              >
                —
              </span>
            </div>
          </td>
          <template v-else>
            <td>
              <span class="endpoint-family">{{ protocolFamilies.join('、') || '—' }}</span>
            </td>
            <td>
              <code>{{ location.port }}</code>
            </td>
            <td>
              <code class="endpoint-context-path">{{ location.contextPath }}</code>
            </td>
          </template>
        </tr>
      </tbody>
    </table>
  </div>
</template>
