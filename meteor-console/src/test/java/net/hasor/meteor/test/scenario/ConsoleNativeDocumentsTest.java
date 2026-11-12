/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.meteor.test.scenario;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.example.api.EchoService;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static net.hasor.meteor.test.scenario.ConsoleInvocationExamplesTest.*;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.Api;
import static net.hasor.meteor.test.scenario.ConsoleTestSupport.console;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 从真实 Web 入口获取文档并执行其中的主代码，验证示例生成而非协议实现。
 * 显式设置 METEOR_EXAMPLES_NATIVE_MATRIX=true 后运行；依赖清单位于 invocation-runtimes 测试资源。
 * Python/Node 运行图分别准备在 build/invocation-python、build/invocation-node，Go/C 使用既有语言工具链镜像。
 */
public class ConsoleNativeDocumentsTest {
    @TempDir
    Path directory;

    @ParameterizedTest(name = "{0} 语言能力对应真实文档及可执行调用")
    @ValueSource(strings = { "rsf", "dubbo", "hprose", "hessian", "jsonrpc", "xmlrpc", "soap", "grpc", "grpc-web", "connect", "triple", "thrift", "thrift-http", "oncrpc", "oncrpc-udp" })
    @EnabledIfEnvironmentVariable(named = "METEOR_EXAMPLES_NATIVE_MATRIX", matches = "true")
    void documentsFollowTheActualPublishedContract(String protocol) throws Exception {
        // 发布真实服务，启动控制台，保持业务提供者与示例客户端的运行图隔离。
        var configuration = configuration(this.directory.resolve("provider"), protocol);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(console(this.directory));
        try (MetContext provider = configuration.buildContext(); BootApplication server = boot.start()) {
            String id = provider.publisher().rsfService(EchoService.class, ConsoleInvocationExamplesTest.echoService()).protocol(protocol).register().getBindID();
            provider.start();
            Api api = new Api(server);
            api.login();
            // 从正式接口取得最终文档，按语言能力检查并执行实际展示的代码。
            JsonNode result = api.get("examples/render?" + query(api, provider, id, protocol));
            Path output = Path.of("build/invocation-documents", protocol).toAbsolutePath();
            Files.createDirectories(output);
            Files.writeString(output.resolve("rendered.json"), result.toPrettyString());
            for (JsonNode language : result.path("languages")) {
                boolean supported = false;
                for (JsonNode supportedProtocol : language.path("protocols")) {
                    supported |= StringUtils.equals(protocol, supportedProtocol.asText());
                }
                if (!supported) {
                    continue;
                }
                String name = language.path("id").asText();
                JsonNode document = document(result, name, "direct");
                Files.writeString(output.resolve(name + ".md"), document.path("markdown").asText());
                if (StringUtils.equals(name, "java") || StringUtils.equals(name, "java-sdk") || StringUtils.equals(name, "browser")) {
                    continue; // Java 独立 SDK 用例与浏览器用例分别执行，不混合类路径。
                }
                String source = extract(document, name);
                Path work = this.directory.resolve(name);
                Files.createDirectories(work);
                String filename = switch (name) {
                    case "python" -> "consumer.py";
                    case "javascript" -> "consumer.mjs";
                    case "shell" -> "consumer.sh";
                    case "go" -> "main.go";
                    case "cpp" -> "consumer.cpp";
                    default -> "consumer.c";
                };
                Files.writeString(work.resolve(filename), source);
                List<String> command;
                if (StringUtils.equals(name, "go")) {
                    // Go 工具链由既有语言 Docker 镜像提供，和 NXN 对端一致。
                    Files.createDirectories(Path.of("build/invocation-go-cache"));
                    Files.createDirectories(Path.of("build/invocation-go-modules"));
                    String modules = "";
                    if (StringUtils.equals(protocol, "dubbo")) {
                        modules = "require dubbo.apache.org/dubbo-go/v3 v3.3.2\n";
                    } else if (StringUtils.equals(protocol, "grpc")) {
                        modules = "require (\n google.golang.org/grpc v1.76.0\n google.golang.org/protobuf v1.36.10\n)\n";
                    }
                    Files.writeString(work.resolve("go.mod"), "module console-example\n\ngo 1.25\n" + modules);
                    command = new ArrayList<>(List.of("docker", "run", "--rm", "--network=host", "--user", Files.getAttribute(work, "unix:uid").toString(), "-v", work + ":/work", "-w", "/work", "-v", Path.of("build/invocation-go-cache").toAbsolutePath() + ":/cache", "-v", Path.of("build/invocation-go-modules").toAbsolutePath() + ":/modules", "-e", "GOCACHE=/cache", "-e", "GOPATH=/work/.go", "-e", "GOMODCACHE=/modules", "-e", "GOFLAGS=-mod=mod -modcacherw", "--entrypoint", "go", "meteor-test/go:persistent-amd64", "build", "-o", "consumer", "main.go"));
                    execute(work, command);
                    command = List.of(work.resolve("consumer").toString());
                } else if (StringUtils.equals(name, "javascript")) {
                    if (StringUtils.equals(protocol, "dubbo")) {
                        Files.move(work.resolve(filename), work.resolve("consumer.cjs"));
                        filename = "consumer.cjs";
                    }
                    if (!Files.exists(work.resolve("node_modules"))) {
                        Files.createSymbolicLink(work.resolve("node_modules"), Path.of("build/invocation-node/node_modules").toAbsolutePath());
                    }
                    command = List.of("node", filename);
                } else if (List.of("c", "cpp").contains(name)) {
                    String compiler = "gcc";
                    if (StringUtils.equals(name, "cpp")) {
                        compiler = "g++";
                    }
                    List<String> compile = new ArrayList<>(List.of(compiler, filename, "-o", "consumer"));
                    if (protocol.startsWith("oncrpc")) {
                        // 执行展示文档中的真实 XDR 契约，不借用测试工程生成物。
                        Files.writeString(work.resolve("service.x"), extract(document, "xdr"));
                        execute(work, nativeCommand(work, List.of("rpcgen", "-h", "-o", "service.h", "service.x")));
                        execute(work, nativeCommand(work, List.of("rpcgen", "-c", "-o", "service_xdr.c", "service.x")));
                        execute(work, nativeCommand(work, List.of("gcc", "-I/usr/include/tirpc", "-c", "service_xdr.c", "-o", "service_xdr.o")));
                        compile.addAll(List.of("service_xdr.o", "-I/usr/include/tirpc", "-ltirpc"));
                    }
                    if (source.contains("curl/curl.h")) {
                        compile.add("-lcurl");
                    }
                    execute(work, nativeCommand(work, compile));
                    command = List.of(work.resolve("consumer").toString());
                } else if (StringUtils.equals(name, "python")) {
                    if (List.of("thrift", "thrift-http").contains(protocol)) {
                        Files.writeString(work.resolve("service.thrift"), extract(document, "thrift"));
                        execute(work, nativeCommand(work, List.of("thrift", "--gen", "py", "-out", ".", "service.thrift")));
                    }
                    command = List.of(Path.of("build/invocation-python/bin/python").toAbsolutePath().toString(), filename);
                } else {
                    command = List.of("bash", filename);
                }
                String response = execute(work, command);
                // 文本或二进制响应都必须包含真实提供者返回的标记。
                String hex = java.util.HexFormat.of().formatHex("actual:".getBytes(StandardCharsets.UTF_8));
                assertTrue(response.contains("actual:") || response.replaceAll("\\s+", "").contains(hex), protocol + "/" + name + "\n" + response);
            }
        }
    }

