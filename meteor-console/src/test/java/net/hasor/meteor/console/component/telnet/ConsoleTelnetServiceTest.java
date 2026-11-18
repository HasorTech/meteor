/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.telnet;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import net.hasor.boot.BootApplication;
import net.hasor.boot.web.WebServer;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.console.ConsoleMain;
import net.hasor.meteor.console.component.rpc.RpcServer;
import net.hasor.meteor.console.dao.ClusterDao;
import net.hasor.meteor.console.global.auth.SessionService;
import net.hasor.meteor.console.model.fo.ClusterProfile;
import net.hasor.meteor.console.utils.JsonUtils;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.meteor.registry.jdbc.JdbcRegistryConfiguration;
import net.hasor.tconsole.client.TelClient;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Telnet 连接经过 Console 公共治理入口操作 H2 注册中心。 */
public class ConsoleTelnetServiceTest {
    @TempDir
    Path directory;

    @MetService(group = "telnet", name = "Echo", version = "1")
    public interface Echo {
        String echo(String text);
    }

    @Test
    public void headlessDoesNotCreateWebServerOrBrowserSession() throws Exception {
        // HTTP 端口已被占用；无头启动仍可成功。
        try (ServerSocket occupied = new ServerSocket(0)) {
            Path config = this.configuration(false, occupied.getLocalPort());
            InetSocketAddress listen;
            try (BootApplication application = ConsoleMain.start("--config=" + config); TelClient client = new TelClient()) {
                assertTrue(application.getAppContext().findBindingBean(WebServer.class).isEmpty());
                assertTrue(application.getAppContext().findBindingBean(SessionService.class).isEmpty());
                listen = application.getAppContext().getInstance(ConsoleTelnetService.class).getListenAddress();
                client.setPassword("telnet-secret");
                client.connectTo(listen, 3000);
                assertTrue(client.isConnected());
                JsonNode status = JsonUtils.readTree(client.sendCommand("status"));
                assertEquals("meteor-console", status.get("application").asText());
                assertFalse(status.get("web").asBoolean());
                assertTrue(client.sendCommand("help").contains("clusters"));
            }
            // 容器关闭后监听端口释放。
            try (ServerSocket rebound = new ServerSocket()) {
                rebound.bind(listen);
            }
        }
    }

