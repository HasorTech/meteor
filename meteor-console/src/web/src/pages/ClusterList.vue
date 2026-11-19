<script setup>
import { t } from '../i18n';
import { computed, inject, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import Feedback from '../components/Feedback.vue';
const props = defineProps({ clusters: Array });
const emit = defineEmits(['changed']);
const navigate = inject('navigate');
const api = inject('api');
const query = ref('');
const removal = ref(null);
const { busy, error, success, run } = useFeedback();
const types = { remote: 'Remote', nacos: 'Nacos', jdbc: 'JDBC', zookeeper: 'ZooKeeper' };
const filtered = computed(() =>
  props.clusters.filter((c) => `${c.name} ${c.address}`.toLowerCase().includes(query.value.toLowerCase()))
);
function copyWithSelection(id) {
  const focused = document.activeElement;
  const text = document.createElement('textarea');
  text.value = id;
  text.readOnly = true;
  text.style.position = 'fixed';
  text.style.opacity = '0';
  document.body.appendChild(text);
  text.select();
  try {
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    text.remove();
    focused?.focus();
  }
}
function copyClusterId(id) {
  run(async () => {
    try {
      await navigator.clipboard.writeText(id);
    } catch {
      if (!copyWithSelection(id)) {
        throw new Error(t('ui.automatic-copy-failed-copy-the-cluster', id));
      }
    }
  }, t('ui.cluster-id-copied'));
}
function remove() {
  run(async () => {
    await api('clusters', { id: removal.value.id }, 'DELETE');
    removal.value = null;
    emit('changed');
  });
}
</script>
<template>
  <div class="tabbar page-actionbar">
    <h1>{{ $t('ui.cluster-connections') }}</h1>
    <div class="toolbar">
      <input
        v-if="clusters.length"
        v-model="query"
        :aria-label="$t('ui.search-clusters')"
        :placeholder="$t('ui.search-clusters')"
        type="search"
      />
      <button
        class="primary"
        @click="navigate({ view: 'connection' })"
      >
        {{ $t('ui.connect-to-a-registry') }}
      </button>
    </div>
  </div>
  <Feedback
    :error="error"
    :success="success"
  />
  <div
    v-if="!clusters.length"
    class="empty-state"
  >
    <span
      class="empty-illustration"
      aria-hidden="true"
    >
      ◇
    </span>
    <h2>{{ $t('ui.connect-to-a-registry-2') }}</h2>
    <p>{{ $t('ui.select-a-registry-to-start-managing') }}</p>
    <button
      class="primary"
      @click="navigate({ view: 'connection' })"
    >
      {{ $t('ui.connect-to-a-registry') }}
    </button>
  </div>
  <div
    v-else
    class="cards cluster-cards"
  >
    <article
      v-for="cluster in filtered"
      :key="cluster.id"
      class="card"
    >
      <div class="card-heading">
        <strong>{{ cluster.name }}</strong>
        <span class="muted">{{ types[cluster.type] }}</span>
      </div>
      <p
        class="address"
        :title="cluster.address"
      >
        {{ cluster.address }}
      </p>
      <p class="small muted">
        {{
          cluster.type === 'nacos'
            ? [cluster.namespace || 'public', cluster.group || 'meteor'].join(' / ')
            : cluster.type === 'zookeeper'
              ? cluster.root || '/meteor'
              : cluster.database
        }}
      </p>
      <div class="card-actions">
        <button
          class="text-button"
          type="button"
          :disabled="busy"
          @click="copyClusterId(cluster.id)"
        >
          {{ $t('ui.copy-cluster-id') }}
        </button>
        <button
          class="text-button"
          @click="navigate({ view: 'connection', id: cluster.id })"
        >
          {{ $t('ui.edit') }}
        </button>
        <button
          class="text-button"
          @click="removal = cluster"
        >
          {{ $t('ui.remove') }}
        </button>
        <button
          class="text-button push-right"
          @click="navigate({ view: 'applications', clusterId: cluster.id })"
        >
          {{ $t('ui.open') }}
        </button>
      </div>
    </article>
  </div>
  <p
    class="total"
    v-if="clusters.length"
  >
    {{ $t('ui.cluster-count', filtered.length) }}
  </p>
  <div
    v-if="clusters.length && !filtered.length"
    class="empty"
  >
    {{ $t('ui.no-matching-clusters') }}
  </div>
  <div
    v-if="removal"
    class="dialog-backdrop"
    @click.self="removal = null"
    @keydown.esc="removal = null"
  >
    <section
      class="dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="remove-title"
    >
      <h2 id="remove-title">{{ $t('ui.remove-connection') }}</h2>
      <p>{{ $t('ui.confirm-remove-connection', removal.name) }}</p>
      <div class="actions">
        <button
          :disabled="busy"
          @click="removal = null"
        >
          {{ $t('ui.cancel') }}
        </button>
        <button
          class="danger"
          :disabled="busy"
          @click="remove"
        >
          {{ $t('ui.remove') }}
        </button>
      </div>
    </section>
  </div>
</template>
