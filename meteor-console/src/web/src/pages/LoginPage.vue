<script setup>
import { inject, ref } from 'vue';
import { useFeedback } from '../useFeedback';
import LanguageSelect from '../components/LanguageSelect.vue';
import Feedback from '../components/Feedback.vue';
defineProps({ connectionError: String });
const emit = defineEmits(['login']);
const api = inject('api');
const username = ref('');
const password = ref('');
const { busy, error, run } = useFeedback();
function login() {
  run(async () => {
    const session = await api('auth/login', {}, 'POST', {
      username: username.value,
      password: password.value
    });
    password.value = '';
    emit('login', session);
  });
}
</script>
<template>
  <main class="login-page">
    <LanguageSelect />
    <a
      class="login-brand"
      href="#"
    >
      Meteor
    </a>
    <form
      class="login-form"
      @submit.prevent="login"
    >
      <h1>{{ $t('ui.sign-in-to-the-console') }}</h1>
      <input
        v-model="username"
        :aria-label="$t('ui.username-2')"
        :placeholder="$t('ui.username-2')"
        autocomplete="username"
        required
        maxlength="100"
        autofocus
      />
      <input
        v-model="password"
        :aria-label="$t('ui.password')"
        :placeholder="$t('ui.password')"
        type="password"
        autocomplete="current-password"
        required
        maxlength="1024"
      />
      <Feedback :error="error || connectionError" />
      <button
        class="primary"
        :disabled="busy"
      >
        {{ busy ? $t('ui.signing-in') : $t('ui.sign-in') }}
      </button>
    </form>
  </main>
</template>
