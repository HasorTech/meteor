<script setup>
import {t} from '../i18n';
import BreadcrumbTrail from '../components/BreadcrumbTrail.vue';
import {computed, inject, reactive, ref} from 'vue';
import {useFeedback} from '../useFeedback';
import {trackDraft} from '../useNavigationGuard';
import Feedback from '../components/Feedback.vue';

const props = defineProps({profile: Object});
const emit = defineEmits(['saved']);
const api = inject('api');
const navigate = inject('navigate');
const settings = inject('consoleSettings');
const types = computed(() => settings.value?.features.registryTypes || []);
const databases = computed(() => settings.value?.features.jdbcDatabases || []);

function typeAvailable(type) {
  return (
      types.value.some(({key}) => key === type) &&
      (type !== 'jdbc' || databases.value.some(({available}) => available))
  );
}

const initial = {
  id: props.profile?.id,
  name: props.profile?.name || '',
  type:
      props.profile?.type ||
      (typeAvailable('nacos') ? 'nacos' : types.value.find(({key}) => typeAvailable(key))?.key || ''),
  address: props.profile?.address || '',
  database: props.profile?.database || databases.value.find(({available}) => available)?.key || '',
  username: props.profile?.username || '',
  password: null,
  managementToken: null,
  remoteClusterId: props.profile?.remoteClusterId || '',
  group: props.profile?.group || 'meteor',
  namespace: props.profile?.namespace || '',
  root: props.profile?.root || '/meteor'
};
const form = reactive({...initial});
const {busy, error, success, run} = useFeedback();
const verified = ref('');
const committed = ref(false);
const dirty = computed(() => !committed.value && JSON.stringify(form) !== JSON.stringify(initial));
trackDraft(() => dirty.value);
const signature = computed(() => JSON.stringify(form));
const tested = computed(() => verified.value === signature.value);
const placeholders = {
  remote: 'rsf://registry.example.com:19193',
  nacos: 'nacos.example.com:8848',
  zookeeper: 'zk.example.com:2181',
  jdbc: 'jdbc:postgresql://db.example.com:5432/meteor'
};
const typeName = computed(() => types.value.find(({key}) => key === form.type)?.label || form.type);
const unavailableReason = computed(() => {
  if (!typeAvailable(form.type)) return t('ui.the-console-does-not-have-the-3');
  if (form.type !== 'jdbc') return '';
  const database = databases.value.find(({key}) => key === form.database);
  return database?.available ? '' : database?.reason || t('ui.the-console-does-not-support-this');
});

function changeType(type) {
  if (form.type === type) return;
  form.type = type;
  form.address = '';
  form.password = null;
  form.managementToken = null;
  form.remoteClusterId = '';
  error.value = '';
  success.value = '';
}

function connectionPayload() {
  if (form.type === 'remote') {
    return {
      id: form.id,
      name: form.name,
      type: form.type,
      address: form.address,
      managementToken: form.managementToken,
      remoteClusterId: form.remoteClusterId.trim()
    };
  }
  const {managementToken, remoteClusterId, ...storage} = form;
  return storage;
}

function testConnection() {
  run(async () => {
    await api('clusters/test', {}, 'POST', connectionPayload());
    verified.value = signature.value;
  }, t('ui.connected'));
}

