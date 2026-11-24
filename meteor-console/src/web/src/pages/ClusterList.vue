<script setup>
import { t } from '../i18n';
import { computed, inject, nextTick, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import Feedback from '../components/Feedback.vue';
import chevronUp from '../assets/icons/chevron-up.svg';
const props = defineProps({ clusters: Array });
const emit = defineEmits(['changed']);
const navigate = inject('navigate');
const api = inject('api');
const query = ref('');
const removal = ref(null);
const cancelRemovalButton = ref(null);
const { busy, error, success, run } = useFeedback();
const types = { remote: 'Remote', nacos: 'Nacos', jdbc: 'JDBC', zookeeper: 'ZooKeeper' };
const filtered = computed(() =>
  props.clusters.filter((c) => `${c.name} ${c.address}`.toLowerCase().includes(query.value.toLowerCase()))
);
let removalTrigger;

function clusterDetails(cluster) {
  if (cluster.type === 'nacos') {
    return [cluster.namespace || 'public', cluster.group || 'meteor'].join(' / ');
  }
  if (cluster.type === 'zookeeper') return cluster.root || '/meteor';
  return cluster.database;
}

function closeActionsOnBlur(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false;
}

function closeActions(details) {
  details.open = false;
  details.querySelector('summary').focus();
}

async function confirmRemoval(cluster, event) {
  const actions = event.currentTarget.closest('details');
  removalTrigger = actions.querySelector('summary');
  closeActions(actions);
  removal.value = cluster;
  await nextTick();
  cancelRemovalButton.value?.focus();
}

function cancelRemoval() {
  if (busy.value) return;
  removal.value = null;
  removalTrigger?.focus();
  removalTrigger = null;
}
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
    removalTrigger = null;
    emit('changed');
  });
}
</script>
<template>
  <div class="tabbar cluster-list-header">
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
    <h2>{{ $t('ui.connect-to-a-registry-2') }}</h2>
    <p>{{ $t('ui.select-a-registry-to-start-managing') }}</p>
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
      <header class="card-heading">
        <h2>{{ cluster.name }}</h2>
        <button
          class="text-button cluster-open"
          type="button"
          @click="navigate({ view: 'applications', clusterId: cluster.id })"
        >
          {{ $t('ui.open') }}
          <img
            :src="chevronUp"
            alt=""
          />
        </button>
      </header>
      <p class="cluster-details">
        <span>{{ types[cluster.type] }}</span>
        <span v-if="clusterDetails(cluster)">{{ clusterDetails(cluster) }}</span>
      </p>
      <code
        class="cluster-address"
        :title="cluster.address"
      >
        {{ cluster.address }}
      </code>
      <footer class="card-actions">
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
          type="button"
          @click="navigate({ view: 'connection', id: cluster.id })"
        >
          {{ $t('ui.edit') }}
        </button>
        <details
          class="cluster-actions"
          name="cluster-actions"
          @focusout="closeActionsOnBlur"
          @keydown.esc.stop="closeActions($event.currentTarget)"
        >
          <summary class="text-button">{{ $t('ui.actions') }}</summary>
          <div class="cluster-actions-panel">
            <button
              class="text-button"
              type="button"
              :disabled="busy"
              @click="confirmRemoval(cluster, $event)"
            >
              {{ $t('ui.remove') }}
            </button>
          </div>
        </details>
      </footer>
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
    @click.self="cancelRemoval"
    @keydown.esc="cancelRemoval"
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
          ref="cancelRemovalButton"
          :disabled="busy"
          @click="cancelRemoval"
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
