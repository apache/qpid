/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import java.util.ArrayList;

import javax.management.JMException;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.MessageHeaders;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;

/**
 * Test class to test AMQQueueMBean attribtues and operations
 */
public class AMQQueueMBeanTest extends TestCase
{
    private AMQQueue _queue;
    private AMQQueueMBean _queueMBean;
    private QueueRegistry _queueRegistry;
    private MessageStore _messageStore = new SkeletonMessageStore();
    private MockProtocolSession _protocolSession;
    private AMQChannel _channel;

    public void testMessageCount() throws Exception
    {
        int messageCount = 10;
        sendMessages(messageCount);
        assertTrue(_queueMBean.getMessageCount() == messageCount);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        assertTrue(_queueMBean.getQueueDepth() == 10);

        _queueMBean.deleteMessageFromTop();
        assertTrue(_queueMBean.getMessageCount() == messageCount - 1);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        _queueMBean.clearQueue();
        assertTrue(_queueMBean.getMessageCount() == 0);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
    }

    public void testConsumerCount() throws Exception
    {
        SubscriptionManager mgr = _queue.getSubscribers();
        assertFalse(mgr.hasActiveSubscribers());
        assertTrue(_queueMBean.getActiveConsumerCount() == 0);
        
        _protocolSession = new MockProtocolSession(_messageStore);        
        _channel = new AMQChannel(1,_protocolSession, _messageStore, null,null);
        _protocolSession.addChannel(_channel);
        
        _queue.registerProtocolSession(_protocolSession, 1, "test", false, null, false, false);
        assertTrue(_queueMBean.getActiveConsumerCount() == 1);

        SubscriptionSet _subscribers = (SubscriptionSet) mgr;
        SubscriptionTestHelper s1 = new SubscriptionTestHelper("S1");
        SubscriptionTestHelper s2 = new SubscriptionTestHelper("S2");
        _subscribers.addSubscriber(s1);
        _subscribers.addSubscriber(s2);
        assertTrue(_queueMBean.getActiveConsumerCount() == 3);
        assertTrue(_queueMBean.getConsumerCount() == 3);

        s1.setSuspended(true);
        assertTrue(_queueMBean.getActiveConsumerCount() == 2);
        assertTrue(_queueMBean.getConsumerCount() == 3);
    }

    public void testGeneralProperties()
    {
        _queueMBean.setMaximumMessageCount(50000);
        _queueMBean.setMaximumMessageSize(2000l);
        _queueMBean.setMaximumQueueDepth(1000l);

        assertTrue(_queueMBean.getMaximumMessageCount() == 50000);
        assertTrue(_queueMBean.getMaximumMessageSize() == 2000);
        assertTrue(_queueMBean.getMaximumQueueDepth() == 1000);

        assertTrue(_queueMBean.getName().equals("testQueue"));
        assertTrue(_queueMBean.getOwner().equals("AMQueueMBeanTest"));
        assertFalse(_queueMBean.isAutoDelete());
        assertFalse(_queueMBean.isDurable());
    }

    public void testExceptions() throws Exception
    {
        try
        {
            _queueMBean.viewMessages(0, 3);
            fail();
        }
        catch (JMException ex)
        {

        }

        try
        {
            _queueMBean.viewMessages(2, 1);
            fail();
        }
        catch (JMException ex)
        {

        }

        try
        {
            _queueMBean.viewMessages(-1, 1);
            fail();
        }
        catch (JMException ex)
        {

        }

        AMQMessage msg = message(false);
        long id = msg.getMessageId();
        _queue.clearQueue();
        _queue.deliver(msg);
        _queueMBean.viewMessageContent(id);
        try
        {
            _queueMBean.viewMessageContent(id + 1);
            fail();
        }
        catch (JMException ex)
        {

        }
    }

    private AMQMessage message(boolean immediate) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Establish some way to determine the version for the test.
                
        MessageHeaders messageHeaders = new MessageHeaders();
    	
    	MessageTransferBody methodBody = MessageTransferBody.createMethodBody(
            (byte)0, (byte)9,               // AMQP version (major, minor)
            messageHeaders.getAppId(),      // String appId
            messageHeaders.getJMSHeaders(), // FieldTable applicationHeaders
            new Content(),                        // Content body
            messageHeaders.getEncoding(),   // String contentEncoding
            messageHeaders.getContentType(), // String contentType
            messageHeaders.getCorrelationId(), // String correlationId
            (short)1,  // short deliveryMode
            "someExchange",                  // String destination
            "someExchange",                  // String exchange
            messageHeaders.getExpiration(), // long expiration
            immediate,                          // boolean immediate
            "",                         // String messageId
            (short)0,                       // short priority
            false,                          // boolean redelivered
            messageHeaders.getReplyTo(),    // String replyTo
            "rk",                           // String routingKey
            new String("abc123").getBytes(), // byte[] securityToken
            0,                              // int ticket
            messageHeaders.getTimestamp(),  // long timestamp
            messageHeaders.getTransactionId(), // String transactionId
            0,                              // long ttl
            messageHeaders.getUserId());    // String userId
    	
    	return new AMQMessage(_messageStore, methodBody, new ArrayList()); 
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _queueRegistry = new DefaultQueueRegistry();
        _queue = new AMQQueue("testQueue", false, "AMQueueMBeanTest", false, _queueRegistry);
        _queueMBean = new AMQQueueMBean(_queue);
    }

    private void sendMessages(int messageCount) throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[messageCount];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message(false);
            ;
        }
        for (int i = 0; i < messageCount; i++)
        {
            _queue.deliver(messages[i]);
        }
    }
}
