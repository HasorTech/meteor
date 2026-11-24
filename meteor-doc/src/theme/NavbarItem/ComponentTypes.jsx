// Licensed under the Apache License, Version 2.0. See LICENSE.txt.
import React, {createContext, useContext, useEffect, useRef, useState} from 'react';
import Link from '@docusaurus/Link';
import {useLocation} from '@docusaurus/router';
import useBaseUrl, {useBaseUrlUtils} from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import {useHistorySelector} from '@docusaurus/theme-common';
import {useAlternatePageUtils} from '@docusaurus/theme-common/internal';
import componentTypes from '@theme-original/NavbarItem/ComponentTypes';
import DefaultNavbarItem from '@theme/NavbarItem/DefaultNavbarItem';
import DropdownNavbarItem from '@theme/NavbarItem/DropdownNavbarItem';
import NavbarSearch from '@theme/Navbar/Search';
import SearchBar from '@theme/SearchBar';
import IconLanguage from '@theme/Icon/Language';
import GiteeIcon from '@site/static/img/projects/icons/gitee.svg';
import GitHubIcon from '@site/static/img/projects/icons/github.svg';
import projects from '@site/hasor-tech/projects.json';
import {portalUrl, dataqlProjectsPath, siteBaseUrl, languagePreferenceKey, projectHomeUrl, localDocsPath, ecosystemUrl} from '@site/hasor-tech/routes';

export const ArticleProject = createContext(null);

const frameworks = [
    ['hasor', '微内核 Java 开发框架', 'A Java microkernel framework'],
    ['dbvisitor', '统一 API、多种类数据库访问', 'One API for multiple types of databases'],
    ['meteor', '跨语言、跨协议的 RPC 框架', 'An RPC framework that spans languages and protocols'],
    ['cobble', '工具库、涵盖常用工具类', 'A library of commonly used Java utility classes'],
    ['dataway', '配置化 API 发布平台', 'A platform for publishing APIs through configuration'],
    ['dataql', '专为数据查询而生的语言', 'A language built for data queries'],
    ['neta', 'Java AIO 高性能网络开发框架', 'A high-performance networking framework built on Java AIO'],
];

export function useSite() {
    const {i18n, siteConfig} = useDocusaurusContext();
    const {pathname} = useLocation();
    const articleProject = useContext(ArticleProject);
    const route = pathname.slice(siteConfig.baseUrl.length - 1).replace(/\/?$/, '/');
    const site = siteConfig.customFields.ecosystemSite;
    const localProjects = projects.filter(project => project.site === site);
    const sharedHome = site === 'dataql' && route === dataqlProjectsPath;
    const project = articleProject || (site === 'www' ? /^\/blog\/([^/]+)/.exec(route)?.[1]
        : localProjects.length === 1 ? localProjects[0].id : /^\/(dataway|dataql)(?:\/|$)/.exec(route)?.[1]);
    const active = sharedHome ? undefined : projects.find(item => item.id === project);
    const frameworkHome = route === '/' || sharedHome ||
        (site === 'dataql' && active && route === '/' + active.id + '/');
    return {site, active, localProjects, frameworkHome, origins: siteConfig.customFields.ecosystemOrigins,
        english: i18n.currentLocale === 'en'};
}

function blogUrl(project, english, origins) {
    return ecosystemUrl(portalUrl + siteBaseUrl('www', english) + 'blog/' + project + '/', origins);
}

export function usePortalBlogUrl(project) {
    const {english, origins} = useSite();
    return blogUrl(project, english, origins);
}

