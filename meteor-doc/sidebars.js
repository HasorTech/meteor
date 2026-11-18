/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
module.exports = {
    "guides": [
        "guides/start/overview",
        "guides/start/concepts",
        "guides/start/quick-start",
        {
            "type": "category",
            "label": "创建应用",
            "link": {
                "type": "doc",
                "id": "guides/application/overview"
            },
            "items": [
                "guides/application/java",
                "guides/application/spring",
                "guides/application/hasor",
                "guides/application/solon"
            ]
        },
        {
            "type": "category",
            "label": "开发手册",
            "link": {
                "type": "doc",
                "id": "guides/services/overview"
            },
            "items": [
                {
                    "type": "category",
                    "label": "发布订阅",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/definition/about"
                    },
                    "items": [
                        "guides/services/contracts",
                        "guides/services/publish",
                        "guides/services/consume"
                    ]
                },
                {
                    "type": "category",
                    "label": "调用方式",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/calling/about"
                    },
                    "items": [
                        "guides/services/invocation",
                        "guides/services/dynamic",
                        "guides/services/async",
                        "guides/services/callbacks",
                        "guides/services/streaming",
                        "guides/services/messages",
                        "guides/services/options"
                    ]
                },
                {
                    "type": "category",
                    "label": "多协议发布",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/multiple/about"
                    },
                    "items": [
                        "guides/services/multiple/ports",
                        "guides/services/multiple/http-paths"
                    ],
                    "className": "meteor-services-multiple"
                },
                {
                    "type": "category",
                    "label": "服务发现",
                    "link": {
                        "type": "doc",
                        "id": "guides/discovery/about"
                    },
                    "items": [
                        "guides/discovery/direct",
                        "guides/discovery/address-book",
                        "guides/discovery/nacos",
                        "guides/discovery/zookeeper",
                        "guides/discovery/jdbc",
                        "guides/discovery/dubbo"
                    ]
                },
                {
                    "type": "category",
                    "label": "生命周期",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/lifecycle/about"
                    },
                    "items": [
                        "guides/services/lifecycle/online",
                        "guides/services/lifecycle/listeners",
                        "guides/services/filters"
                    ]
                },
                {
                    "type": "category",
                    "label": "框架配置",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/configuration/about"
                    },
                    "items": [
                        "guides/services/timeouts",
                        "guides/services/configuration/network",
                        "guides/services/configuration/resources",
                        "guides/services/configuration/connections",
                        "guides/services/configuration/http2-window",
                        "guides/services/configuration/cache",
                        "guides/services/configuration/data-home"
                    ]
                },
                {
                    "type": "category",
                    "label": "安全通信",
                    "link": {
                        "type": "doc",
                        "id": "guides/services/security/about"
                    },
                    "items": [
                        "guides/services/security/certificates",
                        "guides/services/security/tls",
                        "guides/services/security/https",
                        "guides/services/security/dtls",
                        "guides/services/security/server-auth",
                        "guides/services/security/mutual-tls",
                        "guides/services/security/management",
                        "guides/services/security/reverse-proxy"
                    ]
                }
            ]
        },
        {
            "type": "category",
            "label": "协议支持",
            "link": {
                "type": "doc",
                "id": "guides/protocols/about"
            },
            "items": [
                "guides/protocols/rsf",
                "guides/protocols/grpc",
                "guides/protocols/grpc-web",
                "guides/protocols/connect",
                "guides/protocols/triple",
                "guides/protocols/dubbo",
                "guides/protocols/thrift",
                "guides/protocols/jsonrpc",
                "guides/protocols/xmlrpc",
                "guides/protocols/soap",
                "guides/protocols/hessian",
                "guides/protocols/hprose",
                "guides/protocols/oncrpc"
            ]
        },
        {
            "type": "category",
            "label": "服务治理",
            "link": {
                "type": "doc",
                "id": "guides/governance/about"
            },
            "items": [
                {
                    "type": "category",
                    "label": "治理能力",
                    "link": {
                        "type": "doc",
                        "id": "guides/governance/capabilities/about"
                    },
                    "items": [
                        "guides/governance/routing",
                        "guides/governance/method-routing",
                        "guides/governance/args-routing",
                        "guides/governance/local",
                        "guides/governance/units",
                        "guides/governance/limits",
                        "guides/governance/enabled",
                        "guides/governance/metadata",
                        "guides/governance/rule-syntax"
                    ]
                },
                {
                    "type": "category",
                    "label": "使用 API",
                    "link": {
                        "type": "doc",
                        "id": "guides/governance/api"
                    },
                    "items": [
                        "guides/governance/api/local",
                        "guides/governance/api/registry",
                        "guides/governance/api/direct",
                        "guides/governance/api/state",
                        "guides/governance/api/metadata"
                    ]
                },
                {
                    "type": "category",
                    "label": "使用控制台",
                    "link": {
                        "type": "doc",
                        "id": "guides/console/about"
                    },
                    "items": [
                        "guides/console/web",
                        "guides/console/headless",
                        "guides/console/remote"
                    ]
                },
                {
                    "type": "category",
                    "label": "使用 Telnet",
                    "link": {
                        "type": "doc",
                        "id": "guides/governance/telnet/about"
                    },
                    "items": [
                        "guides/governance/telnet/connect",
                        "guides/governance/telnet/status",
                        "guides/governance/telnet/clusters",
                        "guides/governance/telnet/applications",
                        "guides/governance/telnet/services",
                        "guides/governance/telnet/instances",
                        "guides/governance/telnet/service",
                        "guides/governance/telnet/instance",
                        "guides/governance/telnet/addresses",
                        "guides/governance/telnet/rule",
                        "guides/governance/telnet/meta"
                    ]
                }
            ]
        },
        {
            "type": "category",
            "label": "框架扩展",
            "link": {
                "type": "doc",
                "id": "guides/extensions/about"
            },
            "items": [
                "guides/extensions/architecture",
                "guides/extensions/lifecycle",
                "guides/extensions/json",
                "guides/extensions/serialization",
                "guides/extensions/protocol",
                "guides/extensions/pipeline",
                "guides/extensions/transport",
                "guides/extensions/registry"
            ]
        }
    ],
    "releases": [{"type": "autogenerated", "dirName": "releases"}]
};
