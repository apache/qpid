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
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.util.InternalBrokerBaseCase;
import org.apache.qpid.server.message.AMQMessage;
import org.apache.qpid.server.message.MessageMetaData;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionFactory;
import org.apache.qpid.server.subscription.SubscriptionFactoryImpl;
import org.apache.qpid.server.protocol.InternalTestProtocolSession;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.mina.common.ByteBuffer;

import javax.management.JMException;

import java.util.ArrayList;

/**
 * Test class to test AMQQueueMBean attribtues and operations
 */
public class AMQQueueMBeanTest extends InternalBrokerBaseCase
{
    private static long MESSAGE_SIZE = 1000;
    private AMQQueueMBean _queueMBean;
    private static final SubscriptionFactoryImpl SUBSCRIPTION_FACTORY = SubscriptionFactoryImpl.INSTANCE;

    public void testMessageCountTransient() throws Exception
    {
        int messageCount = 10;
        sendMessages(messageCount, false);
        assertTrue(_queueMBean.getMessageCount() == messageCount);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        long queueDepth = (messageCount * MESSAGE_SIZE);
        assertTrue(_queueMBean.getQueueDepth() == queueDepth);

        _queueMBean.deleteMessageFromTop();
        assertTrue(_queueMBean.getMessageCount() == (messageCount - 1));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        _queueMBean.clearQueue();
        assertEquals(0,(int)_queueMBean.getMessageCount());
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        //Ensure that the data has been removed from the Store
        verifyBrokerState();
    }

    public void testMessageCountPersistent() throws Exception
    {
        int messageCount = 10;
        sendMessages(messageCount, true);
        assertEquals("", messageCount, _queueMBean.getMessageCount().intValue());
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        long queueDepth = (messageCount * MESSAGE_SIZE);
        assertTrue(_queueMBean.getQueueDepth() == queueDepth);

        _queueMBean.deleteMessageFromTop();
        assertTrue(_queueMBean.getMessageCount() == (messageCount - 1));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        _queueMBean.clearQueue();
        assertTrue(_queueMBean.getMessageCount() == 0);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        //Ensure that the data has been removed from the Store
        verifyBrokerState();
    }

    public void testDeleteMessages() throws Exception
    {
        int messageCount = 10;
        sendMessages(messageCount, true);
        assertEquals("", messageCount, _queueMBean.getMessageCount().intValue());
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        long queueDepth = (messageCount * MESSAGE_SIZE);
        assertTrue(_queueMBean.getQueueDepth() == queueDepth);

        //delete first message
        _queueMBean.deleteMessages(1L,1L);
        assertTrue(_queueMBean.getMessageCount() == (messageCount - 1));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        try
        {
            _queueMBean.viewMessageContent(1L);
            fail("Message should no longer be on the queue");
        }
        catch(Exception e)
        {

        }

        //delete last message, leaving 2nd to 9th
        _queueMBean.deleteMessages(10L,10L);
        assertTrue(_queueMBean.getMessageCount() == (messageCount - 2));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        try
        {
            _queueMBean.viewMessageContent(10L);
            fail("Message should no longer be on the queue");
        }
        catch(Exception e)
        {

        }

        //delete remaining messages, leaving none
        _queueMBean.deleteMessages(2L,9L);
        assertTrue(_queueMBean.getMessageCount() == (0));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        //Ensure that the data has been removed from the Store
        verifyBrokerState();
    }

    // todo: collect to a general testing class -duplicated from Systest/MessageReturntest
    private void verifyBrokerState()
    {

        TestableMemoryMessageStore store = (TestableMemoryMessageStore) getVirtualHost().getMessageStore();

        // Unlike MessageReturnTest there is no need for a delay as there this thread does the clean up.

        assertEquals("Store should have no messages:" + store.getMessageCount(), 0, store.getMessageCount());
    }

