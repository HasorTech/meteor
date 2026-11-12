<script setup>
import { computed } from 'vue';

const props = defineProps({
  field: { type: Object, required: true },
  types: { type: Object, default: () => ({}) },
  ancestors: { type: Array, default: () => [] },
  showName: { type: Boolean, default: true },
  showType: { type: Boolean, default: true }
});
const type = computed(() => props.types[props.field.type]);
const recursive = computed(() => props.ancestors.includes(props.field.type));
const children = computed(() => {
  if (recursive.value || !type.value) return [];
  if (type.value.kind === 'array') return [{ name: '元素', type: type.value.elementType }];
  if (type.value.kind === 'collection') return [{ name: '元素', type: type.value.arguments?.[0] }];
  if (type.value.kind === 'map') {
    return [
      { name: '键', type: type.value.arguments?.[0] },
      { name: '值', type: type.value.arguments?.[1] }
    ];
  }
  return type.value.properties || [];
});
const collapsible = computed(
  () =>
    !recursive.value &&
    (props.showName || props.showType) &&
    (children.value.length > 0 || type.value?.values?.length > 0)
);
</script>

<template>
  <li
    class="method-field"
    :class="{ 'method-field-anonymous': !showName && !showType }"
  >
    <component
      :is="collapsible ? 'details' : 'div'"
      class="method-field-node"
      :open="collapsible && !ancestors.length ? true : undefined"
    >
      <component
        v-if="showName || showType || field.extensions?.description || recursive"
        :is="collapsible ? 'summary' : 'div'"
        class="method-field-heading"
      >
        <code
          v-if="showName"
          class="method-field-name"
        >
          {{ field.name || field.javaName || `arg${field.argIndex ?? 0}` }}
        </code>
        <code
          v-if="showType"
          class="method-field-type"
        >
          {{ field.type || '未上报类型' }}
        </code>
        <span
          v-if="field.extensions?.description"
          class="method-description"
        >
          {{ field.extensions.description }}
        </span>
        <span
          v-if="recursive"
          class="muted"
        >
          递归引用
        </span>
      </component>
      <p
        v-if="type?.extensions?.description"
        class="method-description"
      >
        {{ type.extensions.description }}
      </p>
      <ul
        v-if="children.length"
        class="method-field-children"
      >
        <MethodField
          v-for="(child, index) in children"
          :key="index"
          :field="child"
          :types="types"
          :ancestors="[...ancestors, field.type]"
        />
      </ul>
      <ul
        v-if="!recursive && type?.values?.length"
        class="method-enum-values"
      >
        <li
          v-for="(value, index) in type.values"
          :key="index"
        >
          <code>{{ value.name || value.javaName }}</code>
          <span
            v-if="value.extensions?.description"
            class="method-description"
          >
            {{ value.extensions.description }}
          </span>
        </li>
      </ul>
    </component>
  </li>
</template>
