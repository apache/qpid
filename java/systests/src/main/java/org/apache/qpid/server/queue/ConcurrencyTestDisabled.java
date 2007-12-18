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
import org.apache.qpid.server.handler.OnCurrentThreadExecutor;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * Tests delivery in the face of concurrent incoming _messages, subscription alterations
 * and attempts to asynchronously process queued _messages.
 */
public class ConcurrencyTestDisabled extends MessageTestHelper
{
    private final Random random = new Random();

    private final int numMessages = 1000;

    private final List<SubscriptionTestHelper> _subscribers = new ArrayList<SubscriptionTestHelper>();
    private final Set<Subscription> _active = new HashSet<Subscription>();
    private final List<QueueEntry> _messages = new ArrayList<QueueEntry>();
    private int next = 0;//index to next message to send
    private final List<QueueEntry> _received = Collections.synchronizedList(new ArrayList<QueueEntry>());
    private final Executor _executor = new OnCurrentThreadExecutor();
    private final List<Thread> _threads = new ArrayList<Thread>();

    private final SubscriptionSet _subscriptionMgr = new SubscriptionSet();
    private final DeliveryManager _deliveryMgr;

    private boolean isComplete;
    private boolean failed;
    private VirtualHost _virtualHost;

    public ConcurrencyTestDisabled() throws Exception
    {

        IApplicationRegistry applicationRegistry = ApplicationRegistry.getInstance();
        _virtualHost = applicationRegistry.getVirtualHostRegistry().getVirtualHost("test");
        _deliveryMgr = new ConcurrentSelectorDeliveryManager(_subscriptionMgr, new AMQQueue(new AMQShortString("myQ"), false, new AMQShortString("guest"), false,
                                                                          _virtualHost));
    }

    public void testConcurrent1() throws InterruptedException, AMQException
    {
        initSubscriptions(10);
        initMessages(numMessages);
        initThreads(1, 4, 4, 4);
        doRun();
        check();
    }

    public void testConcurrent2() throws InterruptedException, AMQException
    {
        initSubscriptions(10);
        initMessages(numMessages);
        initThreads(4, 2, 2, 2);
        doRun();
        check();
    }

    void check()
    {
        assertFalse("Failed", failed);

        _deliveryMgr.processAsync(_executor);

        assertEquals("Did not recieve the correct number of messages", _messages.size(), _received.size());
        for(int i = 0; i < _messages.size(); i++)
        {
            assertEquals("Wrong message at " + i, _messages.get(i), _received.get(i));
        }
    }

    void initSubscriptions(int subscriptions)
    {
        for(int i = 0; i < subscriptions; i++)
        {
            _subscribers.add(new SubscriptionTestHelper("Subscriber" + i, _received));
        }
    }

    void initMessages(int messages) throws AMQException
    {
        for(int i = 0; i < messages; i++)
        {
            _messages.add(message());
        }
    }

    void initThreads(int senders, int subscribers, int suspenders, int processors)
    {
        addThreads(senders, senders == 1 ? new Sender() : new OrderedSender());
        addThreads(subscribers, new Subscriber());
        addThreads(suspenders, new Suspender());
        addThreads(processors, new Processor());
    }

    void addThreads(int count, Runnable runner)
    {
        for(int i = 0; i < count; i++)
        {
            _threads.add(new Thread(runner, runner.toString()));
        }
    }

    void doRun() throws InterruptedException
    {
        for(Thread t : _threads)
        {
            t.start();
        }

        for(Thread t : _threads)
        {
            t.join();
        }
    }

    private void toggle(Subscription s)
    {
        synchronized (_active)
        {
            if (_active.contains(s))
            {
                _active.remove(s);
                Subscription result = _subscriptionMgr.removeSubscriber(s);
                assertTrue("Removed subscription " + result + " but trying to remove subscription " + s,
                        result != null && result.equals(s));
            }
            else
            {
                _active.add(s);
                _subscriptionMgr.addSubscriber(s);
            }
        }
    }

    private QueueEntry nextMessage()
    {
        synchronized (_messages)
        {
            if (next < _messages.size())
            {
                return _messages.get(next++);
            }
            else
            {
                if (!_deliveryMgr.hasQueuedMessages()) {
                    isComplete = true;
                }
                return null;
            }
        }
    }

    private boolean randomBoolean()
    {
        return random.nextBoolean();
    }

    private SubscriptionTestHelper randomSubscriber()
    {
        return _subscribers.get(random.nextInt(_subscribers.size()));
    }

    private class Sender extends Runner
    {
        void doRun() throws Throwable
        {
            QueueEntry msg = nextMessage();
            if (msg != null)
            {
                _deliveryMgr.deliver(null, new AMQShortString(toString()), msg, false);
            }
        }
    }

    private class OrderedSender extends Sender
    {
        synchronized void doRun() throws Throwable
        {
            super.doRun();
        }
    }

    private class Suspender extends Runner
    {
        void doRun() throws Throwable
        {
            randomSubscriber().setSuspended(randomBoolean());
        }
    }

    private class Subscriber extends Runner
    {
        void doRun() throws Throwable
        {
            toggle(randomSubscriber());
        }
    }

    private class Processor extends Runner
    {
        void doRun() throws Throwable
        {
            _deliveryMgr.processAsync(_executor);
        }
    }

    private abstract class Runner implements Runnable
    {
        public void run()
        {
            try
            {
                while (!stop())
                {
                    doRun();
                }
            }
            catch (Throwable t)
            {
                failed = true;
                t.printStackTrace();
            }
        }

        abstract void doRun() throws Throwable;

        boolean stop()
        {
            return isComplete || failed;
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ConcurrencyTestDisabled.class);
    }

}
