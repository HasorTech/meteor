// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
const {online: onlineOrigins} = require('./origins.json');
const portalUrl = onlineOrigins.www;
const dataqlProjectsPath = '/dataql/projects/';
const projects = require('./projects.json');
const languagePreferenceKey = 'hasor-tech-language';
const languagePaths = {'zh-cn': 'cn', en: 'en'};

function siteBaseUrl(site, english = false) {
    const locale = '/' + languagePaths[english ? 'en' : 'zh-cn'] + '/';
    return site === 'www' ? locale : locale + site + '/';
}

function localeBaseUrl(site, locale, baseUrl, mode) {
    const mount = baseUrl.replace(/\/$/, '');
    const project = mode === 'portal' && site !== 'www' && site !== 'dataql' ? site + '/' : '';
    return mount + '/' + languagePaths[locale] + '/' + project;
}

function projectUrl(project, english = false, docs = project.docs) {
    return projectHomeUrl(project, english) + docs.slice(1);
}

function projectHomeUrl(project, english = false) {
    return onlineOrigins[project.site] + siteBaseUrl(project.id, english);
}

function localDocsPath(project) {
    return project.site === 'dataql' ? '/' + project.id + project.docs : project.docs;
}

function ecosystemUrl(href, origins) {
    if (typeof href !== 'string' || !Object.values(onlineOrigins).some(origin =>
        href === origin || href.startsWith(origin + '/'))) return href;
    const url = new URL(href);
    const [, namespace] = url.pathname.replace(/^\/(?:cn|en)(?=\/)/, '').split('/');
    const site = projects.find(project => project.id === namespace)?.site ?? 'www';
    if (url.origin !== portalUrl && url.origin !== onlineOrigins[site]) return href;
    return (origins[site] || onlineOrigins[site]) + url.pathname + url.search + url.hash;
}

module.exports = {portalUrl, onlineOrigins, dataqlProjectsPath, languagePreferenceKey, languagePaths,
    localeBaseUrl, siteBaseUrl, projectUrl, projectHomeUrl, localDocsPath, ecosystemUrl};
