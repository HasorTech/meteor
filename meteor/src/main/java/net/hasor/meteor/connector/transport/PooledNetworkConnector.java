/*
 * Copyright 2015-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 * See the LICENSE.txt file for the full license.
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.hasor.meteor.connector.transport;
import net.hasor.cobble.concurrent.future.Future;
import net.hasor.meteor.address.InterAddress;
import net.hasor.meteor.connector.RouteTable;

/** Optional transport capability: bounded independent outbound protocol sessions per address. */
public interface PooledNetworkConnector<M> extends NetworkConnector<M> {
    /** Positive member limit; members are opened on first selection and owned by this connector. */
    int clientPoolSize();

    /** Opens one member. The endpoint supplies its independent protocol session and ordered receiver. */
    Future<Void> connectMember(String initial, InterAddress address, RouteTable<M> routes, ChannelListenerFactory<M> factory);
}
