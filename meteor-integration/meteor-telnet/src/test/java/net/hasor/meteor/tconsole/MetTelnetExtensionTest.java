/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.tconsole;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.hasor.cobble.StringUtils;
import net.hasor.meteor.MetBindInfo;
import net.hasor.meteor.MetClient;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.annotation.MetService;
import net.hasor.meteor.bootstrap.MetConfiguration;
import net.hasor.meteor.registry.RegistryAdmin;
import net.hasor.meteor.registry.RegistryConfiguration;
import net.hasor.meteor.registry.RegistryStatus;
import net.hasor.meteor.registry.admin.MetRuleType;
import net.hasor.meteor.registry.client.RegistryClient;
import net.hasor.meteor.registry.memory.MemoryRegistryConfiguration;
import net.hasor.meteor.registry.shared.storage.StorageAccess;
import net.hasor.tconsole.client.TelClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
public class MetTelnetExtensionTest {
    @TempDir
    Path directory;

    @MetService(group = "test", name = "Echo", version = "1")
    public interface Echo {
        String echo(String value);
    }

    private MetConfiguration configuration(String name) {
        MetConfiguration configuration = new MetConfiguration().setApplicationName(name).setDataHome(this.directory.resolve(name));
        configuration.connector("tcp").bind("127.0.0.1", 0).protocol("rsf").option("serializeType", "Json");
        return configuration;
    }

