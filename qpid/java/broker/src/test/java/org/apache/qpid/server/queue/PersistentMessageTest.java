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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.StoreContext;

public class PersistentMessageTest extends TransientMessageTest
{
    private MemoryMessageStore _messageStore;

    public void setUp()
    {
        _messageStore = new MemoryMessageStore();
        _messageStore.configure();
        _storeContext = new StoreContext();
    }

    @Override
    protected AMQMessage newMessage(Long id)
    {
        return new MessageFactory().createMessage(id, _messageStore, true);
    }

    @Override
    public void testIsPersistent()
    {
        _message = newMessage(1L);
        assertTrue(_message.isPersistent());
    }

}
