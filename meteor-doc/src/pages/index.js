import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import CodeBlock from '@theme/CodeBlock';
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import styles from './index.module.css';

const protocolGroups = [
    {name: 'Protobuf 生态', items: [['gRPC', 'grpc'], ['gRPC-Web', 'grpc-web'], ['Connect', 'connect'], ['Triple', 'triple']]},
    {name: 'HTTP 与 WebSocket', items: [['JSON-RPC', 'jsonrpc'], ['XML-RPC', 'xmlrpc'], ['SOAP', 'soap'], ['Hessian', 'hessian'], ['Hprose', 'hprose']]},
    {name: 'TCP 与 UDP', items: [['RSF', 'rsf'], ['Dubbo', 'dubbo'], ['Thrift', 'thrift'], ['ONC RPC', 'oncrpc']]},
];

const examples = [
    {
        value: 'configure', label: '01 配置与启动', title: '从一个端口开始',
        description: '通过 MetConfiguration 声明传输和协议。多个 HTTP 协议可以共享监听端口，通过各自的路径分流。',
        link: '/docs/guides/application/java', code: `MetConfiguration configuration = new MetConfiguration();
configuration.setUnitName("local");

var http = configuration.connector("http")
        .bind("127.0.0.1", 8080);
http.protocol("jsonrpc").contextPath("/jsonrpc");
http.protocol("grpc").contextPath("/grpc");

MetContext context = configuration.buildContext();
context.start();
// 应用退出时，由创建者关闭 context。`,
    },
    {
        value: 'publish', label: '02 发布服务', title: '业务接口，保持简单',
        description: '发布普通 Java 接口，按服务选择协议和别名。协议插件负责映射到对应契约，业务实现专注于业务本身。',
        link: '/docs/guides/services/publish', code: `public interface EchoService {
    String echo(String message);
}

// 已启动的 context 中发布 JSON-RPC 服务。
context.publisher()
        .rsfService(EchoService.class, text -> text)
        .protocol("jsonrpc")
        .aliasName("jsonrpc", "echo")
        .register();`,
    },
    {
        value: 'call', label: '03 订阅与调用', title: '像本地接口一样调用',
        description: '消费者声明相同的服务身份，再通过地址获得接口代理。接入注册中心后，可由服务发现提供地址。',
        link: '/docs/guides/services/consume', code: `// 消费者已配置 JSON-RPC 并启动 context。
var binding = context.publisher()
        .rsfService(EchoService.class)
        .protocol("jsonrpc")
        .aliasName("jsonrpc", "echo")
        .register();

var address = new InterAddress("jsonrpc", "127.0.0.1", 8080, "local");
EchoService echo = context.getRsfClient(address).getRemote(binding);
System.out.println(echo.echo("Hello, Meteor!"));`,
    },
];

function Hero() {
    const {siteConfig: {customFields}} = useDocusaurusContext();
    return <header className={styles.hero}>
        <div className="container">
            <div className={styles.eyebrow}>JAVA · MULTI-PROTOCOL RPC</div>
            <h1>Meteor<span className={styles.spark} aria-hidden="true">✦</span></h1>
            <p className={styles.slogan}>一个 Java 接口，连接多种 RPC 协议</p>
            <p className={styles.description}>用熟悉的接口发布与调用服务，通过协议插件连接不同语言与生态。<br/>从点对点调用，到服务发现与运行期管理。</p>
            <div className={styles.buttons}>
                <Link className={styles.primaryButton} to="/docs/guides/start/quick-start">快速开始 <span aria-hidden="true">→</span></Link>
                <Link className={styles.secondaryButton} to="/blog/archive">阅读博客</Link>
                <Link className={styles.sourceLink} to={customFields.repositoryUrl}>浏览源码 ↗</Link>
            </div>
            <div className={styles.release}><span className={styles.statusDot} />{customFields.lastReleaseVer} · {customFields.lastReleaseVer.endsWith('-SNAPSHOT') ? '开发版本' : '正式版本'}<span className={styles.divider}>/</span>Apache License 2.0</div>
        </div>
    </header>;
}

function Protocols() {
    return <section className={styles.protocolSection} aria-labelledby="protocols-title">
        <div className="container">
            <div className={styles.sectionHeading}>
                <div><span className={styles.kicker}>CONNECTED BY DESIGN</span><h2 id="protocols-title">接入你熟悉的协议生态</h2></div>
                <Link to="/docs/guides/protocols/about">查看协议支持 <span aria-hidden="true">↗</span></Link>
            </div>
            <div className={styles.protocolGroups}>
                {protocolGroups.map(group => <div className={styles.protocolGroup} key={group.name}>
                    <h3>{group.name}</h3>
                    <div className={styles.protocolLinks}>{group.items.map(([name, id]) => <Link key={id} to={`/docs/guides/protocols/${id}`}>{name}<span aria-hidden="true">↗</span></Link>)}</div>
                </div>)}
            </div>
            <p className={styles.note}>各协议支持的传输、契约、流式模式和互通边界，详见对应手册。</p>
        </div>
    </section>;
}

