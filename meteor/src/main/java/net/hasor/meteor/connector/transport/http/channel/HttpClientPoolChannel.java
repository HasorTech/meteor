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
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.hasor.cobble.concurrent.future.BasicFuture;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.transport.ChannelListener;
import net.hasor.meteor.connector.transport.ChannelListenerFactory;
import net.hasor.meteor.connector.transport.NetworkChannel;

/** HTTP 出站连接池；端点只观察一个逻辑通道，成员连接由池持有并统一关闭。 */
public final class HttpClientPoolChannel implements NetworkChannel<HttpExchange> {
    private final InterAddress                       remote;
    private final Executor                           executor;
    private final int                                size;
    private final List<NetworkChannel<HttpExchange>> members = new ArrayList<>();
    private final List<Future<Void>>                 opening = new ArrayList<>();
    private final BasicFuture<Void>                  ready   = new BasicFuture<>();
    private final BasicFuture<Void>                  closed  = new BasicFuture<>();
    private       ChannelListener<HttpExchange>      receiver;
    private       int                                connected;
    private       boolean                            stopping;
    private       boolean                            closing;
    private       boolean                            terminated;

    public HttpClientPoolChannel(InterAddress remote, Executor executor, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("HTTP client pool size must be positive");
        }
        this.remote = remote;
        this.executor = executor;
        this.size = size;
    }

    public synchronized Future<Void> initialize(ChannelListenerFactory<HttpExchange> factory, //
            Function<ChannelListenerFactory<HttpExchange>, Future<Void>> connector) {
        this.ready.onCancel(done -> this.close());
        try {
            this.receiver = factory.createListener(this);
            this.execute(() -> this.open(connector));
        } catch (Throwable failure) {
            this.fail(failure);
        }
        return this.ready;
    }

    private void open(Function<ChannelListenerFactory<HttpExchange>, Future<Void>> connector) {
        try {
            for (int i = 0; i < this.size; i++) {
                synchronized (this) {
                    if (this.stopping) {
                        return;
                    }
                }
                Future<Void> connecting = connector.apply(this::attachMember);
                synchronized (this) {
                    this.opening.add(connecting);
                    if (this.stopping) {
                        connecting.cancel();
                    }
                }
                connecting.onFinal(done -> {
                    if (done.isCancelled() || done.getCause() != null) {
                        Throwable failure = done.getCause();
                        if (failure == null) {
                            failure = new CancellationException("HTTP pool connection cancelled");
                        }
                        this.fail(failure);
                    }
                });
            }
        } catch (Throwable failure) {
            this.fail(failure);
        }
    }

    private synchronized ChannelListener<HttpExchange> attachMember(//
            NetworkChannel<HttpExchange> channel) throws IOException {
        if (this.stopping) {
            throw new IOException("HTTP client pool closed");
        }
        this.members.add(channel);
        return new ChannelListener<>() {
            public void connected() {
                execute(() -> memberConnected());
            }

            public void receive(HttpExchange exchange) {
                execute(() -> {
                    try {
                        receiver.receive(exchange);
                    } catch (Throwable failure) {
                        fail(failure);
                    }
                });
            }

            public void closed(Throwable cause) {
                // 成员意外终止使整个逻辑会话失效；已发送的请求不换连接重试。
                fail(cause == null ? new IOException("HTTP pool member closed") : cause);
            }
        };
    }

    private synchronized void memberConnected() {
        if (!this.stopping && ++this.connected == this.size) {
            try {
                this.receiver.connected();
                this.ready.completed(null);
            } catch (Throwable failure) {
                this.fail(failure);
            }
        }
    }

    private synchronized void fail(Throwable failure) {
        if (this.stopping) {
            return;
        }
        this.ready.failed(failure);
        this.close();
    }

    @Override
    public InterAddress getRemote() {
        return this.remote;
    }

    @Override
    public synchronized InterAddress getLocal() {
        for (NetworkChannel<HttpExchange> member : this.members) {
            InterAddress local = member.getLocal();
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    @Override
    public synchronized boolean isOpen() {
        return !this.stopping;
    }

    @Override
    public synchronized Future<Void> write(HttpExchange exchange) {
        if (this.stopping || this.connected != this.size) {
            IOException failure = new IOException("HTTP client pool is not ready or closing");
            exchange.complete(failure);
            BasicFuture<Void> sent = new BasicFuture<>();
            sent.failed(failure);
            return sent;
        }
        // 每次请求独立随机选择，取消、超时及正文生命周期仍由选中的成员负责。
        int index = ThreadLocalRandom.current().nextInt(this.members.size());
        return this.members.get(index).write(exchange);
    }

    @Override
    public void execute(Runnable task) {
        this.executor.execute(task);
    }

    @Override
    public Future<Void> close() {
        return this.stop(false);
    }

    @Override
    public Future<Void> drainAndClose() {
        return this.stop(true);
    }

    private synchronized Future<Void> stop(boolean drain) {
        if (this.stopping) {
            if (!drain && !this.closing) {
                this.closing = true;
                for (NetworkChannel<HttpExchange> member : this.members) {
                    member.close();
                }
            }
            return this.closed;
        }
        this.stopping = true;
        this.closing = !drain;
        this.ready.failed(new IOException("HTTP client pool closed before ready"));
        for (Future<Void> connecting : this.opening) {
            if (!connecting.isDone()) {
                connecting.cancel();
            }
        }
        AtomicInteger remaining = new AtomicInteger(this.members.size());
        if (this.members.isEmpty()) {
            this.finishClose();
        } else {
            for (NetworkChannel<HttpExchange> member : this.members) {
                Future<Void> completion = drain ? member.drainAndClose() : member.close();
                completion.onFinal(done -> {
                    if (remaining.decrementAndGet() == 0) {
                        this.finishClose();
                    }
                });
            }
        }
        return this.closed;
    }

    private void finishClose() {
        try {
            this.execute(() -> {
                synchronized (this) {
                    if (this.terminated) {
                        return;
                    }
                    this.terminated = true;
                }
                try {
                    if (this.receiver != null) {
                        this.receiver.closed(new IOException("HTTP client pool closed"));
                    }
                } finally {
                    this.closed.completed(null);
                }
            });
        } catch (Throwable failure) {
            this.closed.failed(failure);
        }
    }
}
