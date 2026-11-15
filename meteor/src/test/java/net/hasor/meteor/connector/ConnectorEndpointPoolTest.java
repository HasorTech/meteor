/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.domain.ProtocolConfig;
import net.hasor.meteor.domain.ProtocolStatus;
import net.hasor.meteor.domain.payload.Payload;
import net.hasor.meteor.domain.payload.RequestPayload;
import net.hasor.meteor.domain.payload.ResponsePayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 HTTP 端点池的协议状态、有序执行器与成员关闭隔离。 */
@Timeout(20)
class ConnectorEndpointPoolTest {
    @Test
    void repeatedSelectionsReuseABoundedSetOfProtocolChannels() throws Exception {
        // HTTP 传输容量四，经管理器随机选取协议会话。
        try (ConnectorManager server = SocketTestSupport.manager(config(true)); ConnectorManager client = SocketTestSupport.manager(config(false))) {
            server.init();
            client.init();
            server.startInbound();
            InterAddress target = server.getListenAddress("httpa");
            Set<MetChannel> first = selected(client, target);
            Set<MetChannel> second = selected(client, target);

            // 后续选取复用相同的有界会话集，而不是始终返回一个外层共享会话。
            assertEquals(4, first.size());
            assertEquals(first, second);
        }
    }

    @Test
    void identicalRequestIdsOnDifferentMembersKeepTheirOwnReplies() throws Exception {
        // 四个成员分别发送相同 RPC 编号，服务端先保留所有响应。
        try (ConnectorManager server = SocketTestSupport.manager(config(true)); ConnectorManager client = SocketTestSupport.manager(config(false))) {
            LinkedBlockingQueue<Delivery> arrivals = new LinkedBlockingQueue<>();
            LinkedBlockingQueue<Delivery> replies = new LinkedBlockingQueue<>();
            server.init();
            client.init();
            server.subscribe((channel, id, payload) -> arrivals.add(new Delivery(channel, id, payload)));
            client.subscribe((channel, id, payload) -> replies.add(new Delivery(channel, id, payload)));
            server.startInbound();
            Set<MetChannel> members = selected(client, server.getListenAddress("httpa"));
            assertEquals(4, members.size());
            for (MetChannel member : members) {
                member.sendData(request(7)).get(3, TimeUnit.SECONDS);
            }
            List<Delivery> pending = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                Delivery arrival = arrivals.poll(3, TimeUnit.SECONDS);
                assertNotNull(arrival);
                pending.add(arrival);
            }

            // 倒序回复，以客户端物理端口标识响应，核对来源成员与各自编号。
            Collections.reverse(pending);
            for (Delivery arrival : pending) {
                String value = "port-" + arrival.channel.getRemote().getPort();
                arrival.channel.sendData(response(arrival.id, value)).get(3, TimeUnit.SECONDS);
            }
            Set<MetChannel> completed = new HashSet<>();
            for (int i = 0; i < members.size(); i++) {
                Delivery reply = replies.poll(3, TimeUnit.SECONDS);
                assertNotNull(reply);
                ResponsePayload value = assertInstanceOf(ResponsePayload.class, reply.payload);
                assertEquals(7, reply.id);
                assertEquals("A:port-" + reply.channel.getLocal().getPort(), value.getReturnData());
                completed.add(reply.channel);
            }
            assertEquals(members, completed);
        }
    }

    @Test
    void blockedDeliveryOnOneMemberDoesNotBlockAnotherMember() throws Exception {
        // 在一个会话的订阅回调内保持阻塞，另一个会话仍须独立交付响应。
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ConnectorManager server = SocketTestSupport.manager(config(true)); ConnectorManager client = SocketTestSupport.manager(config(false))) {
            try {
                server.init();
                client.init();
                server.subscribe((channel, id, payload) -> channel.sendData(response(id, "ok")));
                server.startInbound();
                List<MetChannel> members = new ArrayList<>(selected(client, server.getListenAddress("httpa")));
                assertEquals(4, members.size());
                MetChannel blocked = members.get(0);
                MetChannel available = members.get(1);
                LinkedBlockingQueue<MetChannel> replies = new LinkedBlockingQueue<>();
                client.subscribe((channel, id, payload) -> {
                    if (channel == blocked) {
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Blocked delivery was not released");
                            }
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    }
                    replies.add(channel);
                });
                blocked.sendData(request(1));
                assertTrue(entered.await(3, TimeUnit.SECONDS));

                // 阻塞未解除时，第二个成员已完成自己的协议处理与消息交付。
                available.sendData(request(2));
                assertSame(available, replies.poll(3, TimeUnit.SECONDS));
                release.countDown();
                assertSame(blocked, replies.poll(3, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void closingOneMemberPreservesOtherMembersAndAllowsReplacement() throws Exception {
        // 关闭一个已选中的协议会话，保留同一池中的其余成员。
        try (ConnectorManager server = SocketTestSupport.manager(config(true)); ConnectorManager client = SocketTestSupport.manager(config(false))) {
            server.init();
            client.init();
            server.startInbound();
            InterAddress target = server.getListenAddress("httpa");
            Set<MetChannel> first = selected(client, target);
            assertEquals(4, first.size());
            MetChannel retired = first.iterator().next();
            ((ConnectorChannel<?>) retired).close().get(3, TimeUnit.SECONDS);
            Set<MetChannel> next = selected(client, target);

            // 原来的其他成员继续复用；被关闭的槽位按需建立新会话。
            assertEquals(4, next.size());
            assertFalse(next.contains(retired));
            first.remove(retired);
            assertTrue(next.containsAll(first));
        }
    }

    private static ConnectorConfig config(boolean bind) {
        Map<String, String> options = Map.of("workerThread", "4", "clientPoolSize", "4", "httpVersion", "1");
        ProtocolConfig protocol = ProtocolConfig.newConfig("httpa", "httpa", Map.of("contextPath", "/a"));
        return new ConnectorConfig("pool", InterAddress.forBinding("http", "::1", 0), options, List.of(protocol), bind);
    }

    private static Set<MetChannel> selected(ConnectorManager manager, InterAddress target) throws Exception {
        Set<MetChannel> channels = new LinkedHashSet<>();
        for (int i = 0; i < 128; i++) {
            channels.add(manager.connect(target).get(3, TimeUnit.SECONDS));
        }
        return channels;
    }

    private static RequestPayload request(long id) {
        RequestPayload request = new RequestPayload();
        request.setRequestID(id);
        return request;
    }

    private static ResponsePayload response(long id, String text) {
        ResponsePayload response = new ResponsePayload();
        response.setRequestID(id);
        response.setStatus(ProtocolStatus.OK);
        response.setReturnData(text);
        return response;
    }

    private record Delivery(MetChannel channel, long id, Payload payload) {
    }
}
