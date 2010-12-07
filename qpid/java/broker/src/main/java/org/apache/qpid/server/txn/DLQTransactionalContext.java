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
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoreContext;

/** A transactional context that only supports local transactions
 * for use in DeadLetterQueue processing */
public class DLQTransactionalContext extends LocalTransactionalContext
{
    private final StoreContext _storeContext;

    public DLQTransactionalContext(final AMQChannel channel, final StoreContext storeContext)
    {
        super(channel);
        _storeContext = storeContext;
    }

    @Override
    public void deliver(final AMQQueue queue, AMQMessage message) throws AMQException
    {
        //TODO: ensure message is not Immediate. Copy the message object if necessary.
        deliver(queue, message, true);
    }

    @Override
    public StoreContext getStoreContext()
    {
        return _storeContext;
    }
}