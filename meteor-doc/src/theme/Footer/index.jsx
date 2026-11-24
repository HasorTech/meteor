// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import React, {useEffect, useRef} from 'react';
import Link from '@docusaurus/Link';
import {useLocation} from '@docusaurus/router';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import useBaseUrl, {useBaseUrlUtils} from '@docusaurus/useBaseUrl';
import {useThemeConfig} from '@docusaurus/theme-common';
import FooterCopyright from '@theme/Footer/Copyright';
import FooterLayout from '@theme/Footer/Layout';
import FooterLogo from '@theme/Footer/Logo';
import projects from '@site/hasor-tech/projects.json';
import {ecosystemUrl, portalUrl, projectHomeUrl, siteBaseUrl} from '@site/hasor-tech/routes';

const products = [];
const about = [
    {zh: '关于作者', en: 'The author', to: '/about/'},
    {zh: '友情链接', en: 'Links', to: '/links/'},
];

let analyticsScript;
let lastTrackedPath;

function FooterAnalytics() {
    const {siteConfig} = useDocusaurusContext();
    const {baiduAnalyticsId} = siteConfig.customFields;
    const {pathname} = useLocation();
    const container = useRef(null);

    useEffect(() => {
        if (!baiduAnalyticsId || process.env.NODE_ENV !== 'production') return;

        const queue = (window._hmt ||= []);
        if (!analyticsScript) {
            queue.push(['_setAutoPageview', false]);
            analyticsScript = document.createElement('script');
            analyticsScript.src = `https://hm.baidu.com/hm.js?${baiduAnalyticsId}`;
            analyticsScript.async = true;
        }

        const pagePath = window.location.pathname;
        if (pagePath !== lastTrackedPath) {
            lastTrackedPath = pagePath;
            queue.push(['_trackPageview', pagePath]);
        }

        if (analyticsScript.parentNode !== container.current) {
            container.current.append(analyticsScript);
        }
    }, [baiduAnalyticsId, pathname]);

    return baiduAnalyticsId ? <div id="analyticsDiv" ref={container} hidden /> : null;
}

function FooterNavigation() {
    const {i18n, siteConfig} = useDocusaurusContext();
    const {withBaseUrl} = useBaseUrlUtils();
    const {ecosystemSite, ecosystemOrigins} = siteConfig.customFields;
    const english = i18n.currentLocale === 'en';
    const language = english ? 'en' : 'zh';
    const portalPath = to => ecosystemSite === 'www' ? {to}
        : {href: ecosystemUrl(portalUrl + siteBaseUrl('www', english) + to.slice(1), ecosystemOrigins)};
    const sections = [
        {id: 'products', title: english ? 'Products' : '产品', links: products},
        {id: 'projects', title: english ? 'Frameworks' : '开源框架', links: projects.map(project => ({
            label: project.name,
            icon: withBaseUrl(project.icon),
            ...(project.site === ecosystemSite
                ? {to: ecosystemSite === 'dataql' ? '/' + project.id + '/' : '/'}
                : {href: ecosystemUrl(projectHomeUrl(project, english), ecosystemOrigins)}),
        }))},
        {id: 'about', title: english ? 'About' : '关于', links: about.map(item => ({
            label: item[language], ...portalPath(item.to),
        }))},
    ];

    return (
        <nav className="ecosystem-footer-grid" aria-label={english ? 'Footer navigation' : '页脚导航'}>
            {sections.map(({id, title, links}) => (
                <section className={`ecosystem-footer-group ecosystem-footer-${id}`} key={id}>
                    <h2 className="footer__title">{title}</h2>
                    {links.length === 0 ? <p className="ecosystem-footer-empty">
                        {english ? 'Coming soon…' : '孵化中...'}
                    </p> : (
                        <ul className="footer__items clean-list">
                            {links.map(({label, icon, ...destination}) => (
                                <li key={label} className="footer__item">
                                    <Link className="footer__link-item" target="_self" {...destination}>
                                        {icon && <img src={icon} width="28" height="28" alt="" loading="lazy" />}
                                        {label}
                                    </Link>
                                </li>
                            ))}
                        </ul>
                    )}
                </section>
            ))}
        </nav>
    );
}

export default function Footer() {
    const {footer} = useThemeConfig();
    const filingIcon = useBaseUrl('/img/projects/icons/beian.png');
    if (!footer) return null;
    const {copyright, logo, style} = footer;
    const publicSecurityRecord = '33011002013536';
    const legalNotice = `<div class="ecosystem-footer-filings">
    <a target="_blank" rel="noopener noreferrer" href="http://www.beian.gov.cn/portal/registerSystemInfo?recordcode=${publicSecurityRecord}">
        <img src="${filingIcon}" alt="">浙公网安备 ${publicSecurityRecord}号
    </a>
    <a target="_blank" rel="noopener noreferrer" href="https://beian.miit.gov.cn/#/Integrated/index">浙ICP备18034797号-1</a>
</div>
${copyright ? `<div class="ecosystem-footer-attribution">${copyright}</div>` : ''}`;

    return <FooterLayout style={style}
        links={<FooterNavigation />}
        logo={logo && <FooterLogo logo={logo} />}
        copyright={<>
            <FooterCopyright copyright={legalNotice} />
            <FooterAnalytics />
        </>} />;
}
