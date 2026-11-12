<script setup>
import BreadcrumbTrail from '../components/BreadcrumbTrail.vue';
import { computed, inject, reactive, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import { trackDraft } from '../useNavigationGuard';
import Feedback from '../components/Feedback.vue';
const props = defineProps({ profile: Object });
const emit = defineEmits(['saved']);
const api = inject('api');
const navigate = inject('navigate');
const settings = inject('consoleSettings');
const types = computed(() => settings.value?.features.registryTypes || []);
const databases = computed(() => settings.value?.features.jdbcDatabases || []);
function typeAvailable(type) {
  return (
    types.value.some(({ key }) => key === type) &&
    (type !== 'jdbc' || databases.value.some(({ available }) => available))
  );
}
const initial = {
  id: props.profile?.id,
  name: props.profile?.name || '',
  type:
    props.profile?.type ||
    (typeAvailable('nacos') ? 'nacos' : types.value.find(({ key }) => typeAvailable(key))?.key || ''),
  address: props.profile?.address || '',
  database: props.profile?.database || databases.value.find(({ available }) => available)?.key || '',
  username: props.profile?.username || '',
  password: null,
  managementToken: null,
  group: props.profile?.group || 'meteor',
  namespace: props.profile?.namespace || '',
  root: props.profile?.root || '/meteor'
};
const form = reactive({ ...initial });
const { busy, error, success, run } = useFeedback();
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
const typeName = computed(() => types.value.find(({ key }) => key === form.type)?.label || form.type);
const unavailableReason = computed(() => {
  if (!typeAvailable(form.type)) return '当前控制台未安装此注册中心类型所需的组件。';
  if (form.type !== 'jdbc') return '';
  const database = databases.value.find(({ key }) => key === form.database);
  return database?.available ? '' : database?.reason || '当前控制台不支持此数据库。';
});
function changeType(type) {
  if (form.type === type) return;
  form.type = type;
  form.address = '';
  form.password = null;
  form.managementToken = null;
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
      managementToken: form.managementToken
    };
  }
  const { managementToken, ...storage } = form;
  return storage;
}
function testConnection() {
  run(async () => {
    await api('clusters/test', {}, 'POST', connectionPayload());
    verified.value = signature.value;
  }, '连接成功');
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
      集群连接
    </button>
    <span>/</span>
    <span>{{ profile ? '编辑连接' : '连接注册中心' }}</span>
    <span>/</span>
    <strong>{{ typeName }}</strong>
  </BreadcrumbTrail>
  <nav
    class="tabs connection-types page-actionbar"
    aria-label="注册中心类型"
  >
    <button
      v-for="{ key, label } in types"
      :key="key"
      type="button"
      :class="{ active: form.type === key }"
      :aria-current="form.type === key ? 'page' : undefined"
      :disabled="busy || !typeAvailable(key)"
      :title="typeAvailable(key) ? undefined : '当前控制台未安装此注册中心类型所需的组件'"
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
      <label class="connection-field">
        <span class="field-caption">
          <span
            class="required-mark"
            aria-hidden="true"
          >
            *
          </span>
          连接名称
        </span>
        <input
          v-model="form.name"
          required
          maxlength="100"
          placeholder="例如：生产 · 华东"
          :disabled="busy"
        />
      </label>
      <label
        v-if="form.type === 'jdbc'"
        class="connection-field"
      >
        <span class="field-caption">数据库</span>
        <select
          aria-label="数据库"
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
            {{ database.label }}{{ database.available ? '' : '（不可用）' }}
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
          {{ form.type === 'jdbc' ? 'JDBC URL' : form.type === 'remote' ? 'RPC 地址' : '服务地址' }}
        </span>
        <input
          v-model="form.address"
          required
          :placeholder="placeholders[form.type]"
          :disabled="busy"
        />
      </label>
      <template v-if="form.type === 'nacos'">
        <label class="connection-field">
          <span class="field-caption">命名空间</span>
          <input
            v-model="form.namespace"
            placeholder="public"
            :disabled="busy"
          />
        </label>
        <label class="connection-field">
          <span class="field-caption">分组</span>
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
        <span class="field-caption">根路径</span>
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
        <span class="field-caption">用户名</span>
        <input
          v-model="form.username"
          autocomplete="off"
          placeholder="可选"
          :disabled="busy"
        />
      </label>
      <label
        v-if="form.type !== 'remote'"
        class="connection-field"
      >
        <span class="field-caption">密码</span>
        <input
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          :placeholder="profile?.hasPassword ? '留空保持已保存的密码' : '可选'"
          :disabled="busy"
        />
      </label>
      <label
        v-if="form.type === 'remote'"
        class="connection-field"
      >
        <span class="field-caption">管理凭据</span>
        <input
          v-model="form.managementToken"
          :required="!profile?.hasManagementToken"
          type="password"
          autocomplete="new-password"
          :placeholder="profile?.hasManagementToken ? '留空保持已保存的凭据' : '远端管理服务的凭据'"
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
            :disabled="busy || !!unavailableReason || !form.name.trim() || !form.address.trim()"
            @click="testConnection"
          >
            {{ busy ? '处理中…' : '测试连接' }}
          </button>
          <button
            class="primary"
            :disabled="busy || !!unavailableReason || !tested"
          >
            保存并进入
          </button>
          <button
            type="button"
            :disabled="busy"
            @click="navigate({ view: 'clusters' })"
          >
            取消
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
