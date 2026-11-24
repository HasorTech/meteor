---
title: How Meteor works
description: Learn where Meteor runs and how protocol integration, discovery, calls, and management fit together.
mdx:
  format: mdx
---

import useBaseUrl from '@docusaurus/useBaseUrl';

# How Meteor works

Meteor runs inside the application process. Providers publish Java interfaces and their implementations. Consumers call them through Meteor or a native SDK for the selected protocol. A registry supplies addresses and governance settings, while the console, Telnet, and APIs provide management access.

![Meteor architecture](/img/architecture/working-model.svg)

Solid lines show service calls. Dashed blue lines show discovery and configuration updates, and dashed green lines show management operations. Service requests go directly to providers. <a href={useBaseUrl('/img/architecture/working-model.svg')} target="_blank" rel="noreferrer">Open the full-size diagram</a>.

## Protocol integration {#协议接入}

RPC endpoints receive requests and dispatch them to the appropriate service methods. A single implementation can expose endpoints for multiple protocols.

- **Protobuf**: [gRPC](../protocols/grpc.md), [gRPC-Web](../protocols/grpc-web.md), [Connect](../protocols/connect.md), and [Triple](../protocols/triple.md).
- **HTTP or WebSocket**: [JSON-RPC](../protocols/jsonrpc.md), [XML-RPC](../protocols/xmlrpc.md), [SOAP](../protocols/soap.md), [Hessian](../protocols/hessian.md), and [Hprose](../protocols/hprose.md).
- **TCP or UDP**: [RSF](../protocols/rsf.md), [Dubbo](../protocols/dubbo.md), [Thrift](../protocols/thrift.md), and [ONC RPC](../protocols/oncrpc.md).

The individual guides specify each protocol's transports, call types, and client languages. Connectors manage listeners and outgoing connections. You can configure [port sharing](../services/multiple/ports.md), [path mounting](../services/multiple/http-paths.md), and [secure communication](../services/security/about.md).

## Service discovery {#注册发现}

Providers register services and endpoint addresses. Consumers synchronize those addresses and choose a provider when making a call. The registry also distributes governance settings. When an instance starts, goes offline, or changes address, consumers update their available addresses.

Meteor supports [ZooKeeper](../discovery/zookeeper.md), [Nacos](../discovery/nacos.md), and a [JDBC registry](../discovery/jdbc.md), and can integrate with [Dubbo registries](../discovery/dubbo.md). Native clients use the address configuration or discovery features offered by their own SDK.

Consumers can also use [fixed addresses](../discovery/direct.md) or an [address book](../discovery/address-book.md) without a registry.

## Service calls {#服务调用}

1. The provider configures the endpoint protocol and publishes the service interface and implementation.
2. The consumer subscribes to the interface and gets provider addresses from a fixed address, address book, or registry.
3. The consumer chooses a provider and sends the request over the selected protocol.
4. The endpoint dispatches the request to the service method, runs the implementation, and returns the result.

A native SDK sends requests directly to the provider endpoint. Both sides must agree on method names, arguments, and data formats. Protocols that use separate interface definitions also require the corresponding Proto, Thrift IDL, or WSDL file.

Java clients support synchronous, asynchronous, and callback-based [calls](../services/calling/about.md). Some protocols also support streaming. See [framework configuration](../services/configuration/about.md) for timeouts and [lifecycle](../services/lifecycle/about.md) for filters.

## Service management {#服务管理}

Use the [API](../governance/api.md), [web console](../console/about.md), or [Telnet](../governance/telnet/about.md) to inspect services, change their state, and configure routing or rate limits. The web console can also invoke methods for debugging and display their results.

Local management changes the running application. When using a registry to manage services, the registry persists the settings and applications apply them after synchronization. Enable management endpoints as needed. See [service governance](../governance/about.md) for details.

Follow the [quick start](quick-start.md) to run and call a service.
