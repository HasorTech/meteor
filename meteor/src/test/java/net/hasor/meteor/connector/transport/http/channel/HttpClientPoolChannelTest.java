/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport.http.channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.hasor.cobble.concurrent.future.BasicFuture;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.transport.ChannelListener;
import net.hasor.meteor.connector.transport.ChannelListenerFactory;
import net.hasor.meteor.connector.transport.NetworkChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpClientPoolChannelTest {
    @Test
    void selectsMembersForEachRequestWithoutCreatingExtraConnections() throws Exception {
        // 初始化四个成员，对外只能发布一个逻辑连接事件。
        Fixture test = new Fixture(4);
        assertTrue(test.pool.isOpen());
        assertEquals(1, test.connections.get());
        assertEquals(4, test.members.size());

        // 连续请求随机分布到固定成员，不额外创建连接，也不重复发送同一请求。
        for (int i = 0; i < 1024; i++) {
            test.pool.write(exchange());
        }
        int total = 0;
        for (Member member : test.members) {
            assertFalse(member.requests.isEmpty());
            total += member.requests.size();
        }
        assertEquals(1024, total);
        test.pool.close();
        assertEquals(1, test.disconnections.get());
    }

    @Test
    void cancellationUsesTheSelectedMembersSendFuture() throws Exception {
        // 池只负责选择，返回成员原始发送 Future。
        Fixture test = new Fixture(2);
        Future<Void> sent = test.pool.write(exchange());
        Member selected = test.members.stream().filter(member -> !member.requests.isEmpty()).findFirst().orElseThrow();
        assertSame(selected.sent, sent);

        // 取消直接到达选中的发送，不提交替代请求。
        assertTrue(sent.cancel());
        assertTrue(selected.sent.isCancelled());
        assertEquals(1, test.members.stream().mapToInt(member -> member.requests.size()).sum());
        test.pool.close();
    }

    @Test
    void gracefulCloseWaitsForEveryMemberAndRejectsNewRequests() throws Exception {
        // 模拟两个成员仍有已接受的写入，排空结束前不能发布池关闭。
        Fixture test = new Fixture(2);
        Future<Void> closed = test.pool.drainAndClose();
        assertFalse(test.pool.isOpen());
        assertFalse(closed.isDone());
        assertTrue(test.members.stream().allMatch(member -> member.draining));
        HttpExchange rejected = exchange();
        assertTrue(test.pool.write(rejected).getCause() instanceof IOException);
        assertTrue(rejected.completion().getCause() instanceof IOException);

        // 首个成员结束不能使另一个成员被强制关闭。
        test.members.get(0).finish();
        assertFalse(closed.isDone());
        assertEquals(0, test.members.get(1).forced);
        test.members.get(1).finish();
        assertTrue(closed.isDone());
        assertEquals(1, test.disconnections.get());
        assertSame(closed, test.pool.close());
    }

    @Test
    void forceCloseInterruptsAnExistingDrain() throws Exception {
        // 优雅关闭等待成员排空，随后明确立即关闭。
        Fixture test = new Fixture(2);
        Future<Void> draining = test.pool.drainAndClose();
        assertFalse(draining.isDone());
        assertSame(draining, test.pool.close());

        // 两条成员连接均立即结束，共享同一个完成通知。
        assertTrue(draining.isDone());
        assertTrue(test.members.stream().allMatch(member -> member.forced == 1));
        assertEquals(1, test.disconnections.get());
    }

    @Test
    void failedMemberInvalidatesPoolWithoutResendingRequests() throws Exception {
        // 保留一个已提交请求，再让任一成员异常退出。
        Fixture test = new Fixture(2);
        test.pool.write(exchange());
        test.members.get(0).listener.closed(new IOException("peer failed"));

        // 上层会话收到一次关闭，原请求不会在其他成员重发。
        assertFalse(test.pool.isOpen());
        assertEquals(1, test.disconnections.get());
        assertEquals(1, test.members.stream().mapToInt(member -> member.requests.size()).sum());
        assertTrue(test.members.stream().allMatch(member -> member.closed.isDone()));
    }

    @Test
    void partialInitializationFailureClosesAlreadyCreatedMembers() {
        // 第一个连接就绪，第二个连接失败，池不能暴露半就绪状态。
        HttpClientPoolChannel pool = pool(2);
        AtomicReference<Member> first = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        IOException failure = new IOException("connect failed");
        Future<Void> ready = pool.initialize(channel -> receiver(), factory -> {
            if (attempts.incrementAndGet() == 1) {
                Member member = new Member();
                first.set(member);
                member.attach(factory);
                return completed();
            }
            BasicFuture<Void> failed = new BasicFuture<>();
            failed.failed(failure);
            return failed;
        });

        // 保留连接失败的原因，回收已创建成员。
        assertSame(failure, ready.getCause());
        assertFalse(pool.isOpen());
        assertTrue(first.get().closed.isDone());
    }

    @Test
    void cancelledInitializationRejectsLateMemberAttachment() throws Exception {
        // 连接尚未完成，保存异步工厂以模拟关闭后才到达的握手。
        HttpClientPoolChannel pool = pool(2);
        List<BasicFuture<Void>> connecting = new ArrayList<>();
        AtomicReference<ChannelListenerFactory<HttpExchange>> delayed = new AtomicReference<>();
        Future<Void> ready = pool.initialize(channel -> receiver(), factory -> {
            delayed.set(factory);
            BasicFuture<Void> future = new BasicFuture<>();
            connecting.add(future);
            return future;
        });
        assertTrue(ready.cancel());

        // 所有在途连接都取消，迟到连接不能被重新加入池。
        assertTrue(connecting.stream().allMatch(Future::isCancelled));
        assertThrows(IOException.class, () -> delayed.get().createListener(new Member()));
        assertFalse(pool.isOpen());
    }

    @Test
    void rejectsNonPositivePoolSize() {
        // 非法容量在连接建立前拒绝。
        assertThrows(IllegalArgumentException.class, () -> pool(0));
        assertThrows(IllegalArgumentException.class, () -> pool(-1));
    }

    @Test
    void listenerFactoryFailureDoesNotOpenConnections() {
        // 上层监听器创建失败时，不启动任何池成员。
        HttpClientPoolChannel pool = pool(2);
        IOException failure = new IOException("listener failed");
        AtomicInteger attempts = new AtomicInteger();
        Future<Void> ready = pool.initialize(channel -> {
            throw failure;
        }, factory -> {
            attempts.incrementAndGet();
            return completed();
        });
        assertSame(failure, ready.getCause());
        assertEquals(0, attempts.get());
        assertTrue(pool.close().isDone());
    }

    @Test
    void executorRejectionCompletesInitializationAndCloseFutures() {
        // 模拟端点执行器已关闭，启动与关闭都不能悬挂或向外抛出执行器异常。
        IllegalStateException failure = new IllegalStateException("executor stopped");
        InterAddress target = new InterAddress("http", "127.0.0.1", 8080);
        HttpClientPoolChannel pool = new HttpClientPoolChannel(target, task -> {
            throw failure;
        }, 2);
        Future<Void> ready = pool.initialize(channel -> receiver(), factory -> completed());
        assertSame(failure, ready.getCause());
        assertSame(failure, pool.close().getCause());
        assertFalse(pool.isOpen());
    }

    private static HttpClientPoolChannel pool(int size) {
        InterAddress target = new InterAddress("http", "127.0.0.1", 8080);
        return new HttpClientPoolChannel(target, Runnable::run, size);
    }

    private static HttpExchange exchange() {
        HttpRequest request = HttpRequest.newBuffered("POST", "/call", Map.of(), new byte[0], 1, Map.of());
        return new HttpExchange(request);
    }

    private static BasicFuture<Void> completed() {
        BasicFuture<Void> ready = new BasicFuture<>();
        ready.completed(null);
        return ready;
    }

    private static ChannelListener<HttpExchange> receiver() {
        return new ChannelListener<>() {
            public void connected() {
            }

            public void receive(HttpExchange exchange) {
            }

            public void closed(Throwable cause) {
            }
        };
    }

    private static final class Fixture {
        private final HttpClientPoolChannel pool;
        private final List<Member>          members        = new ArrayList<>();
        private final AtomicInteger         connections    = new AtomicInteger();
        private final AtomicInteger         disconnections = new AtomicInteger();

        private Fixture(int size) throws Exception {
            this.pool = pool(size);
            Future<Void> ready = this.pool.initialize(channel -> new ChannelListener<>() {
                public void connected() {
                    connections.incrementAndGet();
                }

                public void receive(HttpExchange exchange) {
                }

                public void closed(Throwable cause) {
                    disconnections.incrementAndGet();
                }
            }, factory -> {
                Member member = new Member();
                this.members.add(member);
                member.attach(factory);
                return completed();
            });
            assertNull(ready.getCause());
            assertTrue(ready.isDone());
        }
    }

    private static final class Member implements NetworkChannel<HttpExchange> {
        private final List<HttpExchange>            requests = new ArrayList<>();
        private final BasicFuture<Void>             closed   = new BasicFuture<>();
        private final BasicFuture<Void>             sent     = new BasicFuture<>();
        private       ChannelListener<HttpExchange> listener;
        private       boolean                       draining;
        private       int                           forced;

        private void attach(ChannelListenerFactory<HttpExchange> factory) {
            try {
                this.listener = factory.createListener(this);
                this.listener.connected();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        public InterAddress getLocal() {
            return null;
        }

        public InterAddress getRemote() {
            return null;
        }

        public boolean isOpen() {
            return !this.closed.isDone();
        }

        public Future<Void> write(HttpExchange message) {
            this.requests.add(message);
            return this.sent;
        }

        public void execute(Runnable task) {
            task.run();
        }

        public Future<Void> close() {
            this.forced++;
            this.finish();
            return this.closed;
        }

        public Future<Void> drainAndClose() {
            this.draining = true;
            return this.closed;
        }

        private void finish() {
            this.listener.closed(null);
            this.closed.completed(null);
        }
    }
}