function save() {
  run(async () => {
    const cluster = await api('clusters', {}, 'POST', connectionPayload());
    committed.value = true;
    emit('saved', cluster);
  });
}
</script>
<template>
  <BreadcrumbTrail>
    <button
        class="text-button"
        @click="navigate({ view: 'clusters' })"
    >
      {{ $t('ui.cluster-connections') }}
    </button>
    <span>/</span>
    <span>{{ profile ? $t('ui.edit-connection') : $t('ui.connect-to-a-registry') }}</span>
    <span>/</span>
    <strong>{{ typeName }}</strong>
  </BreadcrumbTrail>
  <nav
      class="tabs connection-types page-actionbar"
      :aria-label="$t('ui.registry-type')"
  >
    <button
        v-for="{ key, label } in types"
        :key="key"
        type="button"
        :class="{ active: form.type === key }"
        :aria-current="form.type === key ? 'page' : undefined"
        :disabled="busy || !typeAvailable(key)"
        :title="typeAvailable(key) ? undefined : $t('ui.the-console-does-not-have-the-2')"
        @click="changeType(key)"
    >
      {{ label }}
    </button>
  </nav>
  <div class="connection-layout">
    <form
        class="connection-editor"
        @submit.prevent="tested ? save() : testConnection()"
    >
      <label
          v-if="profile?.id"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.local-cluster-id') }}</span>
        <input
            :value="profile.id"
            readonly
        />
      </label>
      <label class="connection-field">
        <span class="field-caption">
          <span
              class="required-mark"
              aria-hidden="true"
          >
            *
          </span>
          {{ $t('ui.connection-name') }}
        </span>
        <input
            v-model="form.name"
            required
            maxlength="100"
            :placeholder="$t('ui.e-g-production-east-china')"
            :disabled="busy"
        />
      </label>
      <label
          v-if="form.type === 'jdbc'"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.database') }}</span>
        <select
            :aria-label="$t('ui.database')"
            v-model="form.database"
            :disabled="busy"
        >
          <option
              v-for="database in databases"
              :key="database.key"
              :value="database.key"
              :disabled="!database.available"
              :title="database.reason || undefined"
          >
            {{ database.label }}{{ database.available ? '' : $t('ui.unavailable') }}
          </option>
        </select>
      </label>
      <label class="connection-field">
        <span class="field-caption">
          <span
              class="required-mark"
              aria-hidden="true"
          >
            *
          </span>
          {{
            form.type === 'jdbc'
                ? 'JDBC URL'
                : form.type === 'remote'
                    ? $t('ui.rpc-address')
                    : $t('ui.service-address')
          }}
        </span>
        <input
            v-model="form.address"
            required
            :placeholder="placeholders[form.type]"
            :disabled="busy"
        />
      </label>
      <label
          v-if="form.type === 'remote'"
          class="connection-field"
      >
        <span class="field-caption">
          <span
              class="required-mark"
              aria-hidden="true"
          >
            *
          </span>
          {{ $t('ui.remote-cluster-id') }}
        </span>
        <input
            v-model="form.remoteClusterId"
            required
            :disabled="busy"
            :placeholder="$t('ui.enter-the-cluster-id-saved-in')"
        />
      </label>
      <template v-if="form.type === 'nacos'">
        <label class="connection-field">
          <span class="field-caption">{{ $t('ui.namespace') }}</span>
          <input
              v-model="form.namespace"
              placeholder="public"
              :disabled="busy"
          />
        </label>
        <label class="connection-field">
          <span class="field-caption">{{ $t('ui.group') }}</span>
          <input
              v-model="form.group"
              placeholder="meteor"
              :disabled="busy"
          />
        </label>
      </template>
      <label
          v-if="form.type === 'zookeeper'"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.root-path') }}</span>
        <input
            v-model="form.root"
            placeholder="/meteor"
            :disabled="busy"
        />
      </label>
      <label
          v-if="form.type !== 'remote'"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.username') }}</span>
        <input
            v-model="form.username"
            autocomplete="off"
            :placeholder="$t('ui.optional')"
            :disabled="busy"
        />
      </label>
      <label
          v-if="form.type !== 'remote'"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.password') }}</span>
        <input
            v-model="form.password"
            type="password"
            autocomplete="new-password"
            :placeholder="profile?.hasPassword ? $t('ui.leave-blank-to-keep-the-saved') : $t('ui.optional')"
            :disabled="busy"
        />
      </label>
      <label
          v-if="form.type === 'remote'"
          class="connection-field"
      >
        <span class="field-caption">{{ $t('ui.management-token') }}</span>
        <input
            v-model="form.managementToken"
            :required="!profile?.hasManagementToken"
            type="password"
            autocomplete="new-password"
            :placeholder="
            profile?.hasManagementToken
              ? $t('ui.leave-blank-to-keep-the-saved-2')
              : $t('ui.token-for-the-remote-management-service')
          "
            :disabled="busy"
        />
      </label>
      <div class="connection-feedback">
        <Feedback
            :error="error || unavailableReason"
            :success="tested ? success : ''"
        />
      </div>
      <div class="connection-footer">
        <div class="actions">
          <button
              type="button"
              :disabled="
              busy ||
              !!unavailableReason ||
              !form.name.trim() ||
              !form.address.trim() ||
              (form.type === 'remote' && !form.remoteClusterId.trim())
            "
              @click="testConnection"
          >
            {{ busy ? $t('ui.processing') : $t('ui.test-connection') }}
          </button>
          <button
              class="primary"
              :disabled="busy || !!unavailableReason || !tested"
          >
            {{ $t('ui.save-and-open') }}
          </button>
          <button
              type="button"
              :disabled="busy"
              @click="navigate({ view: 'clusters' })"
          >
            {{ $t('ui.cancel') }}
          </button>
        </div>
      </div>
    </form>
  </div>
</template>
<style scoped>
.connection-layout {
  padding-top: 12px;
}

.connection-editor {
  --label-width: 116px;
  --field-gap: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  max-width: 760px;
  min-width: 0;
  padding: 2px 0 32px;
}

.connection-field {
  display: grid;
  grid-template-columns: var(--label-width) minmax(0, 1fr);
  align-items: center;
  column-gap: var(--field-gap);
  min-height: 36px;
}

.field-caption {
  text-align: right;
  color: var(--muted);
}

.required-mark {
  color: var(--danger);
  margin-right: 4px;
}

.connection-field input,
.connection-field select {
  width: 100%;
  min-height: 36px;
}

.connection-feedback:empty {
  display: none;
}

.connection-feedback {
  margin-left: calc(var(--label-width) + var(--field-gap));
}

.connection-feedback :deep(.notice) {
  margin-bottom: 0;
}

.connection-footer {
  margin-top: 4px;
  border-top: 1px solid var(--border);
  padding: 20px 0 0 calc(var(--label-width) + var(--field-gap));
}

@media (max-width: 1100px) {
  .connection-editor {
    --label-width: 100px;
    --field-gap: 16px;
  }
}

@media (max-width: 700px) {
  .connection-layout {
    padding-top: 0;
  }

  .connection-editor {
    gap: 16px;
  }

  .connection-field {
    grid-template-columns: minmax(0, 1fr);
    gap: 6px;
  }

  .field-caption {
    text-align: left;
  }

  .connection-feedback {
    margin-left: 0;
  }

  .connection-footer {
    padding-left: 0;
  }
}
</style>
