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

    protected AMQMessage newMessage()
    {
        return MessageFactory.getInstance().createMessage(null, false);
    }

    public void testMessageID()
    {
        _message = newMessage();

        assertTrue("Message ID is not set ", _message.getMessageId() > 0L);
    }

    public void testInvalidContentChunk()
    {
        _message = newMessage();

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

        _message = newMessage();

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

        _message = newMessage();

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
        _message = newMessage();

        assertEquals("Initial Arrival time should be 0L", 0L, _message.getArrivalTime());
    }

    public void testSetPublishAndContentHeaderBody_WithBody()
    {
        _message = newMessage();

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
        _message = newMessage();

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
        _message = newMessage();

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
        _message = newMessage();

        assertFalse(_message.isPersistent());
    }

 
}