function Projects({mobile, ...props}) {
    const {site, active, frameworkHome, english, origins} = useSite();
    const sectionLabel = english ? 'Frameworks' : '开源框架';
    const label = site === 'www' ? sectionLabel : active?.name ?? 'Dataway / DataQL';
    const {withBaseUrl} = useBaseUrlUtils();
    const links = frameworks.map(([id, description, englishDescription]) => {
        const project = projects.find(item => item.id === id);
        const destination = project.site === site
            ? {to: site === 'dataql' ? '/' + project.id + '/' : '/'}
            : {href: ecosystemUrl(projectHomeUrl(project, english), origins), target: '_self'};
        const icon = withBaseUrl(project.icon);
        return {id, name: project.name, icon, description: english ? englishDescription : description, destination};
    });
    const [open, setOpen] = useState(false);
    const root = useRef(null);
    const trigger = useRef(null);
    const menu = useRef(null);
    const closeTimer = useRef(null);
    useEffect(() => {
        if (!open) return undefined;
        const close = event => {
            if (!root.current?.contains(event.target)) setOpen(false);
        };
        const dismiss = event => {
            if (event.key !== 'Escape' || event.defaultPrevented) return;
            setOpen(false);
            if (root.current?.contains(document.activeElement)) trigger.current?.focus();
        };
        document.addEventListener('pointerdown', close);
        document.addEventListener('focusin', close);
        document.addEventListener('keydown', dismiss);
        return () => {
            clearTimeout(closeTimer.current);
            document.removeEventListener('pointerdown', close);
            document.removeEventListener('focusin', close);
            document.removeEventListener('keydown', dismiss);
        };
    }, [open]);
    if (mobile) {
        const items = links.map(({name, icon, destination}) => ({
            ...destination,
            label: <><img className="ecosystem-project-mobile-icon" src={icon} alt="" width="20" height="20"/>{name}</>,
        }));
        return <DropdownNavbarItem {...props} mobile label={label} items={items}
            className={frameworkHome ? 'menu__link--active' : undefined} aria-current={frameworkHome || undefined} />;
    }

    return (
        <div ref={root} className="navbar__item ecosystem-project"
            onPointerEnter={event => {
                if (event.pointerType === 'mouse') {
                    clearTimeout(closeTimer.current);
                    setOpen(true);
                }
            }}
            onPointerLeave={event => {
                if (open && event.pointerType === 'mouse' && !menu.current?.contains(document.activeElement)) {
                    closeTimer.current = setTimeout(() => setOpen(false), 160);
                }
            }}>
            <button ref={trigger} type="button"
                className={`navbar__link ecosystem-project-button${frameworkHome ? ' navbar__link--active' : ''}`}
                data-wide-label={(site === 'www' && english) || (site === 'dataql' && !active) || undefined}
                aria-current={frameworkHome || undefined}
                aria-expanded={open} aria-controls="ecosystem-project-menu"
                onClick={event => setOpen(event.detail === 0 ? !open : true)}>
                {label}
            </button>
            <nav ref={menu} id="ecosystem-project-menu" className="ecosystem-project-menu" aria-label={sectionLabel} hidden={!open}>
                <ul className="ecosystem-project-list">
                    {links.map(({id, name, icon, description, destination}) => {
                        const current = id === active?.id;
                        return (
                            <li key={id}>
                                <Link {...destination} onClick={() => setOpen(false)} aria-current={current || undefined}>
                                    <img className="ecosystem-project-icon" src={icon} alt="" width="38" height="38"/>
                                    <span>
                                        <span className="ecosystem-project-name">
                                            {name}
                                            {current && <span className="ecosystem-project-current">{english ? 'Current project' : '当前项目'}</span>}
                                        </span>
                                        <span className="ecosystem-project-description">{description}</span>
                                    </span>
                                </Link>
                            </li>
                        );
                    })}
                </ul>
            </nav>
        </div>
    );
}

function Docs(props) {
    const {site, active, localProjects, english} = useSite();
    const label = english ? 'Technical Docs' : '技术文档';
    if (active) {
        const activeBaseRegex = site === 'dbvisitor' ? '/docs/guides/' : '/docs/(?!releases(?:/|$))';
        return <DefaultNavbarItem {...props} label={label} to={localDocsPath(active)} activeBaseRegex={activeBaseRegex} />;
    }
    const projectDocsLabel = english ? 'Tech Docs' : label;
    return localProjects.map(project => (
        <DefaultNavbarItem key={project.id} {...props} label={project.name + ' ' + projectDocsLabel}
            to={localDocsPath(project)} />
    ));
}

function Blog(props) {
    const {site, english, origins} = useSite();
    const destination = site === 'www' ? {to: '/blog/archive/', activeBasePath: '/blog'}
        : {href: blogUrl('archive', english, origins), target: '_self'};
    return <DefaultNavbarItem {...props} label={english ? 'Blog' : '博客'} className="ecosystem-blog-link" {...destination} />;
}

