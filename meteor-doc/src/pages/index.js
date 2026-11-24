import Community from '@site/hasor-tech/Community';
import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import Translate, {translate} from '@docusaurus/Translate';
import useBaseUrl from '@docusaurus/useBaseUrl';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import HomeArchitecture from '../components/HomeArchitecture';
import LicenseBadge from '@site/static/img/labels/license.svg';
import styles from './index.module.css';

const protocolGroups = [
    {id: 'protobuf', name: <Translate id="home.protocols.protobuf">Protobuf 生态</Translate>, items: [['gRPC', 'grpc'], ['gRPC-Web', 'grpc-web'], ['Connect', 'connect']]},
    {id: 'http', name: <Translate id="home.protocols.http">HTTP 与 WebSocket</Translate>, items: [['JSON-RPC', 'jsonrpc'], ['XML-RPC', 'xmlrpc'], ['SOAP', 'soap'], ['Hessian', 'hessian'], ['Hprose', 'hprose']]},
    {id: 'dubbo', name: <Translate id="home.protocols.dubbo">Dubbo 生态</Translate>, items: [['Dubbo', 'dubbo'], ['Triple', 'triple']]},
    {id: 'other', name: <Translate id="home.protocols.other">其它</Translate>, items: [['Thrift', 'thrift'], ['ONC RPC', 'oncrpc'], ['RSF', 'rsf']]},
];

const languages = [
    ['java.svg', 'Java'],
    ['python.svg', 'Python'],
    ['go-gopher.svg', 'Go', 'Go Gopher by Renee French (CC BY 4.0)'],
    ['nodejs.svg', 'Node.js'],
    ['c.svg', 'C'],
    ['cplusplus.png', 'C++'],
    ['javascript.svg', 'JavaScript'],
    ['terminal.svg', 'Shell'],
];

function getExamples() {
    return [
        {
            value: 'configure',
            label: translate({id: 'home.examples.configure.label', message: '01 配置与启动'}),
            code: `MetConfiguration config = new MetConfiguration();
config.connector("http")
      .bind("127.0.0.1", 8080)
      .protocol("jsonrpc")
      .contextPath("/jsonrpc");
MetContext context = config.buildContext();
context.start();`,
        },
        {
            value: 'publish',
            label: translate({id: 'home.examples.publish.label', message: '02 发布服务'}),
            code: `public interface EchoService {
    String echo(String message);
}

context.publisher()
       .rsfService(EchoService.class, text -> text)
       .protocol("jsonrpc")
       .aliasName("jsonrpc", "echo")
       .register();`,
        },
        {
            value: 'call',
            label: translate({id: 'home.examples.call.label', message: '03 订阅与调用'}),
            code: `MetBindInfo<EchoService> binding = context
    .publisher()
    .rsfService(EchoService.class)
    .protocol("jsonrpc")
    .aliasName("jsonrpc", "echo")
    .register();
InterAddress address = new InterAddress("jsonrpc", "127.0.0.1", 8080);
EchoService echo = context
    .getRsfClient(address)
    .getRemote(binding);
System.out.println(echo.echo("Hello, Meteor!"));`,
        },
    ];
}

function Hero() {
    const {i18n} = useDocusaurusContext();
    const isEnglish = i18n.currentLocale === 'en';
    const illustration = useBaseUrl(`img/home/rpc-${isEnglish ? 'en' : 'zh-cn'}.webp`);
    return <header className={styles.hero}>
        <div className={`container ${styles.heroGrid}`}>
            <div>
                <h1>
                    <span><Translate id="home.hero.slogan">多协议多语言</Translate></span>{' '}
                    <span><Translate id="home.hero.sloganFramework">RPC 服务框架</Translate></span>
                </h1>
                <p className={styles.description}><Translate id="home.hero.description">用熟悉的接口发布与调用服务，通过协议插件连接不同语言与生态。</Translate></p>
                <div className={styles.buttons}>
                    <Link className={styles.primaryButton} to="/docs/guides/start/quick-start"><Translate id="home.hero.quickStart">快速开始</Translate> <span aria-hidden="true">→</span></Link>
                    <Link className={styles.secondaryButton} to="/docs/guides/start/overview"><Translate id="home.hero.overview">认识 Meteor</Translate></Link>
                </div>
            </div>
            <img className={styles.heroIllustration} src={illustration} width={isEnglish ? 1733 : 1732} height={isEnglish ? 907 : 908} fetchpriority="high"
                alt={translate({
                    id: 'home.hero.illustration',
                    message: 'Python 和 Node.js 客户端分别通过 gRPC 和 JSON-RPC 调用 Java 应用发布的服务。Java 应用分别通过 gRPC 和 Thrift 调用 Go 和 Python 服务。两个方向都支持更多语言和协议。',
                })} />
        </div>
    </header>;
}

