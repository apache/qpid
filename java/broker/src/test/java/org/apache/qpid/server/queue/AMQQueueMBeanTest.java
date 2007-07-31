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

import junit.framework.TestCase;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.protocol.TestMinaProtocolSession;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.txn.TransactionalContext;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.txn.MemoryTransactionManager;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.messageStore.MemoryMessageStore;

import javax.management.JMException;
import java.util.LinkedList;
import java.util.HashSet;

/**
 * Test class to test AMQQueueMBean attribtues and operations
 */
public class AMQQueueMBeanTest extends TestCase
{
    private static long MESSAGE_SIZE = 1000;
    private AMQQueue _queue;
    private AMQQueueMBean _queueMBean;
    private MessageStore _messageStore = new MemoryMessageStore();
    private TransactionManager _txm = new MemoryTransactionManager();
    private StoreContext _storeContext = new StoreContext();
    private TransactionalContext _transactionalContext = new NonTransactionalContext(_messageStore, _storeContext,
                                                                                     null,
                                                                                     new LinkedList<RequiredDeliveryException>(),
                                                                                     new HashSet<Long>());
    private VirtualHost _virtualHost;

    public void testMessageCount() throws Exception
    {
        int messageCount = 10;
        sendMessages(messageCount);
        assertTrue(_queueMBean.getMessageCount() == messageCount);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
        long queueDepth = (messageCount * MESSAGE_SIZE) >> 10;
        assertTrue(_queueMBean.getQueueDepth() == queueDepth);

        _queueMBean.deleteMessageFromTop();
        assertTrue(_queueMBean.getMessageCount() == (messageCount - 1));
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);

        _queueMBean.clearQueue();
        assertTrue(_queueMBean.getMessageCount() == 0);
        assertTrue(_queueMBean.getReceivedMessageCount() == messageCount);
    }

    public void testConsumerCount() throws AMQException
    {
        SubscriptionManager mgr = _queue.getSubscribers();
        assertFalse(mgr.hasActiveSubscribers());
        assertTrue(_queueMBean.getActiveConsumerCount() == 0);


        TestMinaProtocolSession protocolSession = new TestMinaProtocolSession();
        AMQChannel channel = new AMQChannel(protocolSession, 1,_txm, _messageStore, null);
        protocolSession.addChannel(channel);

        _queue.registerProtocolSession(protocolSession, 1, new AMQShortString("test"), false, null,false,false);
        assertTrue(_queueMBean.getActiveConsumerCount() == 1);

        SubscriptionSet _subscribers = (SubscriptionSet) mgr;
        SubscriptionFactory subscriptionFactory = new SubscriptionImpl.Factory();
        Subscription s1 =  subscriptionFactory.createSubscription(channel.getChannelId(),
                                                                  protocolSession,
                                                                  new AMQShortString("S1"),
                                                                  false,
                                                                  null,
                                                                  true,
                                                                  _queue);

        Subscription s2 =  subscriptionFactory.createSubscription(channel.getChannelId(),
                                                                  protocolSession,
                                                                  new AMQShortString("S2"),
                                                                  false,
                                                                  null,
                                                                  true,
                                                                  _queue);
        _subscribers.addSubscriber(s1);
        _subscribers.addSubscriber(s2);
        assertTrue(_queueMBean.getActiveConsumerCount() == 3);
        assertTrue(_queueMBean.getConsumerCount() == 3);

        s1.close();
        assertTrue(_queueMBean.getActiveConsumerCount() == 2);
        assertTrue(_queueMBean.getConsumerCount() == 3);
    }

    public void testGeneralProperties()
    {
        long maxQueueDepth = 1000; // in bytes
        _queueMBean.setMaximumMessageCount(50000l);
        _queueMBean.setMaximumMessageSize(2000l);
        _queueMBean.setMaximumQueueDepth(maxQueueDepth);

        assertTrue(_queueMBean.getMaximumMessageCount() == 50000);
        assertTrue(_queueMBean.getMaximumMessageSize() == 2000);
        assertTrue(_queueMBean.getMaximumQueueDepth() == (maxQueueDepth >> 10));

        assertTrue(_queueMBean.getName().equals("testQueue"));
        assertTrue(_queueMBean.getOwner().equals("AMQueueMBeanTest"));
        assertFalse(_queueMBean.isAutoDelete());
        assertFalse(_queueMBean.isDurable());
    }

    public void testExceptions() throws Exception
    {
    	/*
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
        _queue.clearQueue(_storeContext);

        msg.enqueue(_queue);
        msg.routingComplete(_messageStore, _storeContext, new MessageHandleFactory());
        _queue.process(_storeContext, msg, false);
        _queueMBean.viewMessageContent(id);
        try
        {
            _queueMBean.viewMessageContent(id + 1);
            fail();
        }
        catch (JMException ex)
        {

        }*/
    }

    private AMQMessage message(final boolean immediate) throws AMQException
    {
        MessagePublishInfo publish = new MessagePublishInfo()
        {

            public AMQShortString getExchange()
            {
                return null;
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
        return new AMQMessage(_messageStore.getNewMessageId(), publish, _transactionalContext, contentHeaderBody);
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        IApplicationRegistry applicationRegistry = ApplicationRegistry.getInstance();
        _virtualHost = applicationRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _queue = new AMQQueue(new AMQShortString("testQueue"), false, new AMQShortString("AMQueueMBeanTest"), false, _virtualHost);
        _queueMBean = new AMQQueueMBean(_queue);
    }

    private void sendMessages(int messageCount) throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[messageCount];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message(false);
            messages[i].enqueue(_queue);
            messages[i].routingComplete(_messageStore, _storeContext, new MessageHandleFactory());
        }
        for (int i = 0; i < messageCount; i++)
        {
            _queue.process(_storeContext, messages[i], false);
        }
    }
}
