import { computed, shallowRef } from 'vue';

const preference = 'meteor-console.language';
const bundles = new Map();
const current = shallowRef({ locale: 'zh-CN', messages: {} });
export const locale = computed(() => current.value.locale);

export function savedLocale() {
  return localStorage.getItem(preference) === 'en-US' ? 'en-US' : 'zh-CN';
}

export async function setLocale(language) {
  let bundle = bundles.get(language);
  if (!bundle) {
    const response = await fetch('/api/i18n', {
      headers: { 'Accept-Language': language },
      signal: AbortSignal.timeout(5000)
    });
    if (!response.ok) throw new Error('Could not load the console language pack.');
    bundle = await response.json();
    if (bundle.locale !== language || !bundle.messages || typeof bundle.messages !== 'object') {
      throw new Error('Invalid console language pack.');
    }
    bundles.set(language, bundle);
  }
  document.documentElement.lang = language;
  localStorage.setItem(preference, language);
  current.value = bundle;
}

export function t(key, ...args) {
  const message = current.value.messages[key] ?? key;
  return message.replace(/\{(\d+)\}/g, (token, index) => args[index] ?? token);
}
