/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.console.component.commands;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CommandCredentialsTest {
    @Test
    public void nestedCallsRestorePreviousTokenEvenAfterFailure() throws Exception {
        // 内层命令失败后恢复外层凭据，不能覆盖同线程的调用方。
        CommandCredentials credentials = new CommandCredentials();
        credentials.withToken("outer", () -> {
            assertEquals("outer", credentials.getToken());
            assertThrows(IllegalStateException.class, () -> credentials.withToken("inner", () -> {
                assertEquals("inner", credentials.getToken());
                throw new IllegalStateException("command failed");
            }));
            assertEquals("outer", credentials.getToken());
            return null;
        });
    }

    @Test
    public void concurrentConnectionsDoNotShareCredentials() throws Exception {
        CommandCredentials credentials = new CommandCredentials();
        CountDownLatch entered = new CountDownLatch(2);
        var workers = Executors.newFixedThreadPool(2);
        try {
            // 两个线程同时停留在凭据范围内，再分别读取，不能交叉覆盖。
            var first = workers.submit(() -> credentials.withToken("first", () -> {
                entered.countDown();
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                return credentials.getToken();
            }));
            var second = workers.submit(() -> credentials.withToken("second", () -> {
                entered.countDown();
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                return credentials.getToken();
            }));
            assertEquals("first", first.get(5, TimeUnit.SECONDS));
            assertEquals("second", second.get(5, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void blankCredentialsAreRejectedBeforeCallingBackend() {
        CommandCredentials credentials = new CommandCredentials();
        assertThrows(IllegalArgumentException.class, () -> credentials.withToken(" ", () -> {
            fail("Blank credentials must not execute a management call");
            return null;
        }));
    }
}
