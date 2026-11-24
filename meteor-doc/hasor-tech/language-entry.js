// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
const path = require('node:path');
const {cp, mkdir, readFile, readdir, writeFile} = require('node:fs/promises');
const {languagePreferenceKey, siteBaseUrl} = require('./routes');

function entryHtml(chinese, english) {
    return `<!doctype html>
<html lang="zh-CN">
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Hasor Tech · Language</title>
<main>
  <h1>选择语言 / Choose a language</h1>
  <ul>
    <li><a data-hasor-locale="zh-cn" href="${chinese}" hreflang="zh-CN" lang="zh-CN">简体中文</a></li>
    <li><a data-hasor-locale="en" href="${english}" hreflang="en" lang="en">English</a></li>
  </ul>
</main>
<script>
  const links = document.querySelectorAll('[data-hasor-locale]');
  const key = ${JSON.stringify(languagePreferenceKey)};
  let choice;
  try { choice = localStorage.getItem(key); } catch {}
  for (const link of links) {
    link.addEventListener('click', () => {
      try { localStorage.setItem(key, link.dataset.hasorLocale); } catch {}
    });
  }
  if (choice !== 'zh-cn' && choice !== 'en') {
    const preferred = (navigator.languages?.length ? navigator.languages : [navigator.language])
      .map(language => language?.toLowerCase().split('-')[0])
      .find(language => language === 'zh' || language === 'en');
    choice = preferred === 'zh' ? 'zh-cn' : 'en';
  }
  const target = [...links].find(link => link.dataset.hasorLocale === choice);
  window.location.replace(target.href + window.location.search + window.location.hash);
</script>
</html>
`;
}

async function cleanStaticHtml(outDir) {
    // The static renderer can emit NUL bytes inside text and URL attributes.
    const files = await readdir(outDir, {recursive: true, withFileTypes: true});
    for (const file of files) {
        if (!file.isFile() || !file.name.endsWith('.html')) continue;
        const filename = path.join(file.parentPath ?? file.path, file.name);
        const html = await readFile(filename, 'utf8');
        if (html.includes('\0')) await writeFile(filename, html.replaceAll('\0', ''));
    }
}

module.exports = function languageEntry(context, {site, mount, mode}) {
    const {i18n} = context;
    const owner = site === 'dataql' ? ['dataql', 'dataway', ...(mode === 'alone' ? [''] : [])] : [site];
    const suffix = mode === 'portal' && site !== 'www' && site !== 'dataql' ? site + '/' : '';
    const entries = owner.map(id => ({
        directory: site === 'dataql' ? id : suffix,
        namespace: site !== 'dataql' ? '' : id ? id + '/' : 'dataql/projects/',
    }));
    const developmentDirectory = path.join(context.generatedFilesDir, 'language-entry');
    const rootSitemaps = site === 'www' && mode === 'portal'
        ? ['www', 'hasor', 'dbvisitor', 'dataql', 'meteor', 'neta', 'cobble'].flatMap(id =>
            [false, true].map(english => id === 'dataql'
                ? siteBaseUrl('www', english) + 'dataql-sitemap.xml'
                : siteBaseUrl(id, english) + 'sitemap.xml'))
        : ['zh-cn', 'en'].map(locale => i18n.localeConfigs[locale].baseUrl +
            (site === 'dataql' ? 'dataql-sitemap.xml' : 'sitemap.xml'));
    return {
        name: 'hasor-language-entry',
        async loadContent() {
            if (process.env.NODE_ENV !== 'development') return;
            for (const {directory, namespace} of entries) {
                const target = context.baseUrl + namespace;
                const output = path.join(developmentDirectory, directory);
                await mkdir(output, {recursive: true});
                await writeFile(path.join(output, 'index.html'), `<!doctype html>
<meta charset="utf-8">
<meta http-equiv="refresh" content="0; url=${target}">
<title>Hasor Tech</title>
<a href="${target}">Hasor Tech</a>
`);
            }
        },
        configureWebpack() {
            if (process.env.NODE_ENV !== 'development') return undefined;
            return {devServer: {static: [{directory: developmentDirectory, publicPath: mount, watch: false}]}};
        },
        async postBuild({outDir, siteConfig}) {
            await cleanStaticHtml(outDir);
            if (i18n.currentLocale !== 'zh-cn') return;
            const localeOutput = siteConfig.baseUrl.slice(mount.length).split('/').filter(Boolean);
            const output = path.resolve(outDir, ...localeOutput.map(() => '..'));
            for (const {directory, namespace} of entries) {
                const destination = path.join(output, directory);
                await mkdir(destination, {recursive: true});
                await writeFile(path.join(destination, 'index.html'), entryHtml(
                    i18n.localeConfigs['zh-cn'].baseUrl + namespace,
                    i18n.localeConfigs.en.baseUrl + namespace));
            }
            if (site === 'www') {
                for (const directory of ['schema', 'rsf']) {
                    await cp(path.join(outDir, directory), path.join(output, directory), {recursive: true});
                }
            }
            if (site === 'www' || mode === 'alone') {
                const sitemaps = rootSitemaps.map(file => 'Sitemap: ' + new URL(file, siteConfig.url).href);
                await writeFile(path.join(output, 'robots.txt'), ['User-agent: *', 'Disallow:', ...sitemaps, ''].join('\n'));
            }
        },
    };
};
