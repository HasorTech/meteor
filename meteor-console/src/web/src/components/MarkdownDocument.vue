<script setup>
import { computed } from 'vue';
import { renderMarkdown } from '../markdown.js';
import 'highlight.js/styles/github.css';

const props = defineProps({
  markdown: { type: String, required: true },
  javaDescription: Object
});
const html = computed(() => renderMarkdown(props.markdown, props.javaDescription));
</script>
<template>
  <article class="markdown-document">
    <div
      class="markdown-content"
      v-html="html"
    />
  </article>
</template>
