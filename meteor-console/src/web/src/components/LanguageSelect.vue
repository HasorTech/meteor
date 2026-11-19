<script setup>
import {ref} from 'vue';
import {locale, setLocale, t} from '../i18n';

const loading = ref(false);
const error = ref('');

async function change(event) {
  const select = event.target;
  loading.value = true;
  error.value = '';
  try {
    await setLocale(select.value);
  } catch {
    select.value = locale.value;
    error.value = t('ui.language-load-failed');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="language-picker">
    <select
        :aria-label="$t('ui.language')"
        :value="locale"
        :disabled="loading"
        @change="change"
    >
      <option
          lang="zh-CN"
          value="zh-CN"
      >
        简体中文
      </option>
      <option
          lang="en-US"
          value="en-US"
      >
        English
      </option>
    </select>
    <span
        v-if="error"
        class="language-error"
        role="alert"
    >
      {{ error }}
    </span>
  </div>
</template>
