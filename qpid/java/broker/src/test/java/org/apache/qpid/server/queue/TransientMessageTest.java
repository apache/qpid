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
import org.apache.qpid.framing.ContentHeaderProperties;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.server.store.StoreContext;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TransientMessageTest extends TestCase
{
    AMQMessage _message;
    StoreContext _storeContext = null;

    protected AMQMessage newMessage(Long id)
    {
        return new MessageFactory().createMessage(id, null, false);
    }

    public void testMessageID()
    {
        Long id = 1L;
        _message = newMessage(id);

        assertEquals("Message not set value", id, _message.getMessageId());
    }

    public void testInvalidContentChunk()
    {
        _message = newMessage(1L);

        try
        {
            _message.getContentChunk(0);
            fail("getContentChunk should not succeed");
        }
        catch (RuntimeException e)
        {
            assertTrue(e.getMessage().equals("No ContentBody has been set"));
        }

        ContentChunk cc = new MockContentChunk(100);

        try
        {
            _message.addContentBodyFrame(_storeContext, cc, false);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            _message.getContentChunk(-1);
            fail("getContentChunk should not succeed");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("out of valid range"));
        }

        try
        {
            _message.getContentChunk(1);
            fail("getContentChunk should not succeed");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("out of valid range"));
        }
    }

    public void testAddSingleContentChunk()
    {

        _message = newMessage(1L);

        ContentChunk cc = new MockContentChunk(100);

        try
        {
            _message.addContentBodyFrame(_storeContext, cc, true);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        assertEquals("Incorrect body count", 1, _message.getBodyCount());

        assertEquals("Incorrect ContentChunk returned.", cc, _message.getContentChunk(0));

        cc = new MockContentChunk(100);

        try
        {
            _message.addContentBodyFrame(_storeContext, cc, true);
            fail("Exception should prevent adding two final chunks");
        }
        catch (UnsupportedOperationException e)
        {
            //normal path
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

    }

    public void testAddMultipleContentChunk()
    {

        _message = newMessage(1L);

        ContentChunk cc = new MockContentChunk(100);

        try
        {
            _message.addContentBodyFrame(_storeContext, cc, false);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        assertEquals("Incorrect body count", 1, _message.getBodyCount());

        assertEquals("Incorrect ContentChunk returned.", cc, _message.getContentChunk(0));

        cc = new MockContentChunk(100);

        try
        {
            _message.addContentBodyFrame(_storeContext, cc, true);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        assertEquals("Incorrect body count", 2, _message.getBodyCount());

        assertEquals("Incorrect ContentChunk returned.", cc, _message.getContentChunk(1));

    }

    public void testInitialArrivalTime()
    {
        _message = newMessage(1L);

        assertEquals("Initial Arrival time should be 0L", 0L, _message.getArrivalTime());
    }

    public void testSetPublishAndContentHeaderBody_WithBody()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();
        int bodySize = 100;

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, new BasicContentHeaderProperties(), bodySize);

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertEquals("BodySize not returned correctly. ", bodySize, _message.getSize());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testSetPublishAndContentHeaderBody_Null()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, mpi, null);
            fail("setPublishAndContentHeaderBody with null ContentHeaederBody did not throw NPE.");
        }
        catch (NullPointerException npe)
        {
            assertEquals("HeaderBody cannot be null", npe.getMessage());
        }
        catch (AMQException e)
        {
            fail("setPublishAndContentHeaderBody should not throw AMQException here:" + e.getMessage());
        }

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, null, chb);
            fail("setPublishAndContentHeaderBody with null MessagePublishInfo did not throw NPE.");
        }
        catch (NullPointerException npe)
        {
            assertEquals("PublishInfo cannot be null", npe.getMessage());
        }
        catch (AMQException e)
        {
            fail("setPublishAndContentHeaderBody should not throw AMQException here:" + e.getMessage());
        }
    }

    public void testSetPublishAndContentHeaderBody_Empty()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertEquals("BodySize not returned correctly. ", bodySize, _message.getSize());

            ContentHeaderBody retreived_chb = _message.getContentHeaderBody();

            ContentHeaderProperties chp = retreived_chb.properties;

            assertEquals("ContentHeaderBody not correct", chb, retreived_chb);

            assertEquals("AppID not correctly retreived", "HandleTest",
                         ((BasicContentHeaderProperties) chp).getAppIdAsString());

            MessagePublishInfo retreived_mpi = _message.getMessagePublishInfo();

            assertEquals("MessagePublishInfo not correct", mpi, retreived_mpi);

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testIsPersistent()
    {
        _message = newMessage(1L);

        assertFalse(_message.isPersistent());
    }

    public void testImmediateAndNotDelivered()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, true, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertTrue("Undelivered Immediate message should still be marked as so", _message.immediateAndNotDelivered());

            assertFalse("Undelivered Message should not say it is delivered.", _message.getDeliveredToConsumer());

            _message.setDeliveredToConsumer();

            assertTrue("Delivered Message should say it is delivered.", _message.getDeliveredToConsumer());

            assertFalse("Delivered Immediate message now be marked as so", _message.immediateAndNotDelivered());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testNotImmediateAndNotDelivered()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertFalse("Undelivered Non-Immediate message should not result in true.", _message.immediateAndNotDelivered());

            assertFalse("Undelivered Message should not say it is delivered.", _message.getDeliveredToConsumer());

            _message.setDeliveredToConsumer();

            assertTrue("Delivered Message should say it is delivered.", _message.getDeliveredToConsumer());

            assertFalse("Delivered Non-Immediate message not change this return", _message.immediateAndNotDelivered());
        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

    public void testExpiry()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        ReentrantLock waitLock = new ReentrantLock();
        Condition wait = waitLock.newCondition();
        try
        {
            _message.setExpiration(System.currentTimeMillis() + 10L);

            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertFalse("New messages should not be expired.", _message.expired());

            final long MILLIS =1000000L;
            long waitTime = 20 * MILLIS;

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

            assertTrue("After a sleep messages should now be expired.", _message.expired());

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }


        public void testNoExpiry()
    {
        _message = newMessage(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl(null, false, false, null);
        int bodySize = 0;

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        ReentrantLock waitLock = new ReentrantLock();
        Condition wait = waitLock.newCondition();
        try
        {

            _message.setPublishAndContentHeaderBody(_storeContext, mpi, chb);

            assertFalse("New messages should not be expired.", _message.expired());

            final long MILLIS =1000000L;
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

            assertFalse("After a sleep messages without an expiry should not expire.", _message.expired());

        }
        catch (AMQException e)
        {
            fail(e.getMessage());
        }
    }

}
