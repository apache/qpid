/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.virtualhost.berkeleydb;

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.DerivedAttribute;
import org.apache.qpid.server.model.ManagedAttribute;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public interface BDBHAVirtualHost<X extends BDBHAVirtualHost<X>> extends VirtualHostImpl<X, AMQQueue<?>, ExchangeImpl<?>>
{
    String REMOTE_TRANSACTION_SYNCHRONIZATION_POLICY = "remoteTransactionSynchronizationPolicy";
    String LOCAL_TRANSACTION_SYNCHRONIZATION_POLICY = "localTransactionSynchronizationPolicy";
    String COALESCING_SYNC = "coalescingSync";
    String DURABILITY = "durability";

    @ManagedAttribute( defaultValue = "SYNC")
    String getLocalTransactionSynchronizationPolicy();

    @ManagedAttribute( defaultValue = "NO_SYNC")
    String getRemoteTransactionSynchronizationPolicy();

    @DerivedAttribute
    boolean isCoalescingSync();

    @DerivedAttribute
    String getDurability();
}
