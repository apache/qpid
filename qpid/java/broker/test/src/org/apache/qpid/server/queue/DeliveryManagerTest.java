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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.apache.qpid.server.handler.OnCurrentThreadExecutor;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.NullApplicationRegistry;
import org.apache.qpid.AMQException;
import junit.framework.JUnit4TestAdapter;

public class DeliveryManagerTest extends MessageTestHelper
{
    private final SubscriptionSet _subscriptions = new SubscriptionSet();
    private final DeliveryManager _mgr;

    public DeliveryManagerTest() throws Exception
    {
        try
        {
            _mgr = new DeliveryManager(_subscriptions, new AMQQueue("myQ", false, "guest", false,
                                                                new DefaultQueueRegistry()));
        }
        catch(Throwable t)
        {
            t.printStackTrace();
            throw new AMQException("Could not initialise delivery manager", t);
        }
    }

    @Test
    public void startInQueueingMode() throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[10];
        for(int i = 0; i < messages.length; i++)
        {
            messages[i] = message();
        }
        int batch = messages.length / 2;

        for(int i = 0; i < batch; i++)
        {
            _mgr.deliver("Me", messages[i]);
        }

        TestSubscription s1 = new TestSubscription("1");
        TestSubscription s2 = new TestSubscription("2");
        _subscriptions.addSubscriber(s1);
        _subscriptions.addSubscriber(s2);

        for(int i = batch; i < messages.length; i++)
        {
            _mgr.deliver("Me", messages[i]);
        }

        assertTrue(s1.getMessages().isEmpty());
        assertTrue(s2.getMessages().isEmpty());

        _mgr.processAsync(new OnCurrentThreadExecutor());

        assertEquals(messages.length / 2, s1.getMessages().size());
        assertEquals(messages.length / 2, s2.getMessages().size());

        for(int i = 0; i < messages.length; i++)
        {
            if(i % 2 == 0)
            {
                assertTrue(s1.getMessages().get(i / 2) == messages[i]);
            }
            else
            {
                assertTrue(s2.getMessages().get(i / 2) == messages[i]);
            }
        }
    }

    @Test
    public void startInDirectMode() throws AMQException
    {
        AMQMessage[] messages = new AMQMessage[10];
        for(int i = 0; i < messages.length; i++)
        {
            messages[i] = message();
        }
        int batch = messages.length / 2;

        TestSubscription s1 = new TestSubscription("1");
        _subscriptions.addSubscriber(s1);

        for(int i = 0; i < batch; i++)
        {
            _mgr.deliver("Me", messages[i]);
        }

        assertEquals(batch, s1.getMessages().size());
        for(int i = 0; i < batch; i++)
        {
            assertTrue(messages[i] == s1.getMessages().get(i));
        }
        s1.getMessages().clear();
        assertEquals(0, s1.getMessages().size());

        s1.setSuspended(true);
        for(int i = batch; i < messages.length; i++)
        {
            _mgr.deliver("Me", messages[i]);
        }

        _mgr.processAsync(new OnCurrentThreadExecutor());
        assertEquals(0, s1.getMessages().size());
        s1.setSuspended(false);

        _mgr.processAsync(new OnCurrentThreadExecutor());
        assertEquals(messages.length - batch, s1.getMessages().size());

        for(int i = batch; i < messages.length; i++)
        {
            assertTrue(messages[i] == s1.getMessages().get(i - batch));
        }

    }

    @Test (expected=NoConsumersException.class)
    public void noConsumers() throws AMQException
    {
        _mgr.deliver("Me", message(true));
    }

    @Test (expected=NoConsumersException.class)
    public void noActiveConsumers() throws AMQException
    {
        TestSubscription s = new TestSubscription("A");
        _subscriptions.addSubscriber(s);
        s.setSuspended(true);
        _mgr.deliver("Me", message(true));
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(DeliveryManagerTest.class);
    }
}
