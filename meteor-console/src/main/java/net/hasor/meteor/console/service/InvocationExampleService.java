/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.console.service;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.type.TypeReference;
import net.hasor.cobble.StringUtils;
import net.hasor.core.Inject;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTarget;
import net.hasor.meteor.console.component.commands.dto.service.ExampleTargetsResult;
import net.hasor.meteor.console.component.examples.ExampleDocument;
import net.hasor.meteor.console.component.examples.ExampleRegistry;
import net.hasor.meteor.console.component.examples.java.JavaExampleGenerator;
import net.hasor.meteor.console.component.examples.java.NativeJavaExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.BinaryExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.BinaryJavaExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.HttpExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.HttpRequestExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.ProtobufExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.WireExampleGenerator;
import net.hasor.meteor.console.component.examples.nativeclient.DubboExampleGenerator;
import net.hasor.meteor.console.dao.ClusterDao;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.model.fo.ManagementTarget;
import net.hasor.meteor.console.model.vo.InvocationExamples;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.admin.RegistryInfo;

/** 查询真实服务快照，再用类路径模板生成调用文档；不建立额外注册中心连接。 */
public final class InvocationExampleService {
    private static final List<Map<String, Object>> LANGUAGES = loadLanguages();
    @Inject
    private GovernanceService governance;
    @Inject
    private ClusterDao clusters;

    /** 已验证的语言与协议能力；不读取注册中心或生成示例正文。 */
    public List<Map<String, Object>> languages() {
        return LANGUAGES;
    }

    public InvocationExamples render(ManagementTarget target, String protocol, String endpointAddress, String contextPath, String address) {
        target.requireServiceId();
        target.requireInstanceId();
        ExampleTargetsResult available = this.governance.exampleTargets(target, protocol, endpointAddress, contextPath);
        List<String> warnings = new ArrayList<>(available.getWarnings());
        if (available.getTargets().size() != 1) {
            throw new IllegalArgumentException("请选择一个仍在线的服务提供端点");
        }
        ExampleTarget selected = available.getTargets().get(0);
        ExampleRegistry registry = this.registry(target, available.getRegistry());
        List<ExampleDocument> documents = new ArrayList<>();
        try {
            documents.addAll(new JavaExampleGenerator().generate(selected, registry, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("Meteor 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new NativeJavaExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("Java RPC SDK 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new HttpExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("原生 HTTP 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new HttpRequestExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("HTTP 请求示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new ProtobufExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("Protobuf SDK 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new BinaryJavaExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("Java 二进制 SDK 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new BinaryExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("原生二进制协议示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new DubboExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("Dubbo SDK 示例：" + unsupported.getMessage());
        }
        try {
            documents.addAll(new WireExampleGenerator().generate(selected, address));
        } catch (IllegalArgumentException unsupported) {
            warnings.add("线上报文示例：" + unsupported.getMessage());
        }
        Set<String> testedLanguages = new HashSet<>();
        for (Map<String, Object> language : LANGUAGES) {
            if (language.get("protocols") instanceof List<?> protocols && protocols.contains(protocol)) {
                testedLanguages.add((String) language.get("id"));
            }
        }
        Set<String> documentKeys = new HashSet<>();
        documents.removeIf(document -> !testedLanguages.contains(document.getLanguage())
                || !documentKeys.add(document.getLanguage() + ":" + document.getRole()));
        return new InvocationExamples(LANGUAGES, documents, warnings);
    }

    private static List<Map<String, Object>> loadLanguages() {
        try (InputStream input = InvocationExampleService.class.getResourceAsStream("/META-INF/invocation-examples/languages.json")) {
            if (input == null) {
                throw new IllegalStateException("缺少示例语言能力配置");
            }
            List<Map<String, Object>> languages = JsonUtils.readValue(input.readAllBytes(), new TypeReference<>() { });
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> language : languages) {
                result.add(Map.of("id", language.get("id"), "label", language.get("label"),
                        "protocols", List.copyOf((List<?>) language.get("protocols"))));
            }
            return List.copyOf(result);
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取示例语言能力配置", failure);
        }
    }

    private ExampleRegistry registry(ManagementTarget target, RegistryInfo info) {
        ExampleRegistry registry = new ExampleRegistry();
        if (!target.isDirect()) {
            ClusterProfile profile = this.clusters.get(target.requireClusterId());
            if (!StringUtils.equals(profile.getType(), "remote")) {
                registry.setType(profile.getType());
                registry.setAddress(profile.getAddress());
                registry.setDatabase(profile.getDatabase());
                registry.setUsername(profile.getUsername());
                registry.setGroup(profile.getGroup());
                registry.setNamespace(profile.getNamespace());
                registry.setRoot(profile.getRoot());
                registry.setCredentialsRequired(StringUtils.isNotEmpty(profile.getPassword()));
                // H2 内存库与嵌入式文件库不能作为独立进程的通用注册发现地址。
                if (StringUtils.equals(profile.getDatabase(), "h2")
                        && !profile.getAddress().startsWith("jdbc:h2:tcp:") && !profile.getAddress().startsWith("jdbc:h2:ssl:")) {
                    return null;
                }
                return registry;
            }
        }
        if (info == null || !List.of("nacos", "zookeeper").contains(info.getProvider())) {
            return null;
        }
        Map<String, String> scope = info.getScope();
        if (StringUtils.isBlank(scope.get("address"))) {
            return null;
        }
        registry.setType(info.getProvider());
        registry.setAddress(scope.get("address"));
        registry.setGroup(scope.get("group"));
        registry.setNamespace(scope.get("namespace"));
        registry.setRoot(scope.get("root"));
        return registry;
    }
}
