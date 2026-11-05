/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.MetContext;
import net.hasor.meteor.MetExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class MetDataDirectoryTest {
    @TempDir
    private Path temporary;

    @Test
    public void defaultDirectoryUsesUserHomeAndApplicationName() {
        // 不创建目录，也不修改同一 JVM 内其他测试使用的 user.home。
        MetConfiguration configuration = new MetConfiguration() //
                .setApplicationName("directory-default-example");
        Path expected = Path.of(System.getProperty("user.home"), ".meteor", "directory-default-example");

        // 默认数据位置由用户目录与当前应用名称共同决定。
        assertEquals(expected.toAbsolutePath().normalize(), configuration.getDataHome());
    }

    @Test
    public void explicitDirectoryIsIndependentOfApplicationName() {
        // 显式数据目录不随应用名称变化。
        Path directory = this.temporary.resolve("custom-data");
        MetConfiguration configuration = new MetConfiguration() //
                .setDataHome(directory) //
                .setApplicationName("renamed-application");

        assertEquals(directory, configuration.getDataHome());
    }

    @Test
    public void buildPersistsIdentityBeforeStartingNetwork() throws Exception {
        // 构建上下文就取得数据目录，无需先开启网络监听。
        Path directory = this.temporary.resolve("provider");
        MetConfiguration configuration = configuration(directory);
        try (MetContext context = configuration.buildContext()) {
            assertTrue(Files.isRegularFile(directory.resolve("meteor.lock")));
            String stored = Files.readString(directory.resolve("meteor.info"), StandardCharsets.UTF_8).trim();

            // 已返回的实例 ID 与持久化内容一致，且是有效 UUID。
            assertEquals(context.getInstanceID(), stored);
            assertTrue(stored.matches("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"));
            assertEquals(directory, context.getSettings().getDataHome());
        }
    }

    @Test
    public void restartInSameDirectoryReusesIdentity() throws Exception {
        // 首次启动后正常关闭，保留数据目录中的实例身份。
        MetConfiguration configuration = configuration(this.temporary);
        String identity;
        try (MetContext context = configuration.buildContext()) {
            context.start();
            identity = context.getInstanceID();
        }

        // 相同目录再次启动时复用上次的实例 ID。
        try (MetContext restarted = configuration.buildContext()) {
            restarted.start();
            assertEquals(identity, restarted.getInstanceID());
        }
    }

    @Test
    public void differentDirectoriesHaveIndependentIdentities() throws Exception {
        // 同一应用可以显式使用不同数据目录运行多个独立实例。
        MetConfiguration first = configuration(this.temporary.resolve("first"));
        MetConfiguration second = configuration(this.temporary.resolve("second"));
        try (MetContext firstContext = first.buildContext(); //
             MetContext secondContext = second.buildContext()) {
            assertNotEquals(firstContext.getInstanceID(), secondContext.getInstanceID());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "12345678-1234-4234-8234-123456789abc", "ABCDEFAB123442348234123456789ABC" })
    public void validStoredUuidIsReusedWithoutRewriting(String identity) throws Exception {
        // 已存在的标准 UUID 或紧凑 UUID 都是有效身份，保留其原始表示。
        Path info = this.temporary.resolve("meteor.info");
        String original = "  " + identity + "\n";
        Files.writeString(info, original, StandardCharsets.UTF_8);
        MetConfiguration configuration = configuration(this.temporary);

        try (MetContext context = configuration.buildContext()) {
            assertEquals(identity, context.getInstanceID());
            assertEquals(original, Files.readString(info, StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { // 每项都是不能作为 UUID 使用的身份文件内容。
            "", "\n  ", "not-a-uuid", "12345678-1234-4234-8234-123456789ab", //
            "uuid=12345678123442348234123456789abc" })
    public void malformedInfoIsReplacedWithFreshUuid(String malformed) throws Exception {
        // 旧文件存在，但内容无法作为 UUID 使用。
        Path info = this.temporary.resolve("meteor.info");
        Files.writeString(info, malformed, StandardCharsets.UTF_8);
        MetConfiguration configuration = configuration(this.temporary);

        // 构建成功后写入新身份，后续启动读取的就是这个身份。
        try (MetContext context = configuration.buildContext()) {
            String stored = Files.readString(info, StandardCharsets.UTF_8).trim();
            assertEquals(context.getInstanceID(), stored);
            assertTrue(stored.matches("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"));
        }
    }

    @Test
    public void unreadableUtf8InfoIsReplacedWithFreshUuid() throws Exception {
        // 非法 UTF-8 不能被正确读取为 UUID，允许用新身份覆盖。
        Path info = this.temporary.resolve("meteor.info");
        Files.write(info, new byte[] { (byte) 0xc3, (byte) 0x28 });
        MetConfiguration configuration = configuration(this.temporary);

        try (MetContext context = configuration.buildContext()) {
            assertEquals(context.getInstanceID(), Files.readString(info, StandardCharsets.UTF_8).trim());
        }
    }

    @Test
    public void anotherContextCannotUseLockedDirectory() throws Exception {
        // 同一 JVM 内的第一个上下文持有目录锁。
        MetConfiguration configuration = configuration(this.temporary);
        try (MetContext context = configuration.buildContext()) {
            String identity = context.getInstanceID();

            // 第二次构建失败，同时不能改写原上下文的实例身份。
            IOException failure = assertThrows(IOException.class, configuration::buildContext);
            assertTrue(failure.getMessage().contains("meteor.lock"));
            assertEquals(identity, Files.readString(this.temporary.resolve("meteor.info")).trim());
        }
    }

    @Test
    public void equivalentPathsCannotBypassDirectoryLock() throws Exception {
        // 路径中的父目录跳转不能使同一个实际数据目录取得两次排他锁。
        MetConfiguration first = configuration(this.temporary);
        Path equivalent = this.temporary.resolve("child").resolve("..");
        MetConfiguration second = configuration(equivalent);
        try (MetContext context = first.buildContext()) {
            assertThrows(IOException.class, second::buildContext);
        }
    }

    @Test
    public void closingUnstartedContextReleasesLock() throws Exception {
        // 构建后尚未 start 的上下文也拥有目录锁。
        MetConfiguration configuration = configuration(this.temporary);
        String identity;
        try (MetContext context = configuration.buildContext()) {
            identity = context.getInstanceID();
        }

        // 关闭后可立即重新构建，不需要等待网络或后台任务启动。
        try (MetContext replacement = configuration.buildContext()) {
            assertEquals(identity, replacement.getInstanceID());
        }
    }

    @Test
    public void repeatedCloseCannotReleaseReplacementContextsLock() throws Exception {
        // 第一个上下文关闭后，第二个上下文接管同一目录。
        MetConfiguration configuration = configuration(this.temporary);
        try (MetContext first = configuration.buildContext()) {
            first.close();
            try (MetContext replacement = configuration.buildContext()) {
                // 对旧上下文重复 close 不能误释放新上下文当前持有的锁。
                first.close();
                assertThrows(IOException.class, configuration::buildContext);
            }
        }
    }

    @Test
    public void extensionCreationFailureReleasesLock() throws Exception {
        // 在取得目录锁后的扩展初始化阶段注入失败。
        MetConfiguration failing = configuration(this.temporary);
        failing.addExtension(context -> {
            throw new IOException("extension creation failed");
        });
        assertThrows(IllegalStateException.class, failing::buildContext);

        // 失败构建不留下无法重新启动的目录锁。
        MetConfiguration replacement = configuration(this.temporary);
        try (MetContext context = replacement.buildContext()) {
            assertNotNull(context.getInstanceID());
        }
    }

    @Test
    public void startupFailureReleasesLock() throws Exception {
        // 构建正常，扩展在正式启动时失败。
        MetConfiguration failing = configuration(this.temporary);
        failing.addExtension(context -> new FailingStartExtension());
        try (MetContext failed = failing.buildContext()) {
            assertThrows(IllegalStateException.class, failed::start);

            // start 失败立即释放锁，不依赖调用方再次 close。
            MetConfiguration replacement = configuration(this.temporary);
            try (MetContext context = replacement.buildContext()) {
                assertEquals(failed.getInstanceID(), context.getInstanceID());
            }
        }
    }

    @Test
    public void extensionCloseFailureStillReleasesLock() throws Exception {
        // 扩展关闭抛异常时，数据目录仍必须完成释放。
        MetConfiguration failing = configuration(this.temporary);
        failing.addExtension(context -> new FailingCloseExtension());
        try (MetContext failed = failing.buildContext()) {
            assertThrows(IllegalStateException.class, failed::close);

            // 清理异常不能使后续启动一直被旧锁阻挡。
            MetConfiguration replacement = configuration(this.temporary);
            try (MetContext context = replacement.buildContext()) {
                assertEquals(failed.getInstanceID(), context.getInstanceID());
            }
        }
    }

    @Test
    public void dataDirectoryCannotBeARegularFile() throws Exception {
        // 配置指向普通文件，不能当作数据目录使用。
        Path file = this.temporary.resolve("not-a-directory");
        Files.writeString(file, "existing data");
        MetConfiguration configuration = configuration(file);

        assertThrows(IOException.class, configuration::buildContext);
        assertEquals("existing data", Files.readString(file));
    }

    @Test
    public void infoWriteFailureReleasesLock() throws Exception {
        // meteor.info 被目录占用，无法写入新的实例身份。
        Path info = this.temporary.resolve("meteor.info");
        Files.createDirectory(info);
        MetConfiguration configuration = configuration(this.temporary);
        assertThrows(IOException.class, configuration::buildContext);

        // 排除文件问题后同一目录能立即重新构建。
        Files.delete(info);
        try (MetContext context = configuration.buildContext()) {
            assertEquals(context.getInstanceID(), Files.readString(info).trim());
        }
    }

    @Test
    public void independentJvmCannotTakeOwnedDirectoryLock() throws Exception {
        // 当前上下文持有目录，另一个真实 JVM 尝试构建自己的上下文。
        MetConfiguration configuration = configuration(this.temporary);
        Path output = this.temporary.resolve("peer-probe.log");
        try (MetContext context = configuration.buildContext()) {
            Process process = new ProcessBuilder(peerCommand("probe", this.temporary)) //
                    .directory(this.temporary.toFile()).redirectErrorStream(true) //
                    .redirectOutput(output.toFile()).start();
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "独立 JVM 未在期限内完成目录锁检查");

                // 跨进程排他失败必须来自真实文件锁，而不是仅靠 JVM 内的 Map。
                String diagnostics = Files.readString(output);
                assertEquals(23, process.exitValue(), diagnostics);
                assertTrue(diagnostics.contains("METEOR_LOCK_REJECTED"), diagnostics);
                assertTrue(diagnostics.contains("meteor.lock"), diagnostics);
                assertEquals(context.getInstanceID(), Files.readString(this.temporary.resolve("meteor.info")).trim());
            } finally {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "独立 JVM 无法结束");
            }
        }
    }

    @Test
    public void exitedJvmReleasesLockAndKeepsIdentity() throws Exception {
        // 由独立 JVM 取得锁；通过输出标记确认已经完成公开构建入口。
        Process process = new ProcessBuilder(peerCommand("hold", this.temporary)) //
                .directory(this.temporary.toFile()).redirectErrorStream(true).start();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<String> identity = reader.submit(() -> {
            InputStreamReader stream = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
            try (BufferedReader output = new BufferedReader(stream)) {
                String line;
                while ((line = output.readLine()) != null) {
                    if (line.startsWith("METEOR_LOCK_READY=")) {
                        return line.substring("METEOR_LOCK_READY=".length());
                    }
                }
                throw new IOException("持锁 JVM 在输出就绪标记前结束");
            }
        });
        try {
            String previous = identity.get(30, TimeUnit.SECONDS);
            MetConfiguration configuration = configuration(this.temporary);
            assertThrows(IOException.class, configuration::buildContext);

            // 强制结束进程，不执行 Java close，操作系统仍须释放文件锁。
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "持锁 JVM 无法结束");
            try (MetContext restarted = configuration.buildContext()) {
                assertEquals(previous, restarted.getInstanceID());
            }
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "持锁 JVM 无法结束");
            reader.shutdownNow();
            assertTrue(reader.awaitTermination(10, TimeUnit.SECONDS), "子进程输出读取线程无法结束");
        }
    }

    private static MetConfiguration configuration(Path directory) {
        MetConfiguration configuration = new MetConfiguration() //
                .setDataHome(directory) //
                .setApplicationName("persistent-instance-test");
        configuration.connector("tcp") //
                .protocol("rsf");
        return configuration;
    }

    private static List<String> peerCommand(String mode, Path directory) {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (!Files.isRegularFile(java)) {
            java = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        }
        String fallbackClasspath = System.getProperty("java.class.path");
        String classpath = System.getProperty("meteor.test.runtimeClasspath", fallbackClasspath);
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.add("-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"));
        command.add("-Duser.home=" + System.getProperty("user.home"));
        command.add("-cp");
        command.add(classpath);
        command.add(MetDataDirectoryPeer.class.getName());
        command.add(mode);
        command.add(directory.toAbsolutePath().toString());
        return command;
    }

    private static final class FailingStartExtension implements MetExtension {
        @Override
        public <T> T getService(Class<T> type) {
            return null;
        }

        @Override
        public void beforeStart() throws Exception {
            throw new IOException("extension startup failed");
        }

        @Override
        public void afterStart() {
        }

        @Override
        public void close() {
        }
    }

    private static final class FailingCloseExtension implements MetExtension {
        @Override
        public <T> T getService(Class<T> type) {
            return null;
        }

        @Override
        public void beforeStart() {
        }

        @Override
        public void afterStart() {
        }

        @Override
        public void close() throws Exception {
            throw new IOException("extension close failed");
        }
    }
}
