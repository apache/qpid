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
package org.apache.qpid.server.store.berkeleydb;

import java.util.Collections;

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreQuotaEventsTestBase;
import org.apache.qpid.server.virtualhost.berkeleydb.BDBVirtualHost;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BDBMessageStoreQuotaEventsTest extends MessageStoreQuotaEventsTestBase
{
    /*
     * Notes on calculation of quota limits.
     *
     * 150 32kb messages is approximately 4.8MB which is greater than
     * OVERFULL_SIZE.
     *
     * We deliberately use settings that force BDB to use multiple log files, so
     * that when one or more of them are subsequently cleaned (following message
     * consumption) the actual size on disk is reduced.
     */

    private static final String MAX_BDB_LOG_SIZE = "1000000"; // ~1MB

    private static final int NUMBER_OF_MESSAGES_TO_OVERFILL_STORE = 150;

    private static final long OVERFULL_SIZE = 4000000; // ~4MB
    private static final long UNDERFULL_SIZE = 3500000; // ~3.5MB

    @Override
    protected int getNumberOfMessagesToFillStore()
    {
        return NUMBER_OF_MESSAGES_TO_OVERFILL_STORE;
    }

    @Override
    protected VirtualHost createVirtualHost(String storeLocation)
    {
        final BDBVirtualHost parent = mock(BDBVirtualHost.class);
        when(parent.getContext()).thenReturn(Collections.singletonMap("je.log.fileMax", MAX_BDB_LOG_SIZE));
        when(parent.getStorePath()).thenReturn(storeLocation);
        when(parent.getStoreOverfullSize()).thenReturn(OVERFULL_SIZE);
        when(parent.getStoreUnderfullSize()).thenReturn(UNDERFULL_SIZE);
        return parent;
    }


    @Override
    protected MessageStore createStore() throws Exception
    {
        MessageStore store = new BDBMessageStore();
        return store;
    }
}
