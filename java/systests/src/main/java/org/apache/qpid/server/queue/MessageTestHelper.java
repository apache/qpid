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

import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.TestApplicationRegistry;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.AMQException;

import junit.framework.TestCase;

import java.util.LinkedList;
import java.util.HashSet;

class MessageTestHelper extends TestCase
{
    private final MessageStore _messageStore = new SkeletonMessageStore();

    private final StoreContext _storeContext = new StoreContext();

    private final TransactionalContext _txnContext = new NonTransactionalContext(_messageStore, _storeContext, null,
                                                                                 new LinkedList<RequiredDeliveryException>(),
                                                                                 new HashSet<Long>());

    MessageTestHelper() throws Exception
    {
        ApplicationRegistry.initialise(new TestApplicationRegistry());
    }

    AMQMessage message() throws AMQException
    {
        return message(false);
    }

    AMQMessage message(boolean immediate) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Establish some way to determine the version for the test.
        BasicPublishBody publish = new BasicPublishBody((byte)8,
                                                        (byte)0,
                                                        null,
                                                        immediate,
                                                        false,
                                                        null,
                                                        0);
        
        return new AMQMessage(_messageStore.getNewMessageId(), publish, _txnContext,
                              new ContentHeaderBody());
    }

}
