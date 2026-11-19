import { locale, t } from './i18n';
export function createApi(onUnauthorized, directCredential = () => undefined) {
  return async function api(path, params = {}, method = 'GET', body, options = {}) {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== undefined && value !== '') query.set(key, value);
    }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeout ?? 30000);
    try {
      const headers = { 'Content-Type': 'application/json', 'Accept-Language': locale.value };
      if (params.mode === 'direct') {
        const token = options.managementToken ?? directCredential(params.target);
        if (!token) throw new Error(t('ui.return-to-direct-connection-and-enter'));
        const bytes = new TextEncoder().encode(token);
        headers['X-Meteor-Management-Token'] = btoa(String.fromCharCode(...bytes));
      }
      const response = await fetch(`/api/${path}?${query}`, {
        method,
        credentials: 'same-origin',
        signal: controller.signal,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body)
      });
      let result;
      try {
        result = await response.json();
      } catch (error) {
        if (error.name === 'AbortError') throw error;
        if (response.status === 404) {
          throw new Error(t('ui.the-console-endpoint-was-not-found'));
        }
        // 开发代理在后端未启动时可能返回空白或 HTML 错误页。
        throw new Error(t('ui.the-console-is-unavailable'));
      }
      if (!response.ok) {
        if (response.status === 401 && path !== 'auth/login') onUnauthorized?.();
        const error = new Error(result.error || t('ui.operation-failed', response.status));
        error.status = response.status;
        throw error;
      }
      return result;
    } catch (error) {
      if (error.name === 'AbortError') {
        if (method === 'GET') {
          throw new Error(t('ui.the-console-is-unavailable'));
        }
        throw new Error(t('ui.the-request-timed-out-the-write'));
      }
      if (error instanceof TypeError) {
        throw new Error(t('ui.the-console-is-unavailable'));
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  };
}
