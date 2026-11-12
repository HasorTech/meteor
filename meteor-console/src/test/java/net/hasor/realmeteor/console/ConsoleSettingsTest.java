/* Copyright 2015-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package net.hasor.realmeteor.console;

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.boot.Boot;
import net.hasor.boot.BootApplication;
import net.hasor.boot.web.WebServer;
import net.hasor.cobble.ClassUtils;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.console.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 经真实 HTTP 验证设置接口，并在裁剪后的独立 JVM 中检查缺包部署。 */
public class ConsoleSettingsTest {
    @TempDir
    public Path directory;

    @Test
    public void settingsRequireLoginAndDescribeTheInstalledConsole() throws Exception {
        // 启动启用认证的控制台，设置接口沿用现有认证边界。
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(configuration(this.directory));
        try (BootApplication server = boot.start()) {
            URI root = URI.create("http://127.0.0.1:" + server.getAppContext().getInstance(WebServer.class).getPort());
            HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
            assertEquals(401, request(client, root, "settings", "GET", null).statusCode());
            assertEquals(200, request(client, root, "auth/login", "POST",
                    Map.of("username", "admin", "password", "settings-test-password")).statusCode());

            // 只返回界面定义和能力，不携带内部部署参数或登录凭据。
            HttpResponse<String> response = request(client, root, "settings", "GET", null);
            assertEquals(200, response.statusCode(), response.body());
            JsonNode settings = JsonUtils.readTree(response.body());
            assertFalse(settings.path("version").asText().isBlank());
            assertEquals("settings-unit", settings.path("localUnit").asText());
            assertTrue(settings.at("/features/debugProtocols").toString().contains("\"rsf\""));
            assertTrue(settings.at("/features/debugProtocols").toString().contains("\"hprose\""));
            assertTrue(database(settings, "mysql").path("available").asBoolean());
            assertTrue(database(settings, "h2").path("available").asBoolean());
            assertTrue(settings.at("/features/registryTypes").isArray());
            JsonNode legacyTypes = JsonUtils.readTree(request(client, root, "endpoint-types", "GET", null).body());
            assertEquals(legacyTypes, settings.path("endpointTypes"));
            assertFalse(response.body().contains("settings-test-password"));
            assertFalse(response.body().contains(this.directory.toString()));
            assertFalse(response.body().contains("securityAccount"));
        }
    }

    @Test
    public void strippedConsoleReportsMissingCapabilitiesAndRejectsTheirOperations() throws Exception {
        // 使用真实裁剪的 classpath，不通过 mock 改写能力结果。
        Set<Path> classpath = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            classpath.add(Path.of(entry).toAbsolutePath());
        }
        for (URL entry : ClassUtils.getClassPath(ConsoleSettingsTest.class.getClassLoader())) {
            if (StringUtils.equals("file", entry.getProtocol())) {
                classpath.add(Path.of(entry.toURI()));
            }
        }
        assertTrue(classpath.removeIf(path -> path.getFileName().toString().startsWith("meteor-protocol-hprose-")));
        assertTrue(classpath.removeIf(path -> path.getFileName().toString().startsWith("mysql-connector-j-")));
        Path log = this.directory.resolve("stripped-console.log");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Djava.io.tmpdir=" + this.directory, //
                "-Duser.home=" + this.directory, "-cp", //
                String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()), //
                ConsoleSettingsTest.class.getName(), this.directory.toString()) //
                .directory(this.directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "裁剪部署未在 60 秒内完成验证");
            assertEquals(0, process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        }
    }

    public static void main(String[] args) throws Exception {
        // 缺少 Hprose 插件和 MySQL 驱动的进程仍可启动并提供认证后的设置接口。
        Path directory = Path.of(args[0]);
        Boot boot = new Boot().sources(ConsoleMain.class).hconfigFile(configuration(directory));
        try (BootApplication server = boot.start()) {
            URI root = URI.create("http://127.0.0.1:" + server.getAppContext().getInstance(WebServer.class).getPort());
            HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
            assertEquals(200, request(client, root, "auth/login", "POST",
                    Map.of("username", "admin", "password", "settings-test-password")).statusCode());
            HttpResponse<String> response = request(client, root, "settings", "GET", null);
            assertEquals(200, response.statusCode(), response.body());
            JsonNode settings = JsonUtils.readTree(response.body());
            assertFalse(settings.at("/features/debugProtocols").toString().contains("\"hprose\""));
            assertTrue(settings.at("/features/debugProtocols").toString().contains("\"rsf\""));
            assertFalse(database(settings, "mysql").path("available").asBoolean());
            assertTrue(database(settings, "mysql").path("reason").asText().contains("MySQL"));
            assertTrue(database(settings, "h2").path("available").asBoolean());
            assertEquals(200, request(client, root, "clusters", "GET", null).statusCode());

            // 直接提交缺包操作也会被拒绝，不进入注册中心查找或尝试数据库连接。
            Map<String, Object> debug = Map.of("protocol", "hprose", "address", "http://127.0.0.1:1", //
                    "method", "echo()", "arguments", List.of());
            HttpResponse<String> rejectedDebug = request(client, root, //
                    "service/debug?mode=registry&clusterId=missing&serviceId=missing&instanceId=missing", "POST", debug);
            assertEquals(409, rejectedDebug.statusCode(), rejectedDebug.body());
            assertTrue(rejectedDebug.body().contains("hprose"));
            HttpResponse<String> rejectedDatabase = request(client, root, "clusters/test", "POST", //
                    Map.of("name", "missing-driver", "type", "jdbc", "database", "mysql", //
                            "address", "jdbc:mysql://127.0.0.1:1/meteor"));
            assertEquals(409, rejectedDatabase.statusCode(), rejectedDatabase.body());
            assertTrue(rejectedDatabase.body().contains("MySQL"));
        }
    }

    private static JsonNode database(JsonNode settings, String key) {
        for (JsonNode database : settings.at("/features/jdbcDatabases")) {
            if (StringUtils.equals(key, database.path("key").asText())) {
                return database;
            }
        }
        throw new AssertionError("缺少数据库定义：" + key);
    }

    private static String configuration(Path directory) throws Exception {
        Path file = directory.resolve("console.properties");
        Files.writeString(file, "hasor.boot.web.connectors.http.host=127.0.0.1\n" //
                + "hasor.boot.web.connectors.http.port=0\nhasor.boot.web.health.enabled=false\n" //
                + "meteor.unit=settings-unit\nsecurity.enable=true\nsecurity.account=admin:settings-test-password\n" //
                + "data.directory=" + directory.toAbsolutePath().toString().replace('\\', '/') + "\n");
        return file.toUri().toString();
    }

    private static HttpResponse<String> request(HttpClient client, URI root, String path, String method, Object body) throws Exception {
        String value = body == null ? "" : JsonUtils.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder(root.resolve("/api/" + path)) //
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json") //
                .method(method, HttpRequest.BodyPublishers.ofString(value)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
