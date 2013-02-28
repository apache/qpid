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

import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreCreator;
import org.apache.qpid.server.store.berkeleydb.BDBHAMessageStore;
import org.apache.qpid.server.store.berkeleydb.BDBMessageStore;
import org.apache.qpid.server.store.derby.DerbyMessageStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class MessageStoreCreatorTest extends QpidTestCase
{
    private static final String[] STORE_TYPES = {MemoryMessageStore.TYPE, DerbyMessageStore.TYPE, BDBMessageStore.TYPE, BDBHAMessageStore.TYPE};

    public void testMessageStoreCreator()
    {
        MessageStoreCreator messageStoreCreator = new MessageStoreCreator();
        for (String type : STORE_TYPES)
        {
            MessageStore store = messageStoreCreator.createMessageStore(type);
            assertNotNull("Store of type " + type + " is not created", store);
        }
    }
}
