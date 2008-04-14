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

import org.apache.qpid.server.handler.OnCurrentThreadExecutor;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;

import junit.framework.TestSuite;

abstract public class DeliveryManagerTest extends MessageTestHelper
{
    protected final SubscriptionSet _subscriptions = new SubscriptionSet();
    protected DeliveryManager _mgr;
    protected StoreContext _storeContext = new StoreContext();
    private static final AMQShortString DEFAULT_QUEUE_NAME = new AMQShortString("Me");

    public DeliveryManagerTest() throws Exception
    {
    }

    public void testStartInQueueingMode() throws AMQException
    {
        QueueEntry[] messages = new QueueEntry[10];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message();
        }
        int batch = messages.length / 2;

        for (int i = 0; i < batch; i++)
        {
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, messages[i], false);
        }

        SubscriptionTestHelper s1 = new SubscriptionTestHelper("1");
        SubscriptionTestHelper s2 = new SubscriptionTestHelper("2");
        _subscriptions.addSubscriber(s1);
        _subscriptions.addSubscriber(s2);

        for (int i = batch; i < messages.length; i++)
        {
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, messages[i], false);
        }

        assertTrue(s1.getMessages().isEmpty());
        assertTrue(s2.getMessages().isEmpty());

        _mgr.processAsync(new OnCurrentThreadExecutor());

        assertEquals(messages.length / 2, s1.getMessages().size());
        assertEquals(messages.length / 2, s2.getMessages().size());

        for (int i = 0; i < messages.length; i++)
        {
            if (i % 2 == 0)
            {
                assertTrue(s1.getMessages().get(i / 2) == messages[i]);
            }
            else
            {
                assertTrue(s2.getMessages().get(i / 2) == messages[i]);
            }
        }
    }

    public void testStartInDirectMode() throws AMQException
    {
        QueueEntry[] messages = new QueueEntry[10];
        for (int i = 0; i < messages.length; i++)
        {
            messages[i] = message();
        }
        int batch = messages.length / 2;

        SubscriptionTestHelper s1 = new SubscriptionTestHelper("1");
        _subscriptions.addSubscriber(s1);

        for (int i = 0; i < batch; i++)
        {
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, messages[i], false);
        }

        assertEquals(batch, s1.getMessages().size());
        for (int i = 0; i < batch; i++)
        {
            assertTrue(messages[i] == s1.getMessages().get(i));
        }
        s1.getMessages().clear();
        assertEquals(0, s1.getMessages().size());

        s1.setSuspended(true);
        for (int i = batch; i < messages.length; i++)
        {
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, messages[i], false);
        }

        _mgr.processAsync(new OnCurrentThreadExecutor());
        assertEquals(0, s1.getMessages().size());
        s1.setSuspended(false);

        _mgr.processAsync(new OnCurrentThreadExecutor());
        assertEquals(messages.length - batch, s1.getMessages().size());

        for (int i = batch; i < messages.length; i++)
        {
            assertTrue(messages[i] == s1.getMessages().get(i - batch));
        }

    }

    public void testNoConsumers() throws AMQException
    {
        try
        {
            QueueEntry msg = message(true);
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, msg, false);
            msg.checkDeliveredToConsumer();
            fail("expected exception did not occur");
        }
        catch (NoConsumersException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected NoConsumersException, got " + e);
        }
    }

    public void testNoActiveConsumers() throws AMQException
    {
        try
        {
            SubscriptionTestHelper s = new SubscriptionTestHelper("A");
            _subscriptions.addSubscriber(s);
            s.setSuspended(true);
            QueueEntry msg = message(true);
            _mgr.deliver(_storeContext, DEFAULT_QUEUE_NAME, msg, false);
            msg.checkDeliveredToConsumer();
            fail("expected exception did not occur");
        }
        catch (NoConsumersException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected NoConsumersException, got " + e);
        }
    }

    public static junit.framework.Test suite()
    {
        TestSuite suite = new TestSuite();
        return suite;
    }
}