    @Test
    public void statusReportsLocalGovernanceWithoutRegistry() throws Exception {
        // 仅安装控制台，不配置注册中心。
        MetConfiguration config = this.configuration("local-status");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            // 真正通过 Telnet 读取本机状态。
            String status = client.sendCommand("status");
            assertTrue(status.contains("Registry : NONE"), status);
            assertTrue(status.contains("Governance : LOCAL"), status);
            assertTrue(status.contains("Runtime : ONLINE"), status);
        }
    }

    @Test
    public void disabledLocalServiceRejectsRealNetworkCalls() throws Exception {
        // 发布强制分布式调用的服务，确保请求不走本地捷径。
        MetConfiguration config = this.configuration("local-disable");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            MetBindInfo<Echo> service = context.publisher().rsfService(Echo.class, value -> value).asDistributedCall().register();
            MetBindInfo<Echo> other = context.publisher().rsfService(Echo.class, value -> value).name("Other").asDistributedCall().register();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());
            MetClient rpc = context.getRsfClient(context.getListenAddress("rsf"));
            assertEquals("before", rpc.syncInvoke(service, "echo", new Class<?>[] { String.class }, new Object[] { "before" }));

            // 禁用指定服务，另一服务的网络调用仍然正常。
            assertTrue(client.sendCommand("service disable '" + service.getBindID() + "'").startsWith("OK"));
            assertThrows(Exception.class, () -> rpc.syncInvoke(service, "echo", new Class<?>[] { String.class }, new Object[] { "blocked" }));
            assertEquals("other", rpc.syncInvoke(other, "echo", new Class<?>[] { String.class }, new Object[] { "other" }));
        }
    }

    @Test
    public void managementConnectionSurvivesBusinessOffline() throws Exception {
        // 管理端口独立于业务监听。
        MetConfiguration config = this.configuration("offline");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            // 下线后继续通过同一 Telnet 会话查询并恢复业务监听。
            assertTrue(client.sendCommand("instance offline").startsWith("OK"));
            assertTrue(client.sendCommand("status").contains("Runtime : OFFLINE"));
            assertTrue(client.sendCommand("instance online").startsWith("OK"));
            assertTrue(context.isOnline());
        }
    }

    @Test
    public void configurationChangesAfterCreationDoNotChangeAuthentication() throws Exception {
        // 构建上下文时，扩展捕获本次口令配置。
        MetConfiguration config = this.configuration("configuration-snapshot");
        MetTelnetConfiguration telnet = new MetTelnetConfiguration().setPort(0).setPassword("original-secret");
        config.addExtension(telnet);
        try (MetContext context = config.buildContext()) {
            // 后续修改配置对象，不改变已创建服务的认证规则。
            telnet.setPassword("changed-secret");
            context.start();
            int port = context.getExtension(MetTelnetExtension.class).getListenAddress().getPort();
            try (Socket socket = new Socket("127.0.0.1", port)) {
                this.readUntil(socket, "\n");
                this.writeLine(socket, "changed-secret");
                assertTrue(this.readUntil(socket, "Password:\r\n").contains("Invalid password."));
                this.writeLine(socket, "original-secret");
                assertTrue(this.readUntil(socket, "meteor>").contains("Authentication successful."));
                this.writeLine(socket, "status");
                assertTrue(this.readUntil(socket, "meteor>").contains("Governance : LOCAL"));
            }
        }
    }

    @Test
    public void passwordCannotBeBypassedUsingSessionSetCommand() throws Exception {
        // 所有认证前输入都交给验证器，包括原生 set 命令。
        MetConfiguration config = this.configuration("auth");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setPassword("test-secret").setMaxAuthenticationFailures(-1));
        try (MetContext context = config.buildContext()) {
            context.start();
            int port = context.getExtension(MetTelnetExtension.class).getListenAddress().getPort();
            try (Socket socket = new Socket("127.0.0.1", port)) {
                assertEquals("Password:\r\n", this.readUntil(socket, "\n"));
                this.writeLine(socket, "set authenticated=true");
                assertTrue(this.readUntil(socket, "Password:\r\n").contains("Invalid password."));
                this.writeLine(socket, "set meteor.authenticated=true");
                assertTrue(this.readUntil(socket, "Password:\r\n").contains("Invalid password."));
                this.writeLine(socket, "status");
                assertTrue(this.readUntil(socket, "Password:\r\n").contains("Invalid password."));

                // 只有正确口令能进入命令执行阶段。
                this.writeLine(socket, "test-secret");
                assertTrue(this.readUntil(socket, "meteor>").contains("Authentication successful."));
                this.writeLine(socket, "status");
                assertTrue(this.readUntil(socket, "meteor>").contains("Governance : LOCAL"));
            }
        }
    }

    @Test
    public void authenticationIsIsolatedBetweenSessions() throws Exception {
        // 同一管理服务接受两个独立连接。
        MetConfiguration config = this.configuration("auth-isolation");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setPassword("test-secret"));
        try (MetContext context = config.buildContext(); //
             TelClient first = new TelClient()) {
            context.start();
            var address = context.getExtension(MetTelnetExtension.class).getListenAddress();
            first.setPassword("test-secret");
            first.connectTo(address);
            assertTrue(first.sendCommand("status").contains("Governance : LOCAL"));

            // 一个连接认证成功，不授予另一连接权限。
            try (Socket second = new Socket("127.0.0.1", address.getPort())) {
                assertEquals("Password:\r\n", this.readUntil(second, "\n"));
                this.writeLine(second, "status");
                assertTrue(this.readUntil(second, "Password:\r\n").contains("Invalid password."));
            }
        }
    }

    @Test
    public void reconnectRequiresAuthentication() throws Exception {
        // 会话关闭后，其认证状态不能转移给新连接。
        MetConfiguration config = this.configuration("auth-reconnect");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setPassword("test-secret"));
        try (MetContext context = config.buildContext()) {
            context.start();
            var address = context.getExtension(MetTelnetExtension.class).getListenAddress();
            try (TelClient first = new TelClient()) {
                first.setPassword("test-secret");
                first.connectTo(address);
                assertTrue(first.sendCommand("status").contains("Governance : LOCAL"));
            }

            // 重连仍需认证。
            try (Socket second = new Socket("127.0.0.1", address.getPort())) {
                assertEquals("Password:\r\n", this.readUntil(second, "\n"));
                this.writeLine(second, "test-secret");
                assertTrue(this.readUntil(second, "meteor>").contains("Authentication successful."));
                this.writeLine(second, "status");
                assertTrue(this.readUntil(second, "meteor>").contains("Governance : LOCAL"));
            }
        }
    }

    @Test
    public void nativeSessionOptionsRemainAvailable() throws Exception {
        // Meteor 命令沿用 tconsole 的帮助和会话选项。
        MetConfiguration config = this.configuration("native-options");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); //
             TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            // 原生命令和 Meteor 管理命令共用同一会话。
            client.sendCommand("set example=meteor");
            assertTrue(client.sendCommand("get example").contains("meteor"));
            assertTrue(client.sendCommand("help").contains("status"));
            assertTrue(client.sendCommand("status").contains("Governance : LOCAL"));
        }
    }

    @Test
    public void multilineRulePreservesBodyAndAppliesAfterValidation() throws Exception {
        // 流控 XML 通过多行输入，而非命令行转义传递。
        MetConfiguration config = this.configuration("multiline");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            // 完整正文直到终止行才提交。
            String result = client.sendCommand("rule put '" + id + "' flow --multiline\n<controlSet>\n</controlSet>\n.end");
            assertTrue(result.startsWith("OK"), result);
            assertEquals("<controlSet>\n</controlSet>", context.getUpdater().flowControl(id));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "service", "method", "args", "flow" })
    public void localRuleCanBeWrittenReadAndCleared(String type) throws Exception {
        // 每种规则分别验证写入、读取、清除，避免规则名称映射到错误的 API。
        MetConfiguration config = this.configuration("local-rule-" + type);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        String xml = StringUtils.equals(type, "flow") ? "<controlSet/>" : "<route><return value=\"addresses\"/></route>";
        try (MetContext context = config.buildContext(); //
             TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            assertTrue(client.sendCommand("rule put '" + id + "' " + type + " '" + xml + "'").startsWith("OK"));
            assertEquals(xml, client.sendCommand("rule show '" + id + "' " + type));
            assertTrue(client.sendCommand("rule clear '" + id + "' " + type).startsWith("OK"));
            assertEquals("EMPTY", client.sendCommand("rule show '" + id + "' " + type));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "service", "method", "args", "flow" })
    public void registryRuleUsesMatchingRuleType(String type) throws Exception {
        // 内存注册中心保存到当前实例服务，命令中的名称必须对应正确的规则类型。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration config = this.configuration("registry-rule-" + type);
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        String xml = StringUtils.equals(type, "flow") ? "<controlSet/>" : "<route><return value=\"addresses\"/></route>";
        MetRuleType expected = switch (type) {
            case "service" -> MetRuleType.SERVICE_ROUTE;
            case "method" -> MetRuleType.METHOD_ROUTE;
            case "args" -> MetRuleType.ARGS_ROUTE;
            case "flow" -> MetRuleType.FLOW_CONTROL;
            default -> throw new IllegalArgumentException(type);
        };
        try (MetContext context = config.buildContext(); //
             TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            assertTrue(client.sendCommand("rule put '" + id + "' " + type + " '" + xml + "'").startsWith("OK"));
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);
            assertEquals(xml, admin.getServiceAdmin(id, context.getInstanceID()).fetchRule(expected));
            assertNull(admin.getServiceAdmin(id).fetchRule(expected));
            assertEquals(xml, client.sendCommand("rule show '" + id + "' " + type));
        }
    }

    @Test
    public void invalidRuleDoesNotReplaceExistingRule() throws Exception {
        // 预先保存合法规则，验证错误输入不会清除旧规则。
        MetConfiguration config = this.configuration("invalid-rule");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.getUpdater().updateFlowControl(id, "<controlSet/>");
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("rule put '" + id + "' flow '<invalid/>'");
            assertTrue(result.contains("INVALID_ARGUMENT"), result);
            assertEquals("<controlSet/>", context.getUpdater().flowControl(id));
        }
    }

    @Test
    public void metadataSupportsQuotedUnicodeJson() throws Exception {
        // 使用中文元信息值，验证参数引号与 JSON 边界。
        MetConfiguration config = this.configuration("metadata");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            MetBindInfo<?> service = context.publisher().rsfService(Echo.class, value -> value).register();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("meta put '" + service.getBindID() + "' tag '\"中文 值\"'");
            assertTrue(result.startsWith("OK"), result);
            assertEquals("中文 值", service.getMetaData("tag"));
        }
    }

    @Test
    public void registryGovernanceOnlyChangesCurrentInstanceService() throws Exception {
        // 同一注册中心接入两个实例，服务 ID 相同。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration a = this.configuration("instance-a");
        a.addExtension(new MetTelnetConfiguration().setPort(0));
        a.addExtension(registry);
        MetConfiguration b = this.configuration("instance-b");
        b.addExtension(registry);
        try (MetContext first = a.buildContext(); MetContext second = b.buildContext(); TelClient client = new TelClient()) {
            String id = first.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            second.publisher().rsfService(Echo.class, value -> value).register();
            first.start();
            second.start();
            client.connectTo(first.getExtension(MetTelnetExtension.class).getListenAddress());

            // 状态识别注册中心，操作写当前实例范围并同步到本机。
            String status = client.sendCommand("status");
            assertTrue(status.contains("Registry : memory"), status);
            assertTrue(status.contains("Connection : CONNECTED"), status);
            String result = client.sendCommand("service disable '" + id + "'");
            assertTrue(result.startsWith("OK"), result);
            assertFalse(first.getExtension(RegistryAdmin.class).getServiceAdmin(id, first.getInstanceID()).isEnable());
            assertTrue(first.getExtension(RegistryAdmin.class).getServiceAdmin(id, second.getInstanceID()).isEnable());
            assertFalse(first.getUpdater().isServiceEnabled(id));
            assertTrue(second.getUpdater().isServiceEnabled(id));
        }
    }

    @Test
    public void savedGovernanceIsAppliedWhenSameInstanceRestarts() throws Exception {
        // 保留同一个注册中心及数据目录；关闭只撤销运行登记，不删除治理配置。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration config = this.configuration("restart");
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        String instanceId;
        String serviceId;
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            serviceId = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            instanceId = context.getInstanceID();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());
            assertTrue(client.sendCommand("service disable '" + serviceId + "'").startsWith("OK"));
        }

        // 同一实例重启后从注册中心恢复禁用状态。
        try (MetContext restarted = config.buildContext()) {
            restarted.publisher().rsfService(Echo.class, value -> value).register();
            restarted.start();
            assertEquals(instanceId, restarted.getInstanceID());
            assertFalse(restarted.getUpdater().isServiceEnabled(serviceId));
        }
    }

    @Test
    public void registryFailureIsVisibleAndDoesNotFallBackToLocalWrites() throws Exception {
        // 通过正式内存存储的装饰器注入连接异常，保持注册中心模式已配置。
        AtomicBoolean failed = new AtomicBoolean();
        MemoryRegistryConfiguration storage = new MemoryRegistryConfiguration();
        RegistryConfiguration registry = new RegistryConfiguration() {
            @Override
            public String getProvider() {
                return "memory-fault";
            }

            @Override
            protected Callable<StorageAccess> createStorageOpener() {
                return () -> {
                    var delegate = storage.openStorage();
                    Class<?> type = StorageAccess.class;
                    return (StorageAccess) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                        if (failed.get() && !StringUtils.equals(method.getName(), "close")) {
                            throw new IllegalStateException("simulated registry outage");
                        }
                        try {
                            return method.invoke(delegate, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    });
                };
            }
        };
        registry.setManagementToken("secret");
        registry.setRefreshMillis(600000);
        MetConfiguration config = this.configuration("registry-failure");
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());
            RegistryStatus before = context.getExtension(RegistryStatus.class);
            assertNotNull(before.getLastSuccess());

            // 拉取失败对调用方可见，状态保留成功时间但标记当前异常。
            failed.set(true);
            assertThrows(ExecutionException.class, () -> context.getExtension(RegistryClient.class).pullSync().get(5, TimeUnit.SECONDS));
            String status = client.sendCommand("status");
            assertTrue(status.contains("Connection : ERROR"), status);
            assertTrue(status.contains("Governance : REGISTRY"), status);
            assertEquals(before.getLastSuccess(), context.getExtension(RegistryStatus.class).getLastSuccess());
            assertTrue(client.sendCommand("service disable '" + id + "'").startsWith("ERROR"));
            assertTrue(context.getUpdater().isServiceEnabled(id));
        }
    }

    @Test
    public void idleSessionIsClosedWithinConfiguredTimeout() throws Exception {
        // 空闲超时只关闭该会话，不关闭管理监听。
        MetConfiguration config = this.configuration("idle");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setIdleTimeoutMillis(200));
        try (MetContext context = config.buildContext()) {
            context.start();
            var address = context.getExtension(MetTelnetExtension.class).getListenAddress();
            try (Socket idle = new Socket(address.getAddress(), address.getPort())) {
                idle.setSoTimeout(3000);
                idle.getInputStream().readAllBytes();
                assertNotNull(context.getExtension(MetTelnetExtension.class).getListenAddress());
            }
        }
    }

    @Test
    public void unterminatedOversizedInputClosesSession() throws Exception {
        // 没有换行的输入同样受内存上限约束。
        MetConfiguration config = this.configuration("input-limit");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setMaxInputBytes(128));
        try (MetContext context = config.buildContext()) {
            context.start();
            var address = context.getExtension(MetTelnetExtension.class).getListenAddress();
            try (Socket oversized = new Socket(address.getAddress(), address.getPort())) {
                oversized.setSoTimeout(3000);
                oversized.getOutputStream().write(new byte[1024]);
                oversized.getOutputStream().flush();
                oversized.getInputStream().readAllBytes();
            }
        }
    }

    @Test
    public void serviceInfoContainsFullSignatureAndActualBinding() throws Exception {
        // 发布服务后查询实际契约，而非仅输出方法名。
        MetConfiguration config = this.configuration("service-info");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("service info '" + id + "'");
            assertTrue(result.contains("java.lang.String"), result);
            assertTrue(result.contains("echo"), result);
            assertTrue(result.contains("listenAddress"), result);
            assertTrue(result.contains(context.getListenAddress("rsf").toString()), result);
        }
    }

    @Test
    public void malformedQuotedCommandDoesNotPoisonSession() throws Exception {
        // 错误引号必须返回参数错误，而不是阻塞后续命令。
        MetConfiguration config = this.configuration("quoted-error");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());
            assertTrue(client.sendCommand("service info 'unterminated").contains("INVALID_ARGUMENT"));
            assertTrue(client.sendCommand("status").contains("Governance : LOCAL"));
        }
    }

    @Test
    public void blockedInstanceCanBeRecoveredThroughManagementConnection() throws Exception {
        // 实例阻断不影响独立的管理监听。
        MetConfiguration config = this.configuration("block-recovery");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            assertTrue(client.sendCommand("instance block").startsWith("OK"));
            assertTrue(client.sendCommand("status").contains("Blocked : true"));
            assertTrue(client.sendCommand("instance unblock").startsWith("OK"));
            assertFalse(context.getUpdater().isBlock());
        }
    }

    @Test
    public void registryRuleIsSavedAtInstanceServiceScope() throws Exception {
        // 规则仅写当前实例服务，不修改服务全局配置。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration config = this.configuration("registry-rule");
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            String id = context.publisher().rsfService(Echo.class, value -> value).register().getBindID();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("rule put '" + id + "' flow '<controlSet/>'");
            assertTrue(result.startsWith("OK"), result);
            RegistryAdmin admin = context.getExtension(RegistryAdmin.class);
            assertEquals("<controlSet/>", admin.getServiceAdmin(id, context.getInstanceID()).fetchRule(MetRuleType.FLOW_CONTROL));
            assertNull(admin.getServiceAdmin(id).fetchRule(MetRuleType.FLOW_CONTROL));
            assertEquals("<controlSet/>", context.getUpdater().flowControl(id));
        }
    }

    @Test
    public void registryMetadataIsSavedAndAppliedToCurrentService() throws Exception {
        // 元信息通过中心配置下发，不依赖进程内临时修改。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration config = this.configuration("registry-meta");
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            MetBindInfo<?> binding = context.publisher().rsfService(Echo.class, value -> value).register();
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("meta put '" + binding.getBindID() + "' weight '123'");
            assertTrue(result.startsWith("OK"), result);
            assertEquals(123, binding.getMetaData("weight"));
            var admin = context.getExtension(RegistryAdmin.class).getServiceAdmin(binding.getBindID(), context.getInstanceID());
            assertEquals(123, admin.fetchMeta().get("weight"));
        }
    }

    @Test
    public void registryInstanceBlockIsPersistedAndManagementRemainsAccessible() throws Exception {
        // 阻断配置保存于当前实例，管理端口不参与业务阻断。
        MemoryRegistryConfiguration registry = new MemoryRegistryConfiguration();
        registry.setManagementToken("secret");
        MetConfiguration config = this.configuration("registry-block");
        config.addExtension(registry);
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            client.connectTo(context.getExtension(MetTelnetExtension.class).getListenAddress());

            String result = client.sendCommand("instance block");
            assertTrue(result.startsWith("OK"), result);
            assertTrue(context.getExtension(RegistryAdmin.class).getInstanceAdmin(context.getInstanceID()).isBlock());
            assertTrue(client.sendCommand("status").contains("Blocked : true"));
        }
    }

    @Test
    public void occupiedManagementPortFailsContextStartup() throws Exception {
        // 外部监听占用管理端口，不允许构建出假成功的应用。
        try (ServerSocket occupied = new ServerSocket(0)) {
            MetConfiguration config = this.configuration("occupied");
            config.addExtension(new MetTelnetConfiguration().setPort(occupied.getLocalPort()));
            try (MetContext context = config.buildContext()) {
                assertThrows(RuntimeException.class, context::start);
            }
        }
    }

    @Test
    public void closingContextReleasesManagementPort() throws Exception {
        // 保存实际监听端口，在上下文关闭后重新绑定验证资源释放。
        MetConfiguration config = this.configuration("release");
        config.addExtension(new MetTelnetConfiguration().setPort(0));
        int port;
        try (MetContext context = config.buildContext()) {
            context.start();
            port = context.getExtension(MetTelnetExtension.class).getListenAddress().getPort();
        }
        try (ServerSocket reopened = new ServerSocket(port)) {
            assertEquals(port, reopened.getLocalPort());
        }
    }

    @Test
    public void sessionLimitRejectsAdditionalConnection() throws Exception {
        // 第一条已初始化会话占用唯一名额。
        MetConfiguration config = this.configuration("limit");
        config.addExtension(new MetTelnetConfiguration().setPort(0).setMaxSessions(1));
        try (MetContext context = config.buildContext(); TelClient client = new TelClient()) {
            context.start();
            var address = context.getExtension(MetTelnetExtension.class).getListenAddress();
            client.connectTo(address);
            try (Socket rejected = new Socket(address.getAddress(), address.getPort())) {
                rejected.setSoTimeout(3000);
                assertTrue(rejected.getInputStream().readAllBytes().length > 0);
            }
            assertTrue(client.sendCommand("status").contains("Governance : LOCAL"));
        }
    }

    private String readUntil(Socket socket, String marker) throws Exception {
        socket.setSoTimeout(3000);
        StringBuilder response = new StringBuilder();
        int value;
        while ((value = socket.getInputStream().read()) != -1) {
            response.append((char) value);
            if (marker != null && response.toString().endsWith(marker)) {
                break;
            }
        }
        return response.toString();
    }

    private void writeLine(Socket socket, String value) throws Exception {
        socket.getOutputStream().write((value + "\n").getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }
}
