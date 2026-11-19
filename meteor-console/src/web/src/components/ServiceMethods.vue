<script setup>
import { computed } from 'vue';
import MethodField from './MethodField.vue';

const props = defineProps({
  description: Object,
  definitions: Array,
  emptyText: String,
  debuggable: Boolean
});
const emit = defineEmits(['debug']);
const entries = computed(() =>
  (props.definitions || [props.description])
    .filter(Boolean)
    .flatMap((description) => (description.methods || []).map((method) => ({ method, description })))
);
function signature(method) {
  return (
    method.javaSignature ||
    `${method.name || method.javaName}(${(method.parameters || []).map((field) => field.type || '?').join(', ')})`
  );
}
</script>

<template>
  <section
    class="info-panel service-methods"
    role="region"
    :aria-label="$t('ui.method-2')"
  >
    <header class="methods-header">
      <h3 class="methods-title">{{ $t('ui.methods') }}</h3>
      <slot name="tools" />
    </header>
    <p
      v-if="description?.extensions?.description"
      class="method-description"
    >
      {{ description.extensions.description }}
    </p>
    <slot name="feedback" />
    <p
      v-if="!entries.length"
      class="empty"
    >
      {{ emptyText ?? $t('ui.no-method-description') }}
    </p>
    <article
      v-else
      class="method-contract"
    >
      <details
        v-for="({ method, description }, methodIndex) in entries"
        :key="`${signature(method)}:${methodIndex}`"
        class="method-detail"
      >
        <summary>
          <code
            class="method-signature"
            :title="signature(method)"
          >
            <strong>{{ method.name || method.javaName }}</strong>
            <span>{{ signature(method).slice(signature(method).indexOf('(')) }}</span>
          </code>
          <span
            v-if="method.message"
            class="badge"
          >
            {{ $t('ui.one-way-message') }}
          </span>
          <button
            v-if="debuggable && method.javaSignature"
            type="button"
            class="text-button method-debug"
            @click.stop.prevent="emit('debug', method)"
          >
            {{ $t('ui.debug-2') }}
          </button>
        </summary>
        <div class="method-detail-body">
          <p
            v-if="method.extensions?.description"
            class="method-description"
          >
            {{ method.extensions.description }}
          </p>
          <div class="method-io">
            <section
              class="method-inputs"
              :aria-label="$t('ui.method-request')"
            >
              <details
                class="method-structure"
                open
              >
                <summary class="method-io-heading">
                  <span>{{ $t('ui.request') }}</span>
                  <code
                    v-for="(field, fieldIndex) in method.parameters || []"
                    :key="fieldIndex"
                    class="method-field-type"
                  >
                    <template v-if="method.parameters.length > 1">
                      {{ field.name || field.javaName || `arg${field.argIndex ?? fieldIndex}` }}:
                    </template>
                    {{ field.type || $t('ui.type-not-reported') }}
                  </code>
                </summary>
                <ul
                  v-if="method.parameters?.length"
                  class="method-fields"
                  :aria-label="$t('ui.request-structure')"
                >
                  <MethodField
                    v-for="(field, fieldIndex) in method.parameters"
                    :key="fieldIndex"
                    :field="field"
                    :types="description.types || {}"
                    :show-type="false"
                  />
                </ul>
                <p
                  v-else
                  class="muted"
                >
                  {{ $t('ui.no-parameters') }}
                </p>
              </details>
            </section>
            <section
              class="method-returns"
              :aria-label="$t('ui.method-response')"
            >
              <details
                class="method-structure"
                open
              >
                <summary class="method-io-heading">
                  <span>{{ $t('ui.response-2') }}</span>
                  <code class="method-field-type">
                    {{ method.returns?.type || (method.message ? 'void' : $t('ui.type-not-reported')) }}
                  </code>
                </summary>
                <ul
                  v-if="method.returns?.type && method.returns.type !== 'void'"
                  class="method-fields"
                  :aria-label="$t('ui.response-structure')"
                >
                  <MethodField
                    :field="method.returns"
                    :types="description.types || {}"
                    :show-name="false"
                    :show-type="false"
                  />
                </ul>
                <p
                  v-else
                  class="muted"
                >
                  {{
                    method.returns?.type === 'void' || method.message
                      ? $t('ui.no-response-content')
                      : $t('ui.response-description-not-reported')
                  }}
                </p>
              </details>
            </section>
          </div>
          <template v-if="method.exceptions?.length">
            <h4>{{ $t('ui.declared-exceptions') }}</h4>
            <ul class="method-exceptions">
              <li
                v-for="exception in method.exceptions"
                :key="exception"
              >
                <code>{{ exception }}</code>
              </li>
            </ul>
          </template>
        </div>
      </details>
    </article>
    <slot name="footer" />
  </section>
</template>
