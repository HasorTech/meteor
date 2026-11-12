<script setup>
import { inject, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import Feedback from '../components/Feedback.vue';
const target = ref('');
const managementToken = ref('');
const api = inject('api');
const directCredentials = inject('directCredentials');
const navigate = inject('navigate');
const { busy, error, run } = useFeedback();
function connect() {
  run(async () => {
    const info = await api('instance', { mode: 'direct', target: target.value }, 'GET', undefined, {
      managementToken: managementToken.value
    });
    directCredentials.set(target.value, managementToken.value);
    navigate({
      view: 'instance',
      target: target.value,
      application: info.instance.applicationName,
      instanceId: info.instance.instanceId
    });
  });
}
</script>
<template>
  <div class="tabbar">
    <div>
      <h1>直连实例</h1>
      <p class="direct-introduction">连接目标实例，查看实例状态、服务清单并进行服务治理。</p>
    </div>
  </div>
  <div class="direct-layout">
    <form
      class="direct-form"
      aria-labelledby="direct-form-title"
      :aria-busy="busy"
      @submit.prevent="connect"
    >
      <h2 id="direct-form-title">连接信息</h2>
      <div class="field">
        <label for="direct-endpoint">实例 Endpoint</label>
        <input
          id="direct-endpoint"
          v-model="target"
          class="direct-endpoint"
          autocomplete="off"
          :spellcheck="false"
          :disabled="busy"
          required
          placeholder="rsf://127.0.0.1:19200"
          aria-describedby="direct-endpoint-hint"
        />
        <p
          id="direct-endpoint-hint"
          class="field-hint"
        >
          填写实例对外提供的完整 Endpoint 地址。
        </p>
      </div>
      <div class="field">
        <label for="direct-token">管理凭据</label>
        <input
          id="direct-token"
          v-model="managementToken"
          type="password"
          autocomplete="off"
          :disabled="busy"
          required
          maxlength="4096"
          placeholder="请输入目标实例的管理凭据"
          aria-describedby="direct-token-hint"
        />
        <p
          id="direct-token-hint"
          class="field-hint"
        >
          使用目标实例配置的管理凭据。
        </p>
      </div>
      <Feedback :error="error" />
      <div class="direct-form-footer">
        <span class="muted">连接成功后进入实例详情</span>
        <button
          type="submit"
          class="primary"
          :disabled="busy"
        >
          {{ busy ? '连接中…' : '连接' }}
        </button>
      </div>
    </form>
    <aside
      class="direct-help"
      aria-labelledby="direct-help-title"
    >
      <h2 id="direct-help-title">连接准备</h2>
      <section>
        <h3>获取实例 Endpoint</h3>
        <p>在已登记实例的详情页中，复制 Endpoint 字段的完整地址。</p>
      </section>
      <section>
        <h3>准备管理凭据</h3>
        <p>凭据由目标实例配置，仅在当前页面会话中使用。刷新页面后需要重新填写。</p>
      </section>
    </aside>
  </div>
</template>