    public void testConsumerCount() throws AMQException
    {

        assertTrue(getQueue().getActiveConsumerCount() == 0);
        assertTrue(_queueMBean.getActiveConsumerCount() == 0);


        InternalTestProtocolSession protocolSession = new InternalTestProtocolSession(getVirtualHost());

        AMQChannel channel = new AMQChannel(protocolSession, 1, getMessageStore());
        protocolSession.addChannel(channel);

        Subscription subscription =
                SUBSCRIPTION_FACTORY.createSubscription(channel.getChannelId(), protocolSession, new AMQShortString("test"), false, null, false, channel.getCreditManager());

        getQueue().registerSubscription(subscription, false);
        assertEquals(1,(int)_queueMBean.getActiveConsumerCount());


        SubscriptionFactory subscriptionFactory = SUBSCRIPTION_FACTORY;
        Subscription s1 = subscriptionFactory.createSubscription(channel.getChannelId(),
                                                                 protocolSession,
                                                                 new AMQShortString("S1"),
                                                                 false,
                                                                 null,
                                                                 true,
                channel.getCreditManager());

        Subscription s2 = subscriptionFactory.createSubscription(channel.getChannelId(),
                                                                 protocolSession,
                                                                 new AMQShortString("S2"),
                                                                 false,
                                                                 null,
                                                                 true,
                channel.getCreditManager());
        getQueue().registerSubscription(s1,false);
        getQueue().registerSubscription(s2,false);
        assertTrue(_queueMBean.getActiveConsumerCount() == 3);
        assertTrue(_queueMBean.getConsumerCount() == 3);

        s1.close();
        assertEquals(2, (int) _queueMBean.getActiveConsumerCount());
        assertTrue(_queueMBean.getConsumerCount() == 3);
    }

    public void testGeneralProperties() throws Exception
    {
        long maxQueueDepth = 1000; // in bytes
        _queueMBean.setMaximumMessageCount(50000l);
        _queueMBean.setMaximumMessageSize(2000l);
        _queueMBean.setMaximumQueueDepth(maxQueueDepth);

        assertEquals("Max MessageCount not set",50000,_queueMBean.getMaximumMessageCount().longValue());
        assertEquals("Max MessageSize not set",2000, _queueMBean.getMaximumMessageSize().longValue());
        assertEquals("Max QueueDepth not set",maxQueueDepth, _queueMBean.getMaximumQueueDepth().longValue());

        assertEquals("Queue Name does not match", new AMQShortString(getName()), _queueMBean.getName());
        assertFalse("AutoDelete should not be set.",_queueMBean.isAutoDelete());
        assertFalse("Queue should not be durable.",_queueMBean.isDurable());
        
        //set+get exclusivity using the mbean, and also verify it is actually updated in the queue
        _queueMBean.setExclusive(true);
        assertTrue("Exclusive property should be true.",_queueMBean.isExclusive());
        assertTrue("Exclusive property should be true.", getQueue().isExclusive());
        _queueMBean.setExclusive(false);
        assertFalse("Exclusive property should be false.",_queueMBean.isExclusive());
        assertFalse("Exclusive property should be false.", getQueue().isExclusive());
    }

