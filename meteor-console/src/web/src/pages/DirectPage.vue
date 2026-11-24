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
  <h1 class="direct-title">{{ $t('ui.direct-connection') }}</h1>
  <div class="direct-layout">
    <form
      class="direct-form"
      :aria-label="$t('ui.connection-details')"
      :aria-busy="busy"
      @submit.prevent="connect"
    >
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
      </div>
      <Feedback :error="error" />
      <div class="direct-form-footer">
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
        <h3>{{ $t('ui.instance-endpoint') }}</h3>
        <p id="direct-endpoint-hint">{{ $t('ui.copy-the-full-address-from-the') }}</p>
      </section>
      <section>
        <h3>{{ $t('ui.management-token') }}</h3>
        <p id="direct-token-hint">{{ $t('ui.the-target-instance-provides-the-token') }}</p>
      </section>
    </aside>
  </div>
</template>
