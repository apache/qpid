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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.framing.amqp_8_0.BasicConsumeBodyImpl;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PersistentMessageTest extends TransientMessageTest
{
    private TestableMemoryMessageStore _messageStore;

    protected SimpleAMQQueue _queue;
    protected AMQShortString _q1name = new AMQShortString("q1name");
    protected AMQShortString _owner = new AMQShortString("owner");
    protected AMQShortString _routingKey = new AMQShortString("routing key");
    private TransactionalContext _messageDeliveryContext;
    private static final long MESSAGE_SIZE = 0L;
    private List<RequiredDeliveryException> _returnMessages = new LinkedList<RequiredDeliveryException>();

    public void setUp() throws Exception
    {
        _messageStore = new TestableMemoryMessageStore();

        _storeContext = new StoreContext();
        VirtualHost vhost = new VirtualHost(PersistentMessageTest.class.getName(), _messageStore);
        _queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(_q1name, false, _owner, false, vhost, null);
        // Create IncomingMessage and nondurable queue
        _messageDeliveryContext = new NonTransactionalContext(_messageStore, new StoreContext(), null, _returnMessages);

    }

    @Override
    protected AMQMessage newMessage()
    {
        return MessageFactory.getInstance().createMessage(_messageStore, true);
    }

    @Override
    public void testIsPersistent()
    {
        _message = newMessage();
        assertTrue(_message.isPersistent());
    }

    /**
     * Tests the returning of a single persistent message to a queue. An immediate message is sent to the queue and
     * checked that it bounced. The transactionlog and returnMessasges are then checked to ensure they have the right
     * contents. TransactionLog = Empty, returnMessages 1 item. 
     *
     * @throws Exception
     */
    public void testImmediateReturnNotInLog() throws Exception
    {
        MessagePublishInfo info = new MessagePublishInfoImpl(null, true, false, null);
        IncomingMessage msg = createMessage(info);

        // Send persistent message
        ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
        qs.add(_queue);

        // equivalent to amqChannel.routeMessage()
        msg.enqueue(qs);

        msg.routingComplete(_messageStore);

        // equivalent to amqChannel.deliverCurrentMessageIfComplete
        msg.deliverToQueues();

        // Check that data has been stored to disk
        long messageId = msg.getMessageId();
        checkMessageMetaDataExists(messageId);

        // Check that it was not enqueued
        List<AMQQueue> queueList = _messageStore.getMessageReferenceMap(messageId);
        assertNull("TransactionLog contains a queue reference for this messageID:" + messageId, queueList);
        checkMessageMetaDataRemoved(messageId);

        assertEquals("Return message count not correct", 1, _returnMessages.size());
    }

    protected IncomingMessage createMessage(MessagePublishInfo info) throws AMQException
    {
        IncomingMessage msg = new IncomingMessage(info, _messageDeliveryContext,
                                                  new MockProtocolSession(_messageStore), _messageStore);

        // equivalent to amqChannel.publishContenHeader
        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.classId = BasicConsumeBodyImpl.CLASS_ID;
        // This message has no bodies
        contentHeaderBody.bodySize = MESSAGE_SIZE;
        contentHeaderBody.properties = new BasicContentHeaderProperties();
        ((BasicContentHeaderProperties) contentHeaderBody.properties).setDeliveryMode((byte) 2);

        msg.setContentHeaderBody(contentHeaderBody);
        msg.setExpiration();

        return msg;
    }

    protected void checkMessageMetaDataExists(long messageId)
    {
        try
        {
            _messageStore.getMessageMetaData(_messageDeliveryContext.getStoreContext(), messageId);
        }
        catch (AMQException amqe)
        {
            fail("Message MetaData does not exist for message:" + messageId);
        }
    }

    protected void checkMessageMetaDataRemoved(long messageId)
    {
        try
        {
            assertNull("Message MetaData still exists for message:" + messageId,
                       _messageStore.getMessageMetaData(_messageDeliveryContext.getStoreContext(), messageId));
            assertNull("Message still has values in the reference map:" + messageId,
                       _messageStore.getMessageReferenceMap(messageId));

        }
        catch (AMQException e)
        {
            fail("AMQE thrown whilst trying to getMessageMetaData:" + e.getMessage());
        }
    }
}
