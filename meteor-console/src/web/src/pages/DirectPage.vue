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
      <h1>{{ $t('ui.direct-connection') }}</h1>
      <p class="direct-introduction">{{ $t('ui.connect-to-an-instance-to-view') }}</p>
    </div>
  </div>
  <div class="direct-layout">
    <form
      class="direct-form"
      aria-labelledby="direct-form-title"
      :aria-busy="busy"
      @submit.prevent="connect"
    >
      <h2 id="direct-form-title">{{ $t('ui.connection-details') }}</h2>
      <div class="field">
        <label for="direct-endpoint">{{ $t('ui.instance-endpoint') }}</label>
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
          {{ $t('ui.enter-the-full-endpoint-address-exposed') }}
        </p>
      </div>
      <div class="field">
        <label for="direct-token">{{ $t('ui.management-token') }}</label>
        <input
          id="direct-token"
          v-model="managementToken"
          type="password"
          autocomplete="off"
          :disabled="busy"
          required
          maxlength="4096"
          :placeholder="$t('ui.enter-the-target-instance-s-management')"
          aria-describedby="direct-token-hint"
        />
        <p
          id="direct-token-hint"
          class="field-hint"
        >
          {{ $t('ui.use-the-token-configured-on-the') }}
        </p>
      </div>
      <Feedback :error="error" />
      <div class="direct-form-footer">
        <span class="muted">{{ $t('ui.on-success-opens-the-instance-details') }}</span>
        <button
          type="submit"
          class="primary"
          :disabled="busy"
        >
          {{ busy ? $t('ui.connecting') : $t('ui.connect') }}
        </button>
      </div>
    </form>
    <aside
      class="direct-help"
      aria-labelledby="direct-help-title"
    >
      <h2 id="direct-help-title">{{ $t('ui.before-connecting') }}</h2>
      <section>
        <h3>{{ $t('ui.get-the-instance-endpoint') }}</h3>
        <p>{{ $t('ui.copy-the-full-address-from-the') }}</p>
      </section>
      <section>
        <h3>{{ $t('ui.get-the-management-token') }}</h3>
        <p>{{ $t('ui.the-target-instance-provides-the-token') }}</p>
      </section>
    </aside>
  </div>
</template>
