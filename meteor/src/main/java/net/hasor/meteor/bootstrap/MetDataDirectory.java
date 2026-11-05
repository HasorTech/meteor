/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.bootstrap;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Owns an instance directory lock and its persistent identity for one context lifetime. */
final class MetDataDirectory implements AutoCloseable {
    private static final Set<Path>   DIRECTORIES = new HashSet<>();
    private final        Path        directory;
    private final        FileChannel channel;
    private final        FileLock    lock;
    private final        String      instanceID;
    private              boolean     closed;

    private MetDataDirectory(Path directory, FileChannel channel, FileLock lock, String instanceID) {
        this.directory = directory;
        this.channel = channel;
        this.lock = lock;
        this.instanceID = instanceID;
    }

    static MetDataDirectory open(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path canonical = directory.toRealPath();
        Path lockPath = canonical.resolve("meteor.lock");
        synchronized (DIRECTORIES) {
            // 同 JVM 不再打开已占用的锁文件；部分系统关闭另一个描述符也会释放进程持有的文件锁。
            if (!DIRECTORIES.add(canonical)) {
                throw occupied(lockPath, null);
            }
        }

        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException failure) {
                throw occupied(lockPath, failure);
            }
            if (lock == null) {
                throw occupied(lockPath, null);
            }

            // 身份文件只允许在获得目录排它锁之后读取或重写。
            String instanceID = loadInstanceID(canonical.resolve("meteor.info"));
            return new MetDataDirectory(canonical, channel, lock, instanceID);
        } catch (IOException | RuntimeException | Error failure) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (Throwable cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            synchronized (DIRECTORIES) {
                DIRECTORIES.remove(canonical);
            }
            throw failure;
        }
    }

    private static IOException occupied(Path lockPath, Throwable cause) {
        return new IOException("Meteor data directory is already in use; cannot acquire exclusive lock: " + lockPath, cause);
    }

    private static String loadInstanceID(Path infoPath) throws IOException {
        String existing = null;
        try {
            existing = Files.readString(infoPath, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            // 缺失或无法读取的身份重新生成；无法写入时仍须让构建失败。
        }
        if (existing != null && (existing.matches("[0-9a-fA-F]{32}") //
                || existing.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            return existing;
        }

        String instanceID = UUID.randomUUID().toString().replace("-", "");
        Files.writeString(infoPath, instanceID + "\n", StandardCharsets.UTF_8, //
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return instanceID;
    }

    String getInstanceID() {
        return this.instanceID;
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.closed) {
            return;
        }
        this.closed = true;
        try {
            if (this.lock.isValid()) {
                this.lock.release();
            }
        } finally {
            try {
                // 锁文件保留，避免删除后新建文件使两个进程锁定不同 inode。
                this.channel.close();
            } finally {
                synchronized (DIRECTORIES) {
                    DIRECTORIES.remove(this.directory);
                }
            }
        }
    }
}
