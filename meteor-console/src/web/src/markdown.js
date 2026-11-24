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

const javaImports = /^(?:[ \t]*import[ \t]+(?:static[ \t]+)?[\p{ID_Continue}.*$]+;[ \t]*\r?\n\s*)+/u;
const javaTokens = /"""(?:\\[\s\S]|(?!""")[\s\S])*"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|[\p{ID_Start}_$][\p{ID_Continue}.$]*/gu;
const platformType = /^java\.(?:[a-z]+\.)+([A-Z]\w*)/;

function javaTypeNames(description) {
  const names = new Map();
  for (const type of [description, ...Object.values(description?.types || {})]) {
    const sourceName = type?.extensions?.javaSourceType;
    const qualified = typeof sourceName === 'string' && sourceName ? sourceName : type?.javaType;
    if (typeof qualified === 'string' && qualified.includes('.') && !qualified.endsWith('[]')) {
      names.set(qualified, qualified.slice(qualified.lastIndexOf('.') + 1));
    }
  }
  return names;
}

function compactJava(code, names) {
  return code.replace(javaImports, '').replace(javaTokens, (token) => {
    // 字符串、注释与内嵌契约里的协议名称需要保留原值。
    if (/^["'/]/.test(token)) return token;
    let end = token.length;
    while (end > 0) {
      const name = names.get(token.slice(0, end));
      if (name) return name + token.slice(end);
      end = token.lastIndexOf('.', end - 1);
    }
    return token.replace(platformType, '$1');
  });
}

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
  highlight(code, language, filename) {
    const escaped = markdown.utils.escapeHtml;
    const content = hljs.getLanguage(language)
      ? hljs.highlight(code, { language, ignoreIllegals: true }).value
      : escaped(code);
    const label = /^[\p{L}\p{N}_./ -]+$/u.test(filename || '')
      ? `<span class="markdown-code-file">${escaped(filename)}</span>`
      : '';
    return `<pre tabindex="0">${label}<code class="hljs language-${escaped(language)}">${content}</code></pre>`;
  }
}).disable('image');

// 文档只允许普通网页、邮件和页内链接，不能把返回内容作为可执行 HTML。
markdown.validateLink = (value) => /^(https?:|mailto:)/i.test(value) || value.startsWith('#');
const renderFence = markdown.renderer.rules.fence;
markdown.renderer.rules.fence = (tokens, index, options, environment, renderer) => {
  const token = tokens[index];
  if (token.info.trim().split(/\s+/)[0] === 'java') {
    token.content = compactJava(token.content, environment.javaTypeNames);
  }
  return renderFence(tokens, index, options, environment, renderer);
};
markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  tokens[index].attrSet('target', '_blank');
  tokens[index].attrSet('rel', 'noopener noreferrer');
  return renderer.renderToken(tokens, index, options);
};

export function renderMarkdown(value, javaDescription) {
  return markdown.render(value || '', { javaTypeNames: javaTypeNames(javaDescription) });
}