function ScopedSearch({mobile, className}) {
    const {site, active, localProjects, english} = useSite();
    const icon = useBaseUrl(active?.icon ?? localProjects[0]?.icon ?? '');
    if (mobile) return null;
    if (site === 'www') {
        const SearchNavbarItem = componentTypes.search;
        return <SearchNavbarItem className={className} />;
    }
    const names = localProjects.map(project => project.name).join(' / ');
    const label = english ? `Search ${names}` : `搜索 ${names}`;
    return (
        <NavbarSearch className={className}>
            <div className="ecosystem-scoped-search" role="search" aria-label={label}
                style={{'--ecosystem-search-framework-icon': `url("${icon}")`}}>
                <SearchBar />
            </div>
        </NavbarSearch>
    );
}

function LanguageMenu() {
    const {i18n: {currentLocale, localeConfigs, locales}, siteConfig} = useDocusaurusContext();
    const suffix = useHistorySelector(history => history.location.search + history.location.hash);
    const {createUrl} = useAlternatePageUtils();
    const disclosure = useRef(null);
    const label = currentLocale === 'en' ? 'Change language' : '切换语言';
    useEffect(() => {
        const closeOutside = event => {
            const menu = disclosure.current;
            if (menu?.open && !menu.contains(event.target)) menu.open = false;
        };
        document.addEventListener('pointerdown', closeOutside);
        return () => document.removeEventListener('pointerdown', closeOutside);
    }, []);

    return (
        <details ref={disclosure} className="ecosystem-language"
            onBlur={event => {
                if (!event.currentTarget.contains(event.relatedTarget)) event.currentTarget.open = false;
            }}
            onKeyDown={event => {
                const menu = event.currentTarget;
                if (event.key !== 'Escape' || !menu.open) return;
                event.preventDefault();
                menu.open = false;
                menu.firstElementChild.focus();
            }}>
            <summary className="ecosystem-tool-button" aria-label={label} title={label}>
                <IconLanguage focusable="false" />
            </summary>
            <ul className="ecosystem-language-list" aria-label={label}>
                {locales.map(locale => {
                    const config = localeConfigs[locale];
                    const href = createUrl({locale, fullyQualified: config.url !== siteConfig.url}) + suffix;
                    return (
                        <li key={locale}>
                            <a className="dropdown__link" aria-current={locale === currentLocale ? 'page' : undefined}
                                href={href} onClick={() => {
                                    try { window.localStorage.setItem(languagePreferenceKey, locale); } catch {}
                                }} hrefLang={config.htmlLang} lang={config.htmlLang}>{config.label}</a>
                        </li>
                    );
                })}
            </ul>
        </details>
    );
}

function Tools({repositories, mobile, onClick}) {
    const {i18n} = useDocusaurusContext();
    const sources = [
        {id: 'gitee', label: 'Gitee', href: repositories.gitee, Icon: GiteeIcon},
        {id: 'github', label: 'GitHub', href: repositories.github, Icon: GitHubIcon},
    ].filter(source => source.href);
    const hasLocales = i18n.locales.length > 1;

    if (mobile) {
        return (
            <>
                {sources.map(({id, label, href}) => (
                    <DefaultNavbarItem key={id} mobile href={href} label={label} onClick={onClick} />
                ))}
                {hasLocales && <LanguageMenu />}
            </>
        );
    }

    return (
        <div className="ecosystem-tools">
            {sources.map(({id, label, href, Icon}) => (
                <a key={id} className={`ecosystem-tool-button ecosystem-${id}`} href={href}
                    target="_blank" rel="noopener noreferrer" aria-label={label} title={label}>
                    <Icon width="20" height="20" aria-hidden="true" focusable="false" />
                </a>
            ))}
            {hasLocales && <LanguageMenu />}
        </div>
    );
}

export default {
    ...componentTypes,
    search: ScopedSearch,
    'custom-ecosystemProjects': Projects,
    'custom-ecosystemDocs': Docs,
    'custom-ecosystemBlog': Blog,
    'custom-ecosystemTools': Tools,
};