    @Test
    public void serviceChangesAffectOnlyExplicitInstance() throws Exception {
        ClusterProfile profile = this.profile("primary");
        MetConfiguration config = this.provider(profile, "provider");
        try (MetContext provider = config.buildContext(); BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); TelClient client = new TelClient()) {
            provider.publisher().rsfService(Echo.class, text -> text).register();
            provider.start();
            application.getAppContext().getInstance(ClusterDao.class).save(profile);
            this.connect(client, application);
            String instance = provider.getInstanceID();
            String id = "[telnet]Echo-1";
            RegistryAdmin admin = provider.getExtension(RegistryAdmin.class);

            // 未明确范围时拒绝写入，不能默认停用整个服务。
            assertTrue(client.sendCommand("service disable '" + id + "' --cluster primary").startsWith("ERROR INVALID_ARGUMENT"));
            assertTrue(admin.getServiceAdmin(id).isEnable());
            assertTrue(client.sendCommand("service disable '" + id + "' --cluster primary --instance " + instance).startsWith("SAVED"));
            assertFalse(admin.getServiceAdmin(id, instance).isEnable());
            assertTrue(admin.getServiceAdmin(id).isEnable());
            assertTrue(client.sendCommand("service enable '" + id + "' --cluster primary --instance " + instance).startsWith("SAVED"));
            assertTrue(admin.getServiceAdmin(id, instance).isEnable());
        }
    }

    @Test
    public void ruleCommandsValidateSaveReadDisableAndClear() throws Exception {
        ClusterProfile profile = this.profile("rules");
        try (MetContext provider = this.provider(profile, "rules-provider").buildContext(); BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); TelClient client = new TelClient()) {
            provider.publisher().rsfService(Echo.class, text -> text).register();
            provider.start();
            application.getAppContext().getInstance(ClusterDao.class).save(profile);
            this.connect(client, application);
            assertTrue(client.sendCommand("clusters use rules").startsWith("OK"));
            String target = " '[telnet]Echo-1' service --scope service";
            String xml = "<route><return value=\"addresses\"/></route>";

            // 校验不写入，保存支持多行输入；切换开关不丢失正文。
            JsonNode validation = JsonUtils.readTree(client.sendCommand("rule validate" + target + " '" + xml + "'"));
            assertTrue(validation.get("valid").asBoolean());
            assertTrue(client.sendCommand("rule put" + target + " --multiline\n" + xml + "\n.end").startsWith("SAVED"));
            JsonNode shown = JsonUtils.readTree(client.sendCommand("rule show" + target));
            assertEquals(xml, shown.get("rule").asText());
            assertTrue(client.sendCommand("rule disable" + target).startsWith("SAVED"));
            assertFalse(JsonUtils.readTree(client.sendCommand("rule show" + target)).get("enabled").asBoolean());
            assertTrue(client.sendCommand("rule enable" + target).startsWith("SAVED"));
            assertTrue(client.sendCommand("rule clear" + target).startsWith("SAVED"));
            RegistryAdmin admin = provider.getExtension(RegistryAdmin.class);
            assertTrue(StringUtils.isBlank(admin.getServiceAdmin("[telnet]Echo-1").fetchRule(MetRuleType.SERVICE_ROUTE)));
        }
    }

    @Test
    public void metadataAndInstanceCommandsUseRegistryApiWithoutHttpSession() throws Exception {
        ClusterProfile profile = this.profile("metadata");
        try (MetContext provider = this.provider(profile, "metadata-provider").buildContext(); BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); TelClient client = new TelClient()) {
            provider.publisher().rsfService(Echo.class, text -> text).register();
            provider.start();
            application.getAppContext().getInstance(ClusterDao.class).save(profile);
            this.connect(client, application);
            String target = " --cluster metadata --scope service --instance " + provider.getInstanceID();
            String service = " '[telnet]Echo-1'";
            RegistryAdmin admin = provider.getExtension(RegistryAdmin.class);

            // JSON 引号和空格完整传递，元信息写入实例服务范围。
            assertTrue(client.sendCommand("meta put" + service + " owner '\"payments team\"'" + target).startsWith("SAVED"));
            assertEquals("payments team", admin.getServiceAdmin("[telnet]Echo-1", provider.getInstanceID()).fetchMeta().get("owner"));
            assertTrue(client.sendCommand("meta list" + service + target).contains("payments team"));
            assertTrue(client.sendCommand("meta remove" + service + " owner" + target).startsWith("SAVED"));
            assertFalse(admin.getServiceAdmin("[telnet]Echo-1", provider.getInstanceID()).fetchMeta().containsKey("owner"));

            // 阻断只写指定实例；Console 不提供远程关闭进程或监听的命令。
            assertTrue(client.sendCommand("instance block " + provider.getInstanceID() + " --cluster metadata").startsWith("SAVED"));
            assertTrue(admin.getInstanceAdmin(provider.getInstanceID()).isBlock());
            assertTrue(client.sendCommand("instance unblock " + provider.getInstanceID() + " --cluster metadata").startsWith("SAVED"));
            assertFalse(admin.getInstanceAdmin(provider.getInstanceID()).isBlock());
            assertTrue(client.sendCommand("instance offline " + provider.getInstanceID() + " --cluster metadata").startsWith("ERROR INVALID_ARGUMENT"));
        }
    }

    @Test
    public void clusterSelectionIsIsolatedAndExplicitOptionDoesNotChangeIt() throws Exception {
        try (BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); TelClient first = new TelClient(); TelClient second = new TelClient()) {
            ClusterDao clusters = application.getAppContext().getInstance(ClusterDao.class);
            clusters.save(this.profile("first"));
            clusters.save(this.profile("second"));
            this.connect(first, application);
            this.connect(second, application);
            assertTrue(first.sendCommand("clusters use first").startsWith("OK"));
            assertTrue(second.sendCommand("services").startsWith("ERROR INVALID_ARGUMENT"));
            assertTrue(first.sendCommand("services --cluster second").contains("items"));
            assertEquals("first", JsonUtils.readTree(first.sendCommand("status")).get("cluster").asText());
            assertTrue(JsonUtils.readTree(second.sendCommand("status")).get("cluster").isNull());
            assertTrue(first.sendCommand("clusters clear").startsWith("OK"));
            assertTrue(first.sendCommand("services").startsWith("ERROR INVALID_ARGUMENT"));
            assertTrue(first.sendCommand("services --cluster first --unknown value").startsWith("ERROR INVALID_ARGUMENT"));
        }
    }

    @Test
    public void wrongPasswordCannotExecuteCommands() throws Exception {
        try (BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); Socket socket = new Socket()) {
            socket.connect(application.getAppContext().getInstance(ConsoleTelnetService.class).getListenAddress());
            socket.setSoTimeout(3000);
            assertEquals("Password:\r\n", this.readUntil(socket, "\n"));
            socket.getOutputStream().write("status\nwrong\nwrong\n".getBytes(StandardCharsets.UTF_8));
            String response = this.readUntil(socket, null);
            assertTrue(response.contains("Too many authentication failures."));
            assertFalse(response.contains("meteor-console"));
        }
    }

    @Test
    public void webAndTelnetCanRunTogether() throws Exception {
        int port;
        try (ServerSocket available = new ServerSocket(0)) {
            port = available.getLocalPort();
        }
        try (BootApplication application = ConsoleMain.start("--config=" + this.configuration(true, port)); TelClient client = new TelClient()) {
            this.connect(client, application);
            assertTrue(JsonUtils.readTree(client.sendCommand("status")).get("web").asBoolean());
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/session")).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
        }
    }

    @Test
    public void noWebAndNoManagementListenerIsRejected() throws Exception {
        Path config = this.configuration(false, 0);
        String content = Files.readString(config).replace("meteor.telnet.enabled=true", "meteor.telnet.enabled=false");
        Files.writeString(config, content);
        assertThrows(IllegalArgumentException.class, () -> ConsoleMain.start("--config=" + config));
    }

    @Test
    public void remoteClusterUsesSavedManagementTokenWithoutBrowserSession() throws Exception {
        ClusterProfile registry = this.profile("source");
        Path remoteConfig = this.configuration(false, 0);
        Files.writeString(remoteConfig, Files.readString(remoteConfig) + "\nmeteor.rpc.enabled=true\nmeteor.rpc.token=remote-management-secret\nmeteor.rpc.port=0\n");
        try (MetContext provider = this.provider(registry, "remote-provider").buildContext(); BootApplication remote = ConsoleMain.start("--config=" + remoteConfig); BootApplication application = ConsoleMain.start("--config=" + this.configuration(false, 0)); TelClient client = new TelClient()) {
            provider.publisher().rsfService(Echo.class, text -> text).register();
            provider.start();
            registry.setId("saved-registry");
            remote.getAppContext().getInstance(ClusterDao.class).save(registry);
            ClusterProfile profile = new ClusterProfile();
            profile.setId("remote");
            profile.setName("remote");
            profile.setType("remote");
            profile.setRemoteClusterId("saved-registry");
            profile.setAddress(remote.getAppContext().getInstance(RpcServer.class).getAddress());
            profile.setManagementToken("remote-management-secret");
            application.getAppContext().getInstance(ClusterDao.class).save(profile);
            this.connect(client, application);

            // Telnet 调用通过 Console 间 RSF 管理连接保存到同一注册中心。
            String reply = client.sendCommand("services --cluster remote");
            assertTrue(reply.contains("[telnet]Echo-1"), reply);
            String saved = client.sendCommand("meta put owner '\"remote-team\"' --scope cluster --cluster remote");
            assertTrue(saved.startsWith("SAVED"), saved);
            RegistryAdmin admin = provider.getExtension(RegistryAdmin.class);
            assertEquals("remote-team", admin.getClusterAdmin().fetchMeta().get("owner"));
            assertNull(application.getAppContext().getBindInfo(SessionService.class));
        }
    }

    @Test
    public void nonLoopbackWithoutPasswordIsRejected() throws Exception {
        Path config = this.configuration(false, 0);
        String content = Files.readString(config).replace("meteor.telnet.auth.password=telnet-secret", "meteor.telnet.host=0.0.0.0");
        Files.writeString(config, content);
        assertThrows(RuntimeException.class, () -> ConsoleMain.start("--config=" + config));
    }

    private Path configuration(boolean web, int httpPort) throws Exception {
        Path root = Files.createDirectories(this.directory.resolve(UUID.randomUUID().toString()));
        Path path = root.resolve("meteor.properties");
        Properties settings = new Properties();
        settings.setProperty("meteor.web.enabled", Boolean.toString(web));
        settings.setProperty("hasor.boot.web.connectors.http.port", Integer.toString(httpPort));
        settings.setProperty("meteor.data.directory", root.resolve("data").toString());
        settings.setProperty("meteor.telnet.enabled", "true");
        settings.setProperty("meteor.telnet.port", "0");
        settings.setProperty("meteor.telnet.auth.password", "telnet-secret");
        try (Writer writer = Files.newBufferedWriter(path)) {
            settings.store(writer, null);
        }
        return path;
    }

    private ClusterProfile profile(String id) throws Exception {
        ClusterProfile profile = new ClusterProfile();
        profile.setId(id);
        profile.setName(id);
        profile.setType("jdbc");
        profile.setDatabase("h2");
        profile.setAddress("jdbc:h2:mem:telnet-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        profile.setUsername("sa");
        profile.setPassword("test-registry-secret");
        JdbcDataSource source = new JdbcDataSource();
        source.setURL(profile.getAddress());
        source.setUser(profile.getUsername());
        source.setPassword(profile.getPassword());
        try (var connection = source.getConnection(); var script = new InputStreamReader(getClass().getResourceAsStream("/META-INF/meteor/schema/h2.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(connection, script);
        }
        return profile;
    }

    private MetConfiguration provider(ClusterProfile profile, String name) {
        MetConfiguration configuration = new MetConfiguration().setApplicationName(name).setDataHome(this.directory.resolve(name));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf");
        JdbcDataSource source = new JdbcDataSource();
        source.setURL(profile.getAddress());
        source.setUser(profile.getUsername());
        source.setPassword(profile.getPassword());
        JdbcRegistryConfiguration registry = new JdbcRegistryConfiguration().setDataSource(source);
        registry.setManagementToken("provider-management-secret");
        configuration.addExtension(registry);
        return configuration;
    }

    private void connect(TelClient client, BootApplication application) {
        client.setPassword("telnet-secret");
        client.connectTo(application.getAppContext().getInstance(ConsoleTelnetService.class).getListenAddress(), 3000);
        assertTrue(client.isConnected());
    }

    private String readUntil(Socket socket, String suffix) throws Exception {
        StringBuilder result = new StringBuilder();
        int value;
        while ((value = socket.getInputStream().read()) >= 0) {
            result.append((char) value);
            if (suffix != null && result.toString().endsWith(suffix)) {
                break;
            }
        }
        return result.toString();
    }
}
