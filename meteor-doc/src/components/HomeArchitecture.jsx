// Licensed under the Apache License, Version 2.0. See LICENSE.txt.

import React from 'react';
import Link from '@docusaurus/Link';
import Translate, {translate} from '@docusaurus/Translate';
import useBaseUrl from '@docusaurus/useBaseUrl';
import styles from './HomeArchitecture.module.css';

const guides = [
    {
        icon: 'box',
        title: <Translate id="home.architecture.app.title">Meteor 应用</Translate>,
        description: <Translate id="home.architecture.app.description">在 Java 应用中集成 Meteor，直接发布多协议服务。</Translate>,
        action: <Translate id="home.architecture.app.action">接入指南</Translate>,
        path: '/docs/guides/start/quick-start',
    },
    {
        icon: 'server',
        title: <Translate id="home.architecture.registry.title">服务发现</Translate>,
        description: <Translate id="home.architecture.registry.description">管理应用与 Sidecar 发布的服务，提供服务登记与发现。</Translate>,
        action: <Translate id="home.architecture.registry.action">服务发现</Translate>,
        path: '/docs/guides/discovery/about',
    },
    {
        icon: 'monitor',
        title: <Translate id="home.architecture.console.title">服务治理</Translate>,
        description: <Translate id="home.architecture.console.description">查询服务、调试调用与管理治理规则。</Translate>,
        action: <Translate id="home.architecture.console.action">治理文档</Translate>,
        path: '/docs/guides/governance/about',
    },
];

const meteorServices = [
    {name: 'A', language: 'Java', icon: 'java.svg', role: <Translate id="home.architecture.role.provider">服务提供方</Translate>, route: 'forward'},
    {name: 'B', language: 'Python', icon: 'python.svg', role: <Translate id="home.architecture.role.sidecarProvider">Sidecar 发布</Translate>},
    {name: 'C', language: 'Go', icon: 'go-gopher.svg', credit: 'Go Gopher by Renee French (CC BY 4.0)', role: <Translate id="home.architecture.role.caller">服务调用方</Translate>, route: 'call'},
];

function ArchitectureIcon({name}) {
    const src = useBaseUrl('/img/architecture/' + name + '.svg');
    return <img className={styles.icon} src={src} alt="" width="32" height="32" />;
}

function LanguageIcon({name, file, credit}) {
    const src = useBaseUrl('/img/languages/' + file);
    return <img src={src} alt={name} title={credit} width="28" height="28" />;
}

function Governance() {
    return <div className={styles.governance}>
        <h3><Translate id="home.architecture.governance">治理侧</Translate></h3>
        <div className={styles.governanceNodes}>
            <div className={styles.governanceNode}>
                <ArchitectureIcon name="server" />
                <div><strong><Translate id="home.architecture.registry.title">服务发现</Translate></strong><span>Nacos · ZooKeeper</span></div>
            </div>
            <div className={styles.governanceLink} aria-hidden="true" />
            <div className={styles.governanceNode}>
                <ArchitectureIcon name="monitor" />
                <div><strong><Translate id="home.architecture.console.title">服务治理</Translate></strong><span><Translate id="home.architecture.console.capabilities">查询 · 调试 · 治理</Translate></span></div>
            </div>
        </div>
    </div>;
}

function DiscoveryLinks() {
    return <div className={styles.discoveryLinks}>
        <svg viewBox="0 0 1000 64" preserveAspectRatio="none" aria-hidden="true">
            <defs>
                <marker id="meteor-discovery-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto">
                    <path d="M0 0 10 5 0 10z" fill="currentColor" />
                </marker>
            </defs>
            <path d="M65 64 V36 H290 V0" markerEnd="url(#meteor-discovery-arrow)" />
            <path d="M360 0 V64" markerEnd="url(#meteor-discovery-arrow)" />
        </svg>
        <span className={styles.readOnly}><Translate id="home.architecture.discovery.readOnly">只读发现</Translate></span>
        <span className={styles.registration}><Translate id="home.architecture.discovery.registration">登记与发现</Translate></span>
    </div>;
}

