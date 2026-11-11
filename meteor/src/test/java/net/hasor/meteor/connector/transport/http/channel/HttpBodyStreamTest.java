/*
 * Copyright 2015-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http.channel;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HttpBodyStreamTest {
    @Test
    public void discardReleasesExistingAndFutureInputWithoutFailingTheExchange() throws Exception {
        // 交付请求头时正文尚未收齐，丢弃只能归还额度，不能把交换关闭。
        HttpBodyStream body = new HttpBodyStream(8);
        HttpExchange exchange = new HttpExchange(HttpRequest.newStreaming("GET", "/rpc?proto", Map.of(), body, 1));
        AtomicInteger released = new AtomicInteger();
        body.offer(new byte[4], released::incrementAndGet);
        assertEquals(0, body.read());
        body.discard();
        assertEquals(1, released.get());
        assertFalse(exchange.completion().isDone());

        // 后续正文无需累积到接收窗口中，EOF 正常完成输入。
        body.offer(new byte[32], released::incrementAndGet);
        assertEquals(2, released.get());
        assertEquals(8, body.writableBytes());
        body.finish(Map.of());
        assertTrue(body.completion().isDone());
        assertNull(body.completion().getCause());
        assertFalse(exchange.completion().isDone());
    }

    @Test
    public void discardAfterEofCompletesUnreadInput() throws Exception {
        // 先收到完整正文，再决定不读取；已有缓冲也必须释放。
        HttpBodyStream body = new HttpBodyStream(8);
        AtomicInteger released = new AtomicInteger();
        body.offer(new byte[4], released::incrementAndGet);
        body.finish(Map.of());
        body.discard();
        assertEquals(1, released.get());
        assertTrue(body.completion().isDone());
        assertNull(body.completion().getCause());
    }
}
