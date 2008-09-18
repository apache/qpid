package org.apache.qpid.server.queue;
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


import java.util.List;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.exchange.DirectExchange;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.store.TestableMemoryMessageStore;
import org.apache.qpid.server.subscription.MockSubscription;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.subscription.SubscriptionImpl;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class SimpleAMQQueueTest extends TestCase
{

    protected SimpleAMQQueue queue;
    protected VirtualHost virtualHost;
    protected MessageStore store = new TestableMemoryMessageStore();
    protected AMQShortString qname = new AMQShortString("qname");
    protected AMQShortString owner = new AMQShortString("owner");
    protected AMQShortString routingKey = new AMQShortString("routing key");
    protected DirectExchange exchange = new DirectExchange();
    protected MockSubscription subscription = new MockSubscription();
    protected FieldTable arguments = null;
    
    MessagePublishInfo info = new MessagePublishInfo()
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
            return false;
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
    
    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        //Create Application Registry for test
        ApplicationRegistry applicationRegistry = (ApplicationRegistry)ApplicationRegistry.getInstance(1);

        virtualHost = new VirtualHost("vhost", store);
        applicationRegistry.getVirtualHostRegistry().registerVirtualHost(virtualHost);

        queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(qname, false, owner, false, virtualHost, arguments);
    }

    @Override
    protected void tearDown()
    {
        queue.stop();
        ApplicationRegistry.remove(1);
    }

    public void testCreateQueue() throws AMQException
    {
        queue.stop();
        try {
            queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(null, false, owner, false, virtualHost, arguments );
            assertNull("Queue was created", queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing name", 
                            e.getMessage().contains("name"));
        }
        
        try {
            queue = new SimpleAMQQueue(qname, false, owner, false, null);
            assertNull("Queue was created", queue);
        }
        catch (IllegalArgumentException e)
        {
            assertTrue("Exception was not about missing vhost", 
                    e.getMessage().contains("Host"));
        }

        queue = (SimpleAMQQueue) AMQQueueFactory.createAMQQueueImpl(qname, false, owner, false, 
                                                                virtualHost, arguments);
        assertNotNull("Queue was not created", queue);
    }
    
    public void testGetVirtualHost()
    {
        assertEquals("Virtual host was wrong", virtualHost, queue.getVirtualHost());
    }
    
    public void testBinding()
    {
        try
        {
            queue.bind(exchange, routingKey, null);
            assertTrue("Routing key was not bound", 
                            exchange.getBindings().containsKey(routingKey));
            assertEquals("Queue was not bound to key", 
                        exchange.getBindings().get(routingKey).get(0),
                        queue);
            assertEquals("Exchange binding count", 1, 
                    queue.getExchangeBindings().size());
            assertEquals("Wrong exchange bound", routingKey, 
                    queue.getExchangeBindings().get(0).getRoutingKey());
            assertEquals("Wrong exchange bound", exchange, 
                    queue.getExchangeBindings().get(0).getExchange());
            
            queue.unBind(exchange, routingKey, null);
            assertFalse("Routing key was still bound", 
                    exchange.getBindings().containsKey(routingKey));
            assertNull("Routing key was not empty", 
                    exchange.getBindings().get(routingKey));
        }
        catch (AMQException e)
        {
            assertNull("Unexpected exception", e);
        }
    }
    
    public void testSubscription() throws AMQException
    {
        // Check adding a subscription adds it to the queue
        queue.registerSubscription(subscription, false);
        assertEquals("Subscription did not get queue", queue, 
                      subscription.getQueue());
        assertEquals("Queue does not have consumer", 1, 
                     queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1, 
                queue.getActiveConsumerCount());
        
        // Check sending a message ends up with the subscriber
        AMQMessage messageA = createMessage(new Long(24));
        queue.enqueue(null, messageA);
        assertEquals(messageA, subscription.getLastSeenEntry().getMessage());
        
        // Check removing the subscription removes it's information from the queue
        queue.unregisterSubscription(subscription);
        assertTrue("Subscription still had queue", subscription.isClosed());
        assertFalse("Queue still has consumer", 1 == queue.getConsumerCount());
        assertFalse("Queue still has active consumer", 
                1 == queue.getActiveConsumerCount());
        
        AMQMessage messageB = createMessage(new Long (25));
        queue.enqueue(null, messageB);
        QueueEntry entry = subscription.getLastSeenEntry();
        assertNull(entry);
    }
    
    public void testQueueNoSubscriber() throws AMQException, InterruptedException
    {
        AMQMessage messageA = createMessage(new Long(24));
        queue.enqueue(null, messageA);
        queue.registerSubscription(subscription, false);
        Thread.sleep(150);
        assertEquals(messageA, subscription.getLastSeenEntry().getMessage());
    }

    public void testExclusiveConsumer() throws AMQException
    {
        // Check adding an exclusive subscription adds it to the queue
        queue.registerSubscription(subscription, true);
        assertEquals("Subscription did not get queue", queue, 
                subscription.getQueue());
        assertEquals("Queue does not have consumer", 1, 
                queue.getConsumerCount());
        assertEquals("Queue does not have active consumer", 1, 
                queue.getActiveConsumerCount());

        // Check sending a message ends up with the subscriber
        AMQMessage messageA = createMessage(new Long(24));
        queue.enqueue(null, messageA);
        assertEquals(messageA, subscription.getLastSeenEntry().getMessage());
        
        // Check we cannot add a second subscriber to the queue
        Subscription subB = new MockSubscription();
        Exception ex = null;
        try
        {
            queue.registerSubscription(subB, false);
        }
        catch (AMQException e)
        {
           ex = e; 
        }
        assertNotNull(ex);
        assertTrue(ex instanceof AMQException);

        // Check we cannot add an exclusive subscriber to a queue with an 
        // existing subscription
        queue.unregisterSubscription(subscription);
        queue.registerSubscription(subscription, false);
        try
        {
            queue.registerSubscription(subB, true);
        }
        catch (AMQException e)
        {
           ex = e; 
        }
        assertNotNull(ex);
    }
    
    public void testAutoDeleteQueue() throws Exception 
    {
       queue.stop();
       queue = new SimpleAMQQueue(qname, false, owner, true, virtualHost);
       queue.registerSubscription(subscription, false);
       AMQMessage message = createMessage(new Long(25));
       queue.enqueue(null, message);
       queue.unregisterSubscription(subscription);
       assertTrue("Queue was not deleted when subscription was removed",
                  queue.isDeleted());
    }
    
    public void testResend() throws Exception
    {
        queue.registerSubscription(subscription, false);
        Long id = new Long(26);
        AMQMessage message = createMessage(id);
        queue.enqueue(null, message);
        QueueEntry entry = subscription.getLastSeenEntry();
        entry.setRedelivered(true);
        queue.resend(entry, subscription);
        
    }
    
    public void testGetFirstMessageId() throws Exception
    {
        // Create message
        Long messageId = new Long(23);
        AMQMessage message = createMessage(messageId);

        // Put message on queue
        queue.enqueue(null, message);
        // Get message id
        Long testmsgid = queue.getMessagesOnTheQueue(1).get(0);

        // Check message id
        assertEquals("Message ID was wrong", messageId, testmsgid);
    }

    public void testGetFirstFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 5; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = createMessage(messageId);
            // Put message on queue
            queue.enqueue(null, message);
        }
        // Get message ids
        List<Long> msgids = queue.getMessagesOnTheQueue(5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }

    public void testGetLastFiveMessageIds() throws Exception
    {
        for (int i = 0 ; i < 10; i++)
        {
            // Create message
            Long messageId = new Long(i);
            AMQMessage message = createMessage(messageId);
            // Put message on queue
            queue.enqueue(null, message);
        }
        // Get message ids
        List<Long> msgids = queue.getMessagesOnTheQueue(5, 5);

        // Check message id
        for (int i = 0; i < 5; i++)
        {
            Long messageId = new Long(i+5);
            assertEquals("Message ID was wrong", messageId, msgids.get(i));
        }
    }


    // FIXME: move this to somewhere useful
    private static AMQMessageHandle createMessageHandle(final long messageId, final MessagePublishInfo publishBody)
    {
        final AMQMessageHandle amqMessageHandle = (new MessageHandleFactory()).createMessageHandle(messageId,
                                                                                                   null,
                                                                                                   false);
        try
        {
            amqMessageHandle.setPublishAndContentHeaderBody(new StoreContext(),
                                                              publishBody,
                                                              new ContentHeaderBody()
            {
                public int getSize()
                {
                    return 1;
                }
            });
        }
        catch (AMQException e)
        {
            // won't happen
        }


        return amqMessageHandle;
    }

    public class TestMessage extends AMQMessage
    {
        private final long _tag;
        private int _count;

        TestMessage(long tag, long messageId, MessagePublishInfo publishBody, StoreContext storeContext)
                throws AMQException
        {
            super(createMessageHandle(messageId, publishBody), storeContext, publishBody);
            _tag = tag;
        }


        public boolean incrementReference()
        {
            _count++;
            return true;
        }

        public void decrementReference(StoreContext context)
        {
            _count--;
        }

        void assertCountEquals(int expected)
        {
            assertEquals("Wrong count for message with tag " + _tag, expected, _count);
        }
    }
    
    protected AMQMessage createMessage(Long id) throws AMQException
    {
        AMQMessage messageA = new TestMessage(id, id, info, new StoreContext());
        return messageA;
    }
}
