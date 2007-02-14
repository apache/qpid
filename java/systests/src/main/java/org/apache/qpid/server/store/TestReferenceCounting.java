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
package org.apache.qpid.server.store;

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.MessageHandleFactory;
import org.apache.qpid.server.txn.TransactionalContext;

/**
 * Tests that reference counting works correctly with AMQMessage and the message store
 */
public class TestReferenceCounting extends TestCase
{
    private TransactionalContext txnContext = null;
    
    private TestableMemoryMessageStore _store;

    private StoreContext _storeContext = new StoreContext();

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = new TestableMemoryMessageStore();
    }

    /**
     * Check that when the reference count is decremented the message removes itself from the store
     */
    public void testMessageGetsRemoved() throws AMQException
    {
        // TODO: fix hardcoded protocol version data
        AMQMessage message = new AMQMessage(_store, 
            new MessageTransferBody((byte)0,
                                    (byte)9,
                                    MessageTransferBody.getClazz((byte)0,(byte)9),
                                    MessageTransferBody.getMethod((byte)0,(byte)9),
                                    null, // AMQShortString appId
                                    null, // FieldTable applicationHeaders
                                    null, // Content body
                                    null, // AMQShortString contentEncoding
                                    null, // AMQShortString contentType
                                    null, // AMQShortString correlationId
                                    (short)0, // short deliveryMode
                                    null, // AMQShortString destination
                                    null, // AMQShortString exchange
                                    0L, // long expiration
                                    false, // boolean immediate
                                    false, // boolean mandatory
                                    null, // AMQShortString messageId
                                    (short)0, // short priority
                                    false, // boolean redelivered
                                    null, // AMQShortString replyTo
                                    null, // AMQShortString routingKey
                                    null, // byte[] securityToken
                                    0, // int ticket
                                    0L, // long timestamp
                                    null, // AMQShortString transactionId
                                    0L, // long ttl
                                    null), // AMQShortString userId
            txnContext);
        message.incrementReference();
        // we call routing complete to set up the handle
//        message.routingComplete(_store, _storeContext, new MessageHandleFactory());
        assertTrue(_store.getMessageMetaDataMap().size() == 1);
        message.decrementReference(_storeContext);
        assertTrue(_store.getMessageMetaDataMap().size() == 0);
    }

    public void testMessageRemains() throws AMQException
    {
        // TODO: fix hardcoded protocol version data
        AMQMessage message = new AMQMessage(_store, 
            new MessageTransferBody((byte)0,
                                    (byte)9,
                                    MessageTransferBody.getClazz((byte)0,(byte)9),
                                    MessageTransferBody.getMethod((byte)0,(byte)9),
                                    null, // AMQShortString appId
                                    null, // FieldTable applicationHeaders
                                    null, // Content body
                                    null, // AMQShortString contentEncoding
                                    null, // AMQShortString contentType
                                    null, // AMQShortString correlationId
                                    (short)0, // short deliveryMode
                                    null, // AMQShortString destination
                                    null, // AMQShortString exchange
                                    0L, // long expiration
                                    false, // boolean immediate
                                    false, // boolean mandatory
                                    null, // AMQShortString messageId
                                    (short)0, // short priority
                                    false, // boolean redelivered
                                    null, // AMQShortString replyTo
                                    null, // AMQShortString routingKey
                                    null, // byte[] securityToken
                                    0, // int ticket
                                    0L, // long timestamp
                                    null, // AMQShortString transactionId
                                    0L, // long ttl
                                    null), // AMQShortString userId
            txnContext);
        message.incrementReference();
        // we call routing complete to set up the handle
//        message.routingComplete(_store, _storeContext, new MessageHandleFactory());
        assertTrue(_store.getMessageMetaDataMap().size() == 1);
        message.incrementReference();
        message.decrementReference(_storeContext);
        assertTrue(_store.getMessageMetaDataMap().size() == 1);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TestReferenceCounting.class);
    }
}
