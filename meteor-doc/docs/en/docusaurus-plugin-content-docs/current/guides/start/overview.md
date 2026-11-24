---
title: Introduction
description: Learn how Meteor connects a single Java service implementation to clients using different protocols and languages.
---

# Introduction

<!-- llms:start -->

Meteor is a Java framework for calling RPC services across languages and protocols. A single Java implementation can be published over several RPC protocols for clients written in different languages. Java applications can also use Meteor to call services in other systems.

## What can I use it for? {#能解决什么问题}

- **Serve different clients from one implementation.** Expose a service over several protocols for clients in Java, Python, Go, JavaScript, or C and C++. Language support depends on the protocol.
- **Call existing services.** Connect to a service over its native RPC protocol and invoke it through a Java interface.
- **Run multiple service instances.** Discover addresses through a registry, configure routing and rate limits, and use the console to inspect services and debug methods.

## How to integrate Meteor {#如何接入}

Meteor runs inside your application. Your code implements the service interface, and Meteor handles network communication and remote calls. Use Meteor directly in a Java application or integrate it with Spring, Hasor, or Solon for service publishing and interface injection.

Add protocols, a registry, a web console, or Telnet as needed. You can start with a fixed address and add discovery and service governance later.

## Get started {#开始使用}

- [How Meteor works](concepts.md) explains the architecture, protocol integration, and calls.
- [Quick start](quick-start.md) shows how to run a service and call it from Java or over HTTP.
- [Build an application](../application/overview.md) covers dependencies and framework integrations.
- [Supported protocols](../protocols/about.md) lists client languages and protocol guides.

<!-- llms:end -->