function LanguageAndProtocolSupport() {
    const iconBase = useBaseUrl('img/languages/');
    const detailsLabel = translate({id: 'home.languages.details', message: '了解详情（语言与协议支持）'});

    return (
        <section className={styles.supportSection} aria-labelledby="meteor-language-support">
            <div className={`container ${styles.supportLayout}`}>
                <header className={styles.supportIntro}>
                    <h2 id="meteor-language-support">
                        <Translate id="home.languages.title">支持多语言、多协议</Translate>
                    </h2>
                    <Link className={styles.supportLink} to="/docs/guides/protocols/about#使用指引" aria-label={detailsLabel}>
                        <Translate id="home.languages.more">了解详情</Translate> <span aria-hidden="true">→</span>
                    </Link>
                </header>
                <div className={styles.supportCatalog}>
                    <ul className={styles.languageList}>
                        {languages.map(([icon, label, credit]) => (
                            <li key={icon}>
                                <img src={`${iconBase}${icon}`} width="52" height="52" alt="" title={credit} />
                                <span>{label}</span>
                            </li>
                        ))}
                    </ul>
                    <div className={styles.protocolGroups}>
                        {protocolGroups.map(group => <div className={styles.protocolGroup} key={group.id}>
                            <h3>{group.name}</h3>
                            <div className={styles.protocolLinks}>
                                {group.items.map(([name, id]) => <Link key={id} to={`/docs/guides/protocols/${id}`}>{name}</Link>)}
                            </div>
                        </div>)}
                    </div>
                </div>
            </div>
        </section>
    );
}

function CodeExamples() {
    const examples = getExamples();
    return <section className={styles.examples} aria-labelledby="meteor-examples-title">
        <div className={`container ${styles.examplesLayout}`}>
            <header className={styles.examplesIntro}>
                <h2 id="meteor-examples-title"><Translate id="home.examples.title">简单的开始</Translate></h2>
                <p><Translate id="home.examples.intro">用 Java 完成一次 JSON-RPC 调用</Translate></p>
                <ul>
                    <li><Translate id="home.examples.start">在 8080 端口启动服务端</Translate></li>
                    <li><Translate id="home.examples.echo">发布 Echo 服务并通过 Java 接口调用</Translate></li>
                </ul>
                <Link className={styles.guideButton} to="/docs/guides/start/quick-start">
                    <Translate id="home.examples.guide">快速上手</Translate>
                </Link>
            </header>
            <div className={styles.exampleTabs}>
                <Tabs>
                    {examples.map(example => <TabItem key={example.value} value={example.value} label={example.label}>
                        <CodeBlock language="java">{example.code}</CodeBlock>
                    </TabItem>)}
                </Tabs>
            </div>
        </div>
    </section>;
}

function Participate() {
    return <section className={styles.participation} aria-labelledby="meteor-participation-title">
        <div className="container">
            <Community>
                <div className={styles.participationHeading}>
                    <h2 id="meteor-participation-title"><Translate id="home.participation.title">加入社区</Translate></h2>
                    <a className={styles.licenseBadge} href="https://www.apache.org/licenses/LICENSE-2.0.html"
                       target="_blank" rel="noopener noreferrer" aria-label="Apache License 2.0">
                        <LicenseBadge aria-hidden="true" focusable="false"/>
                    </a>
                </div>
                <p><Translate id="home.participation.description">交流使用经验、反馈问题，参与 Meteor 的改进。</Translate></p>
            </Community>
        </div>
    </section>;
}

export default function Home() {
    return <Layout
        title={translate({id: 'home.title', message: 'Java 多协议 RPC 框架'})}
        description={translate({id: 'home.description', message: 'Meteor 通过统一的 Java 服务 API 接入 gRPC、Dubbo、Thrift、JSON-RPC 等协议，支持服务注册发现与多语言调用。'})}>
        <main className={styles.home}><Hero /><LanguageAndProtocolSupport /><HomeArchitecture /><CodeExamples /><Participate /></main>
    </Layout>;
}
