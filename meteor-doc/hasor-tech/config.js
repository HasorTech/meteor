// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
const projects = require('./projects.json');
const {baidu} = require('./analytics.json');

const {portalUrl, ecosystemUrl, languagePaths, localeBaseUrl} = require('./routes');
const githubOrganization = 'https://github.com/HasorTech';

module.exports = function ecosystem(config, site) {
    const portal = site === 'www';
    const mode = process.env.ECOSYSTEM_BUILD_MODE || 'portal';
    const mount = config.baseUrl;
    const localeConfigs = Object.fromEntries(config.i18n.locales.map(locale => [
        locale, {path: languagePaths[locale], baseUrl: localeBaseUrl(site, locale, mount, mode)},
    ]));
    const origins = process.env.NODE_ENV === 'development' ? JSON.parse(process.env.ECOSYSTEM_DEV_ORIGINS || '{}') : {};
    const publicLink = href => ecosystemUrl(href, origins);
    const current = projects.filter(project => project.site === site);
    const logo = portal
        ? config.themeConfig.navbar?.logo?.src ?? config.favicon
        : current[0].icon.slice(1).replace(/\.svg$/, '-brand.svg');
    const repositories = {
        gitee: portal ? 'https://gitee.com/hasortech' : current[0].source,
        github: portal ? githubOrganization : `${githubOrganization}/${site}`,
    };
    const releasesPath = site === 'dataql' ? '/dataql/docs/releases/latest' : '/docs/releases/latest';
    const items = [
        {type: 'custom-ecosystemProjects', position: 'left'},
        {type: 'custom-ecosystemBlog', position: 'left'},
    ];
    if (!portal) {
        items.push({type: 'custom-ecosystemDocs', position: 'left'});
        if (current.some(project => project.releases !== false)) {
            items.push({to: releasesPath, label: '发布版本', activeBaseRegex: '/docs/releases/', position: 'left'});
        }
        if (site === 'dbvisitor') {
            items.push(
                {to: '/docs/features/overview', label: '数据源', activeBaseRegex: '/docs/features/', position: 'left'},
                {to: '/docs/drivers/about', label: 'JDBC 驱动', activeBaseRegex: '/docs/drivers/', position: 'left'},
            );
        }
    }
    items.push(
        {type: 'search', position: 'right'},
        {type: 'custom-ecosystemTools', position: 'right', repositories},
    );
    const previous = config.themeConfig.footer || {};
    return {
        ...config,
        url: origins[site] || config.url,
        i18n: {...config.i18n, path: 'docs', localeConfigs},
        plugins: [...(config.plugins ?? []), [require.resolve('./language-entry'), {site, mount, mode}]],
        customFields: {
            ...config.customFields,
            ...(config.customFields?.portalBlogUrl && {portalBlogUrl: publicLink(config.customFields.portalBlogUrl)}),
            ecosystemSite: site,
            ecosystemOrigins: origins,
            baiduAnalyticsId: mode === 'portal'
                ? baidu.portal
                : baidu.alone[site] ?? '',
        },
        themeConfig: {
            ...config.themeConfig,
            navbar: {
                title: 'Hasor Tech',
                logo: {
                    src: logo,
                    alt: '',
                    ...(portal ? {} : {
                        href: publicLink(portalUrl + '/'),
                        target: '_self',
                    }),
                },
                items,
            },
            footer: {
                ...previous,
                style: 'light',
            },
        },
    };
};