    public void testExceptions() throws Exception
    {
        try
        {
            _queueMBean.viewMessages(0L, 3L);
            fail();
        }
        catch (JMException ex)
        {

        }

        try
        {
            _queueMBean.viewMessages(2L, 1L);
            fail();
        }
        catch (JMException ex)
        {

        }

        try
        {
            _queueMBean.viewMessages(-1L, 1L);
            fail();
        }
        catch (JMException ex)
        {

        }

        try
        {
            long end = Integer.MAX_VALUE;
            end+=2;
            _queueMBean.viewMessages(1L, end);
            fail("Expected Exception due to oversized(> 2^31) message range");
        }
        catch (JMException ex)
        {

        }

        IncomingMessage msg = message(false, false);
        getQueue().clearQueue();
        ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
        qs.add(getQueue());
        msg.enqueue(qs);
        MessageMetaData mmd = msg.headersReceived();
        msg.setStoredMessage(getMessageStore().addMessage(mmd));
        long id = msg.getMessageNumber();

        msg.addContentBodyFrame(new ContentChunk()
        {
            ByteBuffer _data = ByteBuffer.allocate((int)MESSAGE_SIZE);

            {
                _data.limit((int)MESSAGE_SIZE);
            }

            public int getSize()
            {
                return (int) MESSAGE_SIZE;
            }

            public ByteBuffer getData()
            {
                return _data;
            }

            public void reduceToFit()
            {

            }
        });

        AMQMessage m = new AMQMessage(msg.getStoredMessage());
        for(BaseQueue q : msg.getDestinationQueues())
        {
            q.enqueue(m);
        }
//        _queue.process(_storeContext, new QueueEntry(_queue, msg), false);
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
    
    public void testFlowControlProperties() throws Exception
    {
        assertTrue(_queueMBean.getCapacity() == 0);
        assertTrue(_queueMBean.getFlowResumeCapacity() == 0);
        assertFalse(_queueMBean.isFlowOverfull());
        
        //capacity currently 0, try setting FlowResumeCapacity above this
        try
        {
            _queueMBean.setFlowResumeCapacity(1L);
            fail("Should have failed to allow setting FlowResumeCapacity above Capacity");
        }
        catch (IllegalArgumentException ex)
        {
            //expected exception
            assertTrue(_queueMBean.getFlowResumeCapacity() == 0);
        }
        
        //add a message to the queue
        sendMessages(1, true);

        //(FlowResume)Capacity currently 0, set both to 2
        _queueMBean.setCapacity(2L);
        assertTrue(_queueMBean.getCapacity() == 2L);
        _queueMBean.setFlowResumeCapacity(2L);
        assertTrue(_queueMBean.getFlowResumeCapacity() == 2L);
        
        //Try setting Capacity below FlowResumeCapacity
        try
        {
            _queueMBean.setCapacity(1L);
            fail("Should have failed to allow setting Capacity below FlowResumeCapacity");
        }
        catch (IllegalArgumentException ex)
        {
            //expected exception
            assertTrue(_queueMBean.getCapacity() == 2);
        }
        
        //create a channel and use it to exercise the capacity check mechanism
        AMQChannel channel = new AMQChannel(getSession(), 1, getMessageStore());
        getQueue().checkCapacity(channel);
        
        assertTrue(_queueMBean.isFlowOverfull());
        assertTrue(channel.getBlocking());
        
        //set FlowResumeCapacity to MESSAGE_SIZE and check queue is now underfull and channel unblocked
        _queueMBean.setCapacity(MESSAGE_SIZE);//must increase capacity too
        _queueMBean.setFlowResumeCapacity(MESSAGE_SIZE);
        
        assertFalse(_queueMBean.isFlowOverfull());
        assertFalse(channel.getBlocking());
    }

    private IncomingMessage message(final boolean immediate, boolean persistent) throws AMQException
    {
        MessagePublishInfo publish = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
            }

            public void setExchange(AMQShortString exchange)
            {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            public boolean isImmediate()
            {
                return immediate;
            }

            public boolean isMandatory()
            {
                return false;
            }

            public AMQShortString getRoutingKey()
            {
                return null;
            }
        };

        ContentHeaderBody contentHeaderBody = new ContentHeaderBody();
        contentHeaderBody.bodySize = MESSAGE_SIZE;   // in bytes
        contentHeaderBody.setProperties(new BasicContentHeaderProperties());
        ((BasicContentHeaderProperties) contentHeaderBody.getProperties()).setDeliveryMode((byte) (persistent ? 2 : 1));
        IncomingMessage msg = new IncomingMessage(publish);
        msg.setContentHeaderBody(contentHeaderBody);
        return msg;

    }

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _queueMBean = new AMQQueueMBean(getQueue());
    }

    public void tearDown()
    {
        ApplicationRegistry.remove();
    }

    private void sendMessages(int messageCount, boolean persistent) throws AMQException
    {
        for (int i = 0; i < messageCount; i++)
        {
            IncomingMessage currentMessage = message(false, persistent);
            ArrayList<AMQQueue> qs = new ArrayList<AMQQueue>();
            qs.add(getQueue());
            currentMessage.enqueue(qs);

            // route header
            MessageMetaData mmd = currentMessage.headersReceived();
            currentMessage.setStoredMessage(getMessageStore().addMessage(mmd));

            // Add the body so we have somthing to test later
            currentMessage.addContentBodyFrame(
                    getSession().getMethodRegistry()
                                                       .getProtocolVersionMethodConverter()
                                                       .convertToContentChunk(
                                                       new ContentBody(ByteBuffer.allocate((int) MESSAGE_SIZE),
                                                                       MESSAGE_SIZE)));

            AMQMessage m = new AMQMessage(currentMessage.getStoredMessage());
            for(BaseQueue q : currentMessage.getDestinationQueues())
            {
                q.enqueue(m);
            }


        }
    }
}