    private static List<String> nativeCommand(Path work, List<String> command) throws Exception {
        List<String> result = new ArrayList<>(List.of("docker", "run", "--rm", "--network=host", "--user", Files.getAttribute(work, "unix:uid").toString(), "-v", work + ":/work", "-w", "/work", "--entrypoint", command.get(0), "meteor-test/native:persistent-amd64"));
        result.addAll(command.subList(1, command.size()));
        return result;
    }

    private static String extract(JsonNode document, String language) {
        String syntax = language;
        if (StringUtils.equals(language, "javascript")) {
            syntax = "javascript";
        } else if (StringUtils.equals(language, "shell")) {
            syntax = "bash";
        }
        var match = Pattern.compile("(?ms)^```" + syntax + "\\s*\\R(.*?)^```").matcher(document.path("markdown").asText());
        if (!match.find()) {
            if (StringUtils.equals(language, "cpp")) {
                return extract(document, "c");
            }
            fail("文档缺少代码块：" + language);
        }
        return match.group(1);
    }

    private static String execute(Path work, List<String> command) throws Exception {
        Path log = work.resolve("process.log");
        Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean done = process.waitFor(90, TimeUnit.SECONDS);
        if (!done) {
            process.destroyForcibly().waitFor();
        }
        String result = Files.readString(log);
        assertTrue(done, command + "\n" + result);
        assertEquals(0, process.exitValue(), command + "\n" + result);
        return result;
    }
}