function Architecture() {
    return <section className={styles.architecture}>
        <div className={`container ${styles.architectureGrid}`}>
            <div className={styles.architectureCopy}>
                <span className={styles.kicker}>ONE SERVICE, MANY CONNECTIONS</span>
                <h2>业务、协议与传输，<br/>各司其职。</h2>
                <p>以 Java 接口表达服务，用协议插件处理契约和编码，再由连接器管理实际的网络通信。</p>
                <p>同一个业务实现可以选择多个协议发布，接入已有客户端，也为新的调用方保留选择。</p>
                <Link to="/docs/guides/start/concepts">了解服务与调用方式 <span aria-hidden="true">→</span></Link>
            </div>
            <div className={styles.diagram} aria-label="Java 服务经 Meteor 服务发布与调用、协议插件，通过 HTTP、TCP 或 UDP 连接其他客户端">
                <div className={styles.diagramTop}><span className={styles.diagramLabel}>YOUR APPLICATION</span><strong>Java 接口 & 业务实现</strong></div>
                <div className={styles.connectorLine} aria-hidden="true">↓</div>
                <div className={styles.diagramCore}><strong>Meteor</strong><span>服务发布 · 订阅调用 · 地址路由 · 过滤器</span></div>
                <div className={styles.connectorLine} aria-hidden="true">↓</div>
                <div className={styles.diagramProtocols}><span>gRPC / Connect</span><span>JSON-RPC / SOAP</span><span>Dubbo / Thrift / …</span></div>
                <div className={styles.diagramTransport}><span>HTTP</span><span>TCP</span><span>UDP</span></div>
                <div className={styles.diagramBottom}>Java · Python · Go · Node.js · C / C++ · Browser</div>
            </div>
        </div>
    </section>;
}

function CodeExamples() {
    return <section className={styles.examples}>
        <div className="container">
            <div className={styles.centerHeading}><span className={styles.kicker}>A FAMILIAR WAY TO BUILD</span><h2>让第一次 RPC 调用更直接</h2><p>从配置到调用，沿用一套清晰的 Java API。</p></div>
            <div className={styles.exampleTabs}><Tabs>
                {examples.map(example => <TabItem key={example.value} value={example.value} label={example.label}>
                    <div className={styles.exampleGrid}>
                        <div className={styles.exampleCopy}><h3>{example.title}</h3><p>{example.description}</p><Link to={example.link}>阅读使用指南 →</Link></div>
                        <CodeBlock language="java" title="Meteor · Java API">{example.code}</CodeBlock>
                    </div>
                </TabItem>)}
            </Tabs></div>
        </div>
    </section>;
}

function GettingStarted() {
    const {siteConfig: {customFields}} = useDocusaurusContext();
    return <section className={styles.gettingStarted}>
        <div className={`container ${styles.gettingStartedGrid}`}>
            <div><span className={styles.kicker}>START SMALL, CONNECT MORE</span><h2>从一个服务开始</h2><p>按需引入协议模块，跟随可运行的示例完成发布与调用。</p><p className={styles.note}>依赖准备、示例代码和预期结果，详见“快速开始”。</p><Link className={styles.primaryButton} to="/docs/guides/start/quick-start">开始接入 Meteor →</Link></div>
            <CodeBlock language="xml" title="pom.xml · JSON-RPC 示例">{`<dependency>
    <groupId>net.hasor</groupId>
    <artifactId>meteor-protocol-jsonrpc</artifactId>
    <version>${customFields.lastReleaseVer}</version>
</dependency>`}</CodeBlock>
        </div>
    </section>;
}

export default function Home() {
    return <Layout title="Java 多协议 RPC 框架" description="Meteor 通过统一的 Java 服务 API 接入 gRPC、Dubbo、Thrift、JSON-RPC 等协议，支持服务注册发现与多语言调用。">
        <Hero />
        <main><Protocols /><Architecture /><CodeExamples /><GettingStarted />
            <section className={styles.more}><div className="container"><h2>继续探索 Meteor</h2><p>按教程完成服务接入、发布和调用。</p><div className={styles.buttons}><Link to="/blog/archive">阅读博客 ↗</Link><Link to="/docs/guides/discovery/nacos">接入注册中心 ↗</Link></div></div></section>
        </main>
    </Layout>;
}
