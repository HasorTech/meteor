/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See LICENSE.txt or https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.test.scenario;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.example.api.EchoService;
import org.junit.jupiter.api.Test;
import static net.hasor.meteor.test.scenario.ConsoleInvocationExamplesTest.*;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 从 Markdown 提取契约与主代码，用原生 SDK 真实调用选定的提供者。 */
public class ConsoleBinaryExamplesTest {
    @Test
    public void pythonThriftDocumentCallsSelectedProvider() throws Exception {
        boolean docker = Boolean.parseBoolean(System.getenv("METEOR_EXAMPLES_NATIVE_DOCKER"));
        assumeTrue(docker || this.available("thrift") && this.pythonThriftAvailable(),
                "需要已安装的 Python Thrift 和编译器，或显式启用 METEOR_EXAMPLES_NATIVE_DOCKER");

        // 用正式入口发布带编号的真实接口；编译产物和进程日志留在当前模块 build 下。
        Path output = Path.of("build/invocation-examples/native/thrift-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(output);
        var config = configuration(output.resolve("provider-data"), "thrift");
        this.bindForContainer(config, docker);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(output.resolve("console")));
        try (MetContext provider = config.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService())
                    .protocol("thrift").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String selected = this.selectedQuery(api, provider, id, "thrift", docker);
            JsonNode response = api.get("examples/render?" + selected);
            JsonNode document = document(response, "python", "direct");
            saveCode(document, output, "python");
            Files.writeString(output.resolve("service.thrift"), code(document, "thrift"));

            // 从文档中的 IDL 生成官方 Python 绑定，再原样执行同一文档的消费者代码。
            if (docker) {
                this.run(this.container(output, "meteor-test/native:persistent-amd64",
                        "thrift --gen py -out . service.thrift"), output, "compile.log");
                this.run(this.container(output, "meteor-test/python:persistent-amd64",
                        "/opt/meteor-toolchain/python-thrift/bin/python consumer.py"), output, "consumer.log");
            } else {
                this.run(List.of("thrift", "--gen", "py", "-out", ".", "service.thrift"), output, "compile.log");
                this.run(List.of("python3", "consumer.py"), output, "consumer.log");
            }

            // 响应由此提供者生成，不能用语法校验或类型一致替代真实网络证据。
            String result = Files.readString(output.resolve("consumer.log"));
            assertTrue(result.contains("actual:meteor"), result);
            assertTrue(result.contains("result=2"), result);
        }
    }

    @Test
    public void cOncRpcDocumentCallsSelectedProvider() throws Exception {
        boolean docker = Boolean.parseBoolean(System.getenv("METEOR_EXAMPLES_NATIVE_DOCKER"));
        assumeTrue(docker || this.available("rpcgen") && this.available("cc") && this.tirpcAvailable(),
                "需要 rpcgen / C 编译器 / libtirpc，或显式启用 METEOR_EXAMPLES_NATIVE_DOCKER");

        // ONC RPC 的程序、过程及 DTO 字段编号来自正式接口，不另造固定测试契约。
        Path output = Path.of("build/invocation-examples/native/oncrpc-" + UUID.randomUUID()).toAbsolutePath();
        Files.createDirectories(output);
        var config = configuration(output.resolve("provider-data"), "oncrpc");
        this.bindForContainer(config, docker);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(output.resolve("console")));
        try (MetContext provider = config.buildContext(); //
             BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService())
                    .protocol("oncrpc").register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            api.managementToken("test-token");
            String selected = this.selectedQuery(api, provider, id, "oncrpc", docker);
            JsonNode response = api.get("examples/render?" + selected);
            JsonNode document = document(response, "c", "direct");
            Files.writeString(output.resolve("service.x"), code(document, "xdr"));
            Files.writeString(output.resolve("consumer.c"), code(document, "c"));

            // 编译步骤属于测试准备；产品文档只提供契约和调用代码，不显示运行命令。
            String command = "rpcgen -h service.x -o service.h && "
                    + "rpcgen -c service.x -o service_xdr.c && "
                    + "cc -Wall -o consumer consumer.c service_xdr.c $(pkg-config --cflags --libs libtirpc) && "
                    + "./consumer";
            if (docker) {
                List<String> dockerCommand = this.container(output, "meteor-test/native:persistent-amd64", command);
                this.run(dockerCommand, output, "consumer.log");
            } else {
                this.run(List.of("sh", "-c", command), output, "consumer.log");
            }

            // XDR 响应包含提供者处理后的 actual:meteor 文本，确认调用到了真实接口。
            String result = Files.readString(output.resolve("consumer.log"));
            assertTrue(result.contains("response XDR"), result);
            assertTrue(result.contains("61637475616c3a6d6574656f72"), result);
        }
    }

    private void bindForContainer(MetConfiguration config, boolean docker) {
        if (docker) {
            // Docker Desktop 与 WSL 的 loopback 不共享；管理与业务监听均接受真实宿主网卡连接。
            for (MetConfiguration.ConnectorSettings connector : config.getConnectors()) {
                connector.bind("0.0.0.0", 0);
            }
        }
    }

    private String selectedQuery(Api api, MetContext provider, String serviceId, String protocol, boolean docker)
            throws Exception {
        if (!docker) {
            return query(api, provider, serviceId, protocol);
        }
        InterAddress listener = provider.getListenAddress("rsf");
        String host = this.publishHost();
        InterAddress management = new InterAddress(listener.getSchema(), host, listener.getPort(), listener.getPath());
        String scope = "mode=direct&target=" + encode(management.toString());
        scope += "&serviceId=" + encode(serviceId) + "&instanceId=" + provider.getInstanceID() + "&protocol=" + protocol;
        JsonNode available = api.get("examples/targets?" + scope);
        assertTrue(available.path("warnings").isEmpty(), available.toPrettyString());
        assertEquals(1, available.path("targets").size(), available.toPrettyString());
        JsonNode target = available.path("targets").get(0);
        String address = target.path("address").asText();
        assertEquals(host, new InterAddress(address).getHost(), address);
        // 让后端从实际管理目标推导可达业务地址；消费者文件不做任何字符串替换。
        return scope + "&endpointAddress=" + encode(target.path("address").asText()) //
                + "&address=" + encode(target.path("address").asText());
    }

    private String publishHost() throws Exception {
        String configured = System.getenv("METEOR_EXAMPLES_PUBLISH_HOST");
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) {
                continue;
            }
            var addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                    return address.getHostAddress();
                }
            }
        }
        throw new IllegalStateException("请通过 METEOR_EXAMPLES_PUBLISH_HOST 指定容器可访问的宿主地址");
    }

    private List<String> container(Path directory, String image, String command) throws Exception {
        String user = Files.getAttribute(directory, "unix:uid") + ":" + Files.getAttribute(directory, "unix:gid");
        List<String> args = new ArrayList<>();
        // Docker 参数按启动器、生命周期、网络、所有权、挂载、镜像和命令依次传递。
        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add("meteor-console-example-" + UUID.randomUUID());
        args.add("--network");
        args.add("host");
        args.add("--user");
        args.add(user);
        args.add("--volume");
        args.add(directory + ":/example");
        args.add("--workdir");
        args.add("/example");
        args.add(image);
        args.add("sh");
        args.add("-c");
        args.add(command);
        return args;
    }

    private boolean available(String command) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String entry : path.split(System.getProperty("path.separator"))) {
            if (Files.isExecutable(Path.of(entry, command))) {
                return true;
            }
        }
        return false;
    }

    private boolean tirpcAvailable() throws Exception {
        if (!this.available("pkg-config")) {
            return false;
        }
        Process probe = new ProcessBuilder("pkg-config", "--exists", "libtirpc").start();
        try {
            return probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } finally {
            if (probe.isAlive()) {
                probe.destroyForcibly();
                probe.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private boolean pythonThriftAvailable() throws Exception {
        if (!this.available("python3")) {
            return false;
        }
        Process probe = new ProcessBuilder("python3", "-c", "from thrift.protocol import TBinaryProtocol")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            return probe.waitFor(5, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } finally {
            if (probe.isAlive()) {
                probe.destroyForcibly();
                probe.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private void run(List<String> command, Path directory, String logName) throws Exception {
        Path log = directory.resolve(logName);
        Process process = new ProcessBuilder(command).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "示例执行超时：" + command + "；日志：" + log);
            assertEquals(0, process.exitValue(), () -> {
                try {
                    return command + "\n" + Files.readString(log, StandardCharsets.UTF_8);
                } catch (Exception failure) {
                    return command + "；日志读取失败：" + failure;
                }
            });
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(10, TimeUnit.SECONDS);
                int nameOption = command.indexOf("--name");
                if (nameOption >= 0) {
                    String container = command.get(nameOption + 1);
                    Process cleanup = new ProcessBuilder("docker", "rm", "-f", container).start();
                    assertTrue(cleanup.waitFor(10, TimeUnit.SECONDS), "容器清理超时：" + container);
                    assertEquals(0, cleanup.exitValue(), "容器清理失败：" + container);
                }
            }
        }
    }
}
