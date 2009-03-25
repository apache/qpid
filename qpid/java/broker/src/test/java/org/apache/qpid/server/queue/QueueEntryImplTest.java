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
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class QueueEntryImplTest extends TestCase
{

    /** Test the Redelivered state of a QueueEntryImpl */
    public void testRedelivered()
    {
        QueueEntry entry = new MockQueueEntry(null);

        assertFalse("New message should not be redelivered", entry.isRedelivered());

        entry.setRedelivered(true);

        assertTrue("New message should not be redelivered", entry.isRedelivered());

        //Check we can revert it.. not that we ever should.
        entry.setRedelivered(false);

        assertFalse("New message should not be redelivered", entry.isRedelivered());

    }

    public void testImmediateAndNotDelivered()
    {
        AMQMessage message = MessageFactory.getInstance().createMessage(null, false);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, true, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            message.setPublishAndContentHeaderBody(null, mpi, chb);

            QueueEntry queueEntry = new MockQueueEntry(message);

            assertTrue("Undelivered Immediate message should still be marked as so", queueEntry.immediateAndNotDelivered());

            assertFalse("Undelivered Message should not say it is delivered.", queueEntry.getDeliveredToConsumer());

            queueEntry.setDeliveredToSubscription();

            assertTrue("Delivered Message should say it is delivered.", queueEntry.getDeliveredToConsumer());

            assertFalse("Delivered Immediate message now be marked as so", queueEntry.immediateAndNotDelivered());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testNotImmediateAndNotDelivered()
    {
        AMQMessage message = MessageFactory.getInstance().createMessage(null, false);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            message.setPublishAndContentHeaderBody(null, mpi, chb);

            QueueEntry queueEntry = new MockQueueEntry(message);

            assertFalse("Undelivered Non-Immediate message should not result in true.", queueEntry.immediateAndNotDelivered());

            assertFalse("Undelivered Message should not say it is delivered.", queueEntry.getDeliveredToConsumer());

            queueEntry.setDeliveredToSubscription();

            assertTrue("Delivered Message should say it is delivered.", queueEntry.getDeliveredToConsumer());

            assertFalse("Delivered Non-Immediate message not change this return", queueEntry.immediateAndNotDelivered());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testExpiry()
    {
        AMQMessage message = MessageFactory.getInstance().createMessage(null, false);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        ReentrantLock waitLock = new ReentrantLock();
        Condition wait = waitLock.newCondition();
        try
        {
            message.setExpiration(System.currentTimeMillis() + 500L);

            message.setPublishAndContentHeaderBody(null, mpi, chb);

            QueueEntry queueEntry = new MockQueueEntry(message);

            assertFalse("New messages should not be expired.", queueEntry.expired());

            final long MILLIS = 1000000L;
            long waitTime = 500 * MILLIS;

            while (waitTime > 0)
            {
                try
                {
                    waitLock.lock();

                    waitTime = wait.awaitNanos(waitTime);
                }
                catch (InterruptedException e)
                {
                    //Stop if we are interrupted
                    fail(e.getMessage());
                }
                finally
                {
                    waitLock.unlock();
                }

            }
            assertTrue("After a sleep messages should now be expired.", queueEntry.expired());

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testNoExpiry()
    {
        AMQMessage message = MessageFactory.getInstance().createMessage(null, false);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        ReentrantLock waitLock = new ReentrantLock();
        Condition wait = waitLock.newCondition();
        try
        {

            message.setPublishAndContentHeaderBody(null, mpi, chb);

            QueueEntry queueEntry = new MockQueueEntry(message);

            assertFalse("New messages should not be expired.", queueEntry.expired());

            final long MILLIS = 1000000L;
            long waitTime = 10 * MILLIS;

            while (waitTime > 0)
            {
                try
                {
                    waitLock.lock();

                    waitTime = wait.awaitNanos(waitTime);
                }
                catch (InterruptedException e)
                {
                    //Stop if we are interrupted
                    fail(e.getMessage());
                }
                finally
                {
                    waitLock.unlock();
                }

            }

            assertFalse("After a sleep messages without an expiry should not expire.", queueEntry.expired());

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

}
