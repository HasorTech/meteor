import {t} from './i18n';

export function instanceSearchText(instance) {
  return [instance?.ipAddress, instance?.processId, instance?.instanceId].join(' ').toLowerCase();
}

export function instanceRole(instance) {
  const roles = instance.roles || [];
  const provider = roles.includes('Provider');
  const consumer = roles.includes('Consumer');
  if (provider && consumer) return t('ui.provide-consume');
  if (provider) return t('ui.providers');
  if (consumer) return t('ui.consumers');
  return '';
}
