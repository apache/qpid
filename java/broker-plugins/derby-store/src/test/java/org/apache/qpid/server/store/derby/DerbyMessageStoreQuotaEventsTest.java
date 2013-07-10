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

import org.apache.log4j.Logger;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreConstants;
import org.apache.qpid.server.store.MessageStoreQuotaEventsTestBase;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

public class DerbyMessageStoreQuotaEventsTest extends MessageStoreQuotaEventsTestBase
{
    private static final Logger _logger = Logger.getLogger(DerbyMessageStoreQuotaEventsTest.class);

    private static final int NUMBER_OF_MESSAGES_TO_OVERFILL_STORE = 10;

    /**
     * Estimated using an assumption that a physical disk space occupied by a
     * message is 3 times bigger then a message size
     */
    private static final int OVERFULL_SIZE = (int) (MESSAGE_DATA.length * 3 * NUMBER_OF_MESSAGES_TO_OVERFILL_STORE * 0.8);

    private static final int UNDERFULL_SIZE = (int) (OVERFULL_SIZE * 0.8);

    @Override
    protected int getNumberOfMessagesToFillStore()
    {
        return NUMBER_OF_MESSAGES_TO_OVERFILL_STORE;
    }

    @Override
    protected void applyStoreSpecificConfiguration(VirtualHost vhost)
    {
        _logger.debug("Applying store specific config. overfull-sze=" + OVERFULL_SIZE + ", underfull-size=" + UNDERFULL_SIZE);

        when(vhost.getAttribute(eq(MessageStoreConstants.OVERFULL_SIZE_ATTRIBUTE))).thenReturn(OVERFULL_SIZE);
        when(vhost.getAttribute(eq(MessageStoreConstants.UNDERFULL_SIZE_ATTRIBUTE))).thenReturn(UNDERFULL_SIZE);
    }

    @Override
    protected MessageStore createStore() throws Exception
    {
        return new DerbyMessageStore();
    }
}
