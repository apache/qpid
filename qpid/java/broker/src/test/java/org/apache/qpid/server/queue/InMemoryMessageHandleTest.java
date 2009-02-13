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

public class InMemoryMessageHandleTest extends TestCase
{
    AMQMessageHandle _handle;

    protected AMQMessageHandle newHandle(Long id)
    {
        return new InMemoryMessageHandle(id);
    }

    public void testMessageID()
    {
        Long id = 1L;
        _handle = newHandle(id);

        assertEquals("Message not set value", id, _handle.getMessageId());
    }

    public void testInvalidContentChunk()
    {
        _handle = newHandle(1L);

        try
        {
            _handle.getContentChunk(null, 0);
            fail("getContentChunk should not succeed");
        }
        catch (RuntimeException e)
        {
            assertTrue(e.getMessage().equals("No ContentBody has been set"));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        ContentChunk cc = new MockContentChunk(null, 100);

        try
        {
            _handle.addContentBodyFrame(null, cc, false);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            _handle.getContentChunk(null, -1);
            fail("getContentChunk should not succeed");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("out of valid range"));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            _handle.getContentChunk(null, 1);
            fail("getContentChunk should not succeed");
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.getMessage().contains("out of valid range"));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }
    }

    public void testAddSingleContentChunk()
    {

        _handle = newHandle(1L);

        ContentChunk cc = new MockContentChunk(null, 100);

        try
        {
            _handle.addContentBodyFrame(null, cc, true);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect body count", 1, _handle.getBodyCount(null));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect ContentChunk returned.", cc, _handle.getContentChunk(null, 0));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        cc = new MockContentChunk(null, 100);

        try
        {
            _handle.addContentBodyFrame(null, cc, true);
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

        _handle = newHandle(1L);

        ContentChunk cc = new MockContentChunk(null, 100);

        try
        {
            _handle.addContentBodyFrame(null, cc, false);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect body count", 1, _handle.getBodyCount(null));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect ContentChunk returned.", cc, _handle.getContentChunk(null, 0));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        cc = new MockContentChunk(null, 100);

        try
        {
            _handle.addContentBodyFrame(null, cc, true);
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect body count", 2, _handle.getBodyCount(null));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

        try
        {
            assertEquals("Incorrect ContentChunk returned.", cc, _handle.getContentChunk(null, 1));
        }
        catch (AMQException e)
        {
            fail("AMQException thrown:" + e.getMessage());
        }

    }

    // todo Move test to QueueEntry
//    public void testRedelivered()
//    {
//        _handle = newHandle(1L);
//
//        assertFalse("New message should not be redelivered", _handle.isRedelivered());
//
//        _handle.setRedelivered(true);
//
//        assertTrue("New message should not be redelivered", _handle.isRedelivered());
//    }

    public void testInitialArrivalTime()
    {
        _handle = newHandle(1L);

        assertEquals("Initial Arrival time should be 0L", 0L, _handle.getArrivalTime());
    }

    public void testSetPublishAndContentHeaderBody_WithBody()
    {
        _handle = newHandle(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();
        int bodySize = 100;

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, new BasicContentHeaderProperties(), bodySize);

        try
        {
            _handle.setPublishAndContentHeaderBody(null, mpi, chb);

            assertEquals("BodySize not returned correctly. ", bodySize, _handle.getBodySize(null));
        }
        catch (AMQException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void testSetPublishAndContentHeaderBody_Empty()
    {
        _handle = newHandle(1L);

        MessagePublishInfo mpi = new MessagePublishInfoImpl();
        int bodySize = 0;                                                         

        BasicContentHeaderProperties props = new BasicContentHeaderProperties();

        props.setAppId("HandleTest");

        ContentHeaderBody chb = new ContentHeaderBody(0, 0, props, bodySize);

        try
        {
            _handle.setPublishAndContentHeaderBody(null, mpi, chb);

            assertEquals("BodySize not returned correctly. ", bodySize, _handle.getBodySize(null));

            ContentHeaderBody retreived_chb = _handle.getContentHeaderBody(null);

            ContentHeaderProperties chp = retreived_chb.properties;

            assertEquals("ContentHeaderBody not correct", chb, retreived_chb);

            assertEquals("AppID not correctly retreived", "HandleTest",
                         ((BasicContentHeaderProperties) chp).getAppIdAsString());

            MessagePublishInfo retreived_mpi = _handle.getMessagePublishInfo(null);

            assertEquals("MessagePublishInfo not correct", mpi, retreived_mpi);


        }
        catch (AMQException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void testIsPersistent()
    {
        _handle = newHandle(1L);
        
        assertFalse(_handle.isPersistent());
    }

}
