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
package org.apache.qpid.server.store.derby;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreQuotaEventsTestBase;
import org.apache.qpid.server.virtualhost.derby.DerbyVirtualHost;

public class DerbyMessageStoreQuotaEventsTest extends MessageStoreQuotaEventsTestBase
{
    private static final int NUMBER_OF_MESSAGES_TO_OVERFILL_STORE = 10;

    /**
     * Estimated using an assumption that a physical disk space occupied by a
     * message is 3 times bigger then a message size
     */
    private static final long OVERFULL_SIZE = (long) (MESSAGE_DATA.length * 3 * NUMBER_OF_MESSAGES_TO_OVERFILL_STORE * 0.8);

    private static final long UNDERFULL_SIZE = (long) (OVERFULL_SIZE * 0.8);

    @Override
    protected int getNumberOfMessagesToFillStore()
    {
        return NUMBER_OF_MESSAGES_TO_OVERFILL_STORE;
    }

    @Override
    protected VirtualHost createVirtualHost(String storeLocation)
    {
        final DerbyVirtualHost parent = mock(DerbyVirtualHost.class);
        when(parent.getContext()).thenReturn(createContextSettings());
        when(parent.getContextKeys(false)).thenReturn(Collections.emptySet());
        when(parent.getStorePath()).thenReturn(storeLocation);
        when(parent.getStoreOverfullSize()).thenReturn(OVERFULL_SIZE);
        when(parent.getStoreUnderfullSize()).thenReturn(UNDERFULL_SIZE);
        return parent;
    }

    @Override
    protected MessageStore createStore() throws Exception
    {
        return new DerbyMessageStore();
    }

    private Map<String, String> createContextSettings()
    {
        return Collections.emptyMap();
    }


}
