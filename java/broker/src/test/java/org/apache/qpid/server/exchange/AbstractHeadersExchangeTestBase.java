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
package org.apache.qpid.server.exchange;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.RequiredDeliveryException;
import org.apache.qpid.server.transactionlog.TransactionLog;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.FailedDequeueException;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.MessageCleanupException;
import org.apache.qpid.server.queue.MockProtocolSession;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.SimpleAMQQueue;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.NonTransactionalContext;
import org.apache.qpid.server.txn.TransactionalContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AbstractHeadersExchangeTestBase extends TestCase
{
    private static final Logger _log = Logger.getLogger(AbstractHeadersExchangeTestBase.class);

    private final HeadersExchange exchange = new HeadersExchange();
    protected final Set<TestQueue> queues = new HashSet<TestQueue>();

    /** Not used in this test, just there to stub out the routing calls */
    private TransactionLog _transactionLog = new MemoryMessageStore();

    private int count;

    public void testDoNothing()
    {
        // this is here only to make junit under Eclipse happy
    }

    protected TestQueue bindDefault(String... bindings) throws AMQException
    {
        return bind("Queue" + (++count), bindings);
    }

    protected TestQueue bind(String queueName, String... bindings) throws AMQException
    {
        return bind(queueName, getHeaders(bindings));
    }

    protected TestQueue bind(String queue, FieldTable bindings) throws AMQException
    {
        return bind(new TestQueue(new AMQShortString(queue)), bindings);
    }

    protected TestQueue bind(TestQueue queue, String... bindings) throws AMQException
    {
        return bind(queue, getHeaders(bindings));
    }

    protected TestQueue bind(TestQueue queue, FieldTable bindings) throws AMQException
    {
        queues.add(queue);
        exchange.registerQueue(null, queue, bindings);
        return queue;
    }

    protected void route(Message m) throws AMQException
    {
        exchange.route(m.getIncomingMessage());
        m.getIncomingMessage().routingComplete(_transactionLog);
        if (m.getIncomingMessage().allContentReceived())
        {
            m.getIncomingMessage().deliverToQueues();
        }
    }

    protected void routeAndTest(Message m, TestQueue... expected) throws AMQException
    {
        routeAndTest(m, false, Arrays.asList(expected));
    }

    protected void routeAndTest(Message m, boolean expectReturn, TestQueue... expected) throws AMQException
    {
        routeAndTest(m, expectReturn, Arrays.asList(expected));
    }

    protected void routeAndTest(Message m, boolean expectReturn, List<TestQueue> expected) throws AMQException
    {
        try
        {
            route(m);
            assertFalse("Expected " + m + " to be returned due to manadatory flag, and lack of routing", expectReturn);
            for (TestQueue q : queues)
            {
                if (expected.contains(q))
                {
                    assertTrue("Expected " + m + " to be delivered to " + q, q.isInQueue(m));
                    //assert m.isInQueue(q) : "Expected " + m + " to be delivered to " + q;
                }
                else
                {
                    assertFalse("Did not expect " + m + " to be delivered to " + q, q.isInQueue(m));
                    //assert !m.isInQueue(q) : "Did not expect " + m + " to be delivered to " + q;
                }
            }
        }

        catch (NoRouteException ex)
        {
            assertTrue("Expected " + m + " not to be returned", expectReturn);
        }

    }

    static FieldTable getHeaders(String... entries)
    {
        FieldTable headers = FieldTableFactory.newFieldTable();
        for (String s : entries)
        {
            String[] parts = s.split("=", 2);
            headers.setObject(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return headers;
    }

    static MessagePublishInfo getPublishRequest(final String id)
    {
        return new MessagePublishInfoImpl(null, false, false, new AMQShortString(id));
    }

    static ContentHeaderBody getContentHeader(FieldTable headers)
    {
        ContentHeaderBody header = new ContentHeaderBody();
        header.properties = getProperties(headers);
        return header;
    }

    static BasicContentHeaderProperties getProperties(FieldTable headers)
    {
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();
        properties.setHeaders(headers);
        return properties;
    }

    static class TestQueue extends SimpleAMQQueue
    {
        //        final List<HeadersExchangeTest.Message> messages = new ArrayList<HeadersExchangeTest.Message>();
        final List<AMQMessage> messages = new ArrayList<AMQMessage>();

        public TestQueue(AMQShortString name) throws AMQException
        {
            super(name, false, new AMQShortString("test"), true, ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test"));
            ApplicationRegistry.getInstance().getVirtualHostRegistry().getVirtualHost("test").getQueueRegistry().registerQueue(this);
        }

        /**
         * We override this method so that the default behaviour, which attempts to use a delivery manager, is
         * not invoked. It is unnecessary since for this test we only care to know whether the message was
         * sent to the queue; the queue processing logic is not being tested.
         *
         * @param msg
         *
         * @throws AMQException
         */
        @Override
        public QueueEntry enqueue(StoreContext context, AMQMessage msg) throws AMQException
        {
            messages.add(msg);//new HeadersExchangeTest.Message(msg));
            return new QueueEntry()
            {

                public AMQQueue getQueue()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public AMQMessage getMessage()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public long getSize()
                {
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean getDeliveredToConsumer()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean expired() throws AMQException
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isAcquired()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean acquire()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean acquire(Subscription sub)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean delete()
                {
                    return false;
                }

                public boolean isDeleted()
                {
                    return false;
                }

                public boolean acquiredBySubscription()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void setDeliveredToSubscription()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void release()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public String debugIdentity()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean immediateAndNotDelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void setRedelivered(boolean b)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public Subscription getDeliveredSubscription()
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void reject()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void reject(Subscription subscription)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isRejectedBy(Subscription subscription)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void requeue(StoreContext storeContext) throws AMQException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dequeue(final StoreContext storeContext) throws FailedDequeueException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dispose(final StoreContext storeContext) throws MessageCleanupException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void restoreCredit()
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public void dequeueAndDelete(StoreContext storeContext) throws FailedDequeueException
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isQueueDeleted()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public void addStateChangeListener(StateChangeListener listener)
                {
                    //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean removeStateChangeListener(StateChangeListener listener)
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public int compareTo(final QueueEntry o)
                {
                    return 0;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public ContentHeaderBody getContentHeaderBody() throws AMQException
                {
                    return null;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isPersistent() throws AMQException
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }

                public boolean isRedelivered()
                {
                    return false;  //To change body of implemented methods use File | Settings | File Templates.
                }
            };
        }

        boolean isInQueue(Message msg)
        {
            return messages.contains(msg);
        }

    }

    /** Just add some extra utility methods to AMQMessage to aid testing. */
    static class Message
    {

        private static TransactionLog _transactionLog = new SkeletonMessageStore();

        private static TransactionalContext _txnContext = new NonTransactionalContext(_transactionLog, new StoreContext(),
                                                                                      null,
                                                                                      new LinkedList<RequiredDeliveryException>()
        );

        public static Message create(String id, String... headers) throws AMQException
        {
            ContentHeaderBody headerBody = getContentHeader(getHeaders(headers));

            MessagePublishInfo mpi = getPublishRequest(id);

            IncomingMessage incomming = new IncomingMessage(mpi, _txnContext, new MockProtocolSession(_transactionLog), _transactionLog);

            try
            {
                incomming.setContentHeaderBody(headerBody);
            }
            catch (AMQException e)
            {

            }

            return new Message(incomming, mpi);
        }

        private IncomingMessage _incoming;
        private MessagePublishInfo _mpi;

        public Message(IncomingMessage incomming, MessagePublishInfo mpi)
        {
            _incoming = incomming;
            _mpi = mpi;
        }

        public IncomingMessage getIncomingMessage()
        {
            return _incoming;
        }

        public MessagePublishInfo getMessagePublishInfo()
        {
            return _mpi;
        }

        public boolean equals(Object o)
        {
            if (o instanceof AMQMessage)
            {
                return _incoming.getMessageId().equals(((AMQMessage) o).getMessageId());
            }

            if (o instanceof Message)
            {
                return _incoming.getMessageId().equals(((Message) o).getIncomingMessage().getMessageId());
            }

            return false;
        }
    }
}
