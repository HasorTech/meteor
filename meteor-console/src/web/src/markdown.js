import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js/lib/core';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import python from 'highlight.js/lib/languages/python';
import c from 'highlight.js/lib/languages/c';
import cpp from 'highlight.js/lib/languages/cpp';
import go from 'highlight.js/lib/languages/go';
import bash from 'highlight.js/lib/languages/bash';
import protobuf from 'highlight.js/lib/languages/protobuf';
import xml from 'highlight.js/lib/languages/xml';
import json from 'highlight.js/lib/languages/json';
import properties from 'highlight.js/lib/languages/properties';

for (const [name, grammar] of Object.entries({
  java,
  javascript,
  python,
  c,
  cpp,
  go,
  bash,
  protobuf,
  xml,
  json,
  properties
})) {
  hljs.registerLanguage(name, grammar);
}

const markdown = new MarkdownIt({
  html: false,
  linkify: false,
  highlight(code, language) {
    const escaped = markdown.utils.escapeHtml;
    const content = hljs.getLanguage(language)
      ? hljs.highlight(code, { language, ignoreIllegals: true }).value
      : escaped(code);
    return `<pre tabindex="0"><code class="hljs language-${escaped(language)}">${content}</code></pre>`;
  }
}).disable('image');

// 文档只允许普通网页、邮件和页内链接，不能把返回内容作为可执行 HTML。
markdown.validateLink = (value) => /^(https?:|mailto:)/i.test(value) || value.startsWith('#');
markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  tokens[index].attrSet('target', '_blank');
  tokens[index].attrSet('rel', 'noopener noreferrer');
  return renderer.renderToken(tokens, index, options);
};

export function renderMarkdown(value) {
  return markdown.render(value || '');
}