function GatewayRoute({direction}) {
    return <div className={direction === 'forward' ? styles.forward : styles.call}>
        <span>{direction === 'forward'
            ? <Translate id="home.architecture.forward">跨语言转发</Translate>
            : <Translate id="home.architecture.gatewayCall">调用网关</Translate>}</span>
    </div>;
}

function Cluster() {
    return <div className={styles.cluster}>
        <div className={styles.clusterHeading}>
            <h3><Translate id="home.architecture.cluster">分布式服务集群</Translate></h3>
        </div>
        <div className={styles.services}>
            <svg className={styles.mesh} viewBox="0 0 800 250" preserveAspectRatio="none" aria-hidden="true">
                <defs>
                    <marker id="meteor-rpc-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto-start-reverse">
                        <path d="M0 0 10 5 0 10z" fill="currentColor" />
                    </marker>
                </defs>
                <g markerStart="url(#meteor-rpc-arrow)" markerEnd="url(#meteor-rpc-arrow)">
                    <path d="M344 37.5 H456" />
                    <path d="M170 75 V175" />
                    <path d="M344 212.5 H456" />
                    <path d="M620 75 V175" />
                    <path d="M255 75 500 175" />
                    <path d="M255 175 500 75" />
                </g>
            </svg>
            {meteorServices.map(({name, language, icon, credit, role, route}) => <div className={styles.service} key={name}>
                <LanguageIcon name="" file={icon} credit={credit} />
                <div>
                    <strong><Translate id="home.architecture.meteorService" values={{name}}>{'Meteor 服务 {name}'}</Translate></strong>
                    <span className={styles.serviceRole}>{language} · {role}</span>
                </div>
                {route && <div className={styles.desktopRoute}><GatewayRoute direction={route}/></div>}
            </div>)}
            <div className={styles.sidecar}>
                <strong>Sidecar <span className={styles.serviceRole}><Translate id="home.architecture.role.local">本地服务接入</Translate></span></strong>
                <div className={styles.sidecarLanguages}>
                    <LanguageIcon name="Python" file="python.svg" />
                    <LanguageIcon name="Go" file="go-gopher.svg" credit="Go Gopher by Renee French (CC BY 4.0)" />
                    <LanguageIcon name="Node.js" file="nodejs.svg" />
                </div>
            </div>
        </div>
    </div>;
}

export default function HomeArchitecture() {
    return <section className={styles.architecture} aria-labelledby="meteor-architecture-title">
        <div className="container">
            <header className={styles.heading}>
                <h2 id="meteor-architecture-title"><Translate id="home.architecture.title">业务、网关、治理 应有尽有</Translate></h2>
                <p><Translate id="home.architecture.summary">统一入口接入分布式服务，Java 应用和本地服务共同参与调用。</Translate></p>
            </header>
            <figure className={styles.topology} role="img" aria-label={translate({
                id: 'home.architecture.diagram.label',
                message: 'Meteor 目标架构。Go 服务 C 调用网关，网关将请求跨语言转发到 Java 服务 A。集群节点之间也可以直接调用。Java 可直接发布服务，Python、Go 和 Node.js 可通过 Sidecar 接入。网关从注册中心只读发现服务，Meteor Console 用于服务治理。',
            })}>
                <Governance />
                <DiscoveryLinks />
                <div className={styles.gateway}><ArchitectureIcon name="gateway" /><strong>Meteor <span>Gateway</span></strong></div>
                <div className={styles.mobileRoutes}>
                    <GatewayRoute direction="forward"/>
                    <GatewayRoute direction="call"/>
                </div>
                <Cluster />
            </figure>
            <div className={styles.guides}>
                {guides.map(guide => <article className={styles.guide} key={guide.path}>
                    <h3><ArchitectureIcon name={guide.icon} />{guide.title}</h3>
                    <p>{guide.description}</p>
                    <Link className={styles.guideLink} to={guide.path}>{guide.action}<span aria-hidden="true">→</span></Link>
                </article>)}
            </div>
        </div>
    </section>;
}
