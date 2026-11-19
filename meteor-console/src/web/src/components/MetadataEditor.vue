<script setup>
import { t } from '../i18n';
import { computed, inject, onMounted, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import { trackDraft } from '../useNavigationGuard';
import Feedback from './Feedback.vue';

const props = defineProps({ scope: { type: Object, required: true }, toolbar: { default: '#page-actions' } });
const api = inject('api');
const metadata = ref(null);
const draft = ref(null);
const { busy, error, success, run } = useFeedback();
trackDraft(() => draft.value !== null);

const rows = computed(() => {
  const entries = Object.entries(metadata.value ?? {});
  entries.sort(([left], [right]) => left.localeCompare(right));
  const result = entries.map(([key, value]) => ({ key, value, isNew: false }));
  if (draft.value?.original === null) {
    result.push({ key: '', value: '', isNew: true });
  }
  return result;
});

async function load() {
  metadata.value = await api('metadata', props.scope);
}

function edit(row) {
  error.value = '';
  success.value = '';
  draft.value = {
    original: row ? row.key : null,
    key: row ? row.key : '',
    value: row ? JSON.stringify(row.value, null, 2) : '""'
  };
}

function isEditing(row) {
  return draft.value && (row.isNew || draft.value.original === row.key);
}

function cancel() {
  draft.value = null;
  error.value = '';
}

function save() {
  run(async () => {
    const creating = draft.value.original === null;
    const key = creating ? draft.value.key.trim() : draft.value.original;
    if (!key) {
      throw new Error(t('ui.enter-a-metadata-key'));
    }
    if (creating && Object.hasOwn(metadata.value, key)) {
      throw new Error(t('ui.this-key-already-exists-edit-its'));
    }
    if (creating && Object.keys(metadata.value).length >= 1000) {
      throw new Error(t('ui.you-can-save-up-to-1'));
    }

    let value;
    try {
      value = JSON.parse(draft.value.value);
    } catch {
      throw new Error(t('ui.enter-a-valid-json-value-use'));
    }
    if (value === null) {
      throw new Error(t('ui.the-value-cannot-be-null-use'));
    }

    // 每次只提交当前行，其他键仍由后端保留。
    await api('metadata', props.scope, 'PUT', { values: { [key]: value } });
    draft.value = null;
    await load();
  }, t('ui.saved'));
}

function remove(key) {
  if (!window.confirm(t('ui.delete-metadata-key', key))) {
    return;
  }
  run(async () => {
    await api('metadata', props.scope, 'DELETE', { keys: [key] });
    await load();
  }, t('ui.saved'));
}

onMounted(() => run(load));
</script>

<template>
  <section class="metadata-panel">
    <Teleport :to="toolbar">
      <button
        :disabled="busy || !!draft"
        @click="run(load)"
      >
        {{ $t('ui.refresh') }}
      </button>
      <button
        class="primary"
        :disabled="busy || !!draft || !metadata"
        @click="edit(null)"
      >
        {{ $t('ui.add') }}
      </button>
    </Teleport>
    <Feedback
      :error="error"
      :success="success"
    />
    <div class="table-scroll">
      <table
        class="metadata-table"
        :aria-label="$t('ui.metadata')"
      >
        <colgroup>
          <col class="metadata-key" />
          <col />
          <col class="metadata-actions" />
        </colgroup>
        <thead>
          <tr>
            <th scope="col">{{ $t('ui.key') }}</th>
            <th scope="col">{{ $t('ui.value-json') }}</th>
            <th scope="col">{{ $t('ui.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in rows"
            :key="row.isNew ? 'draft' : 'key:' + row.key"
          >
            <td>
              <input
                v-if="row.isNew"
                v-model="draft.key"
                :aria-label="$t('ui.metadata-key')"
                :placeholder="$t('ui.e-g-region')"
                :disabled="busy"
              />
              <code v-else>{{ row.key }}</code>
            </td>
            <td>
              <textarea
                v-if="isEditing(row)"
                v-model="draft.value"
                :aria-label="$t('ui.metadata-value-json')"
                rows="2"
                spellcheck="false"
                :disabled="busy"
              />
              <code
                v-else
                class="metadata-value"
              >
                {{ JSON.stringify(row.value) }}
              </code>
            </td>
            <td>
              <div
                v-if="isEditing(row)"
                class="actions"
              >
                <button
                  class="text-button"
                  :disabled="busy"
                  @click="save"
                >
                  {{ $t('ui.save') }}
                </button>
                <button
                  class="text-button"
                  :disabled="busy"
                  @click="cancel"
                >
                  {{ $t('ui.cancel') }}
                </button>
              </div>
              <div
                v-else
                class="actions"
              >
                <button
                  class="text-button"
                  :disabled="busy || !!draft"
                  @click="edit(row)"
                >
                  {{ $t('ui.edit') }}
                </button>
                <button
                  class="text-button"
                  :disabled="busy || !!draft"
                  @click="remove(row.key)"
                >
                  {{ $t('ui.delete') }}
                </button>
              </div>
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td
              colspan="3"
              class="empty"
            >
              {{
                busy
                  ? $t('ui.loading')
                  : metadata
                    ? $t('ui.no-metadata')
                    : $t('ui.metadata-has-not-been-loaded')
              }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
