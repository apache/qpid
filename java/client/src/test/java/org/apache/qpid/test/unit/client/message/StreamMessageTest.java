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
package org.apache.qpid.test.unit.client.message;

import junit.framework.TestCase;
import org.apache.qpid.client.message.JMSStreamMessage;
import org.apache.qpid.client.message.TestMessageHelper;

import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import javax.jms.MessageFormatException;
import javax.jms.MessageEOFException;
import java.util.HashMap;

/**
 * @author Apache Software Foundation
 */
public class StreamMessageTest extends TestCase
{
    /**
     * Tests that on creation a call to getBodyLength() throws an exception
     * if null was passed in during creation
     */
    public void testNotReadableOnCreationWithNull() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.readByte();
            fail("expected exception did not occur");
        }
        catch (MessageNotReadableException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageNotReadableException, got " + e);
        }
    }

    public void testResetMakesReadble() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeInt(10);
            bm.reset();
            bm.writeInt(12);
            fail("expected exception did not occur");
        }
        catch (MessageNotWriteableException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageNotWriteableException, got " + e);
        }
    }

    public void testClearBodyMakesWritable() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeInt(10);
        bm.reset();
        bm.clearBody();
        bm.writeInt(10);
    }

    public void testWriteBoolean() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeBoolean(true);
        bm.writeBoolean(false);
        bm.reset();
        boolean val = bm.readBoolean();
        assertEquals(true, val);
        val = bm.readBoolean();
        assertEquals(false, val);
    }

    public void testWriteInt() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeInt(10);
        bm.reset();
        int val = bm.readInt();
        assertTrue(val == 10);
    }

    public void testWriteString() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeString("Bananas");
        bm.reset();
        String res = bm.readString();
        assertEquals("Bananas", res);
    }

    public void testWriteBytes() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        byte[] bytes = {1,2,3,4};
        bm.writeBytes(bytes, 1, 2);
        bm.reset();
        bytes = new byte[2];
        bm.readBytes(bytes);
        assertEquals(2, bytes[0]);
        assertEquals(3, bytes[1]);
    }

    public void testWriteObject() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeObject(new Boolean(true));
        bm.writeObject(new Boolean(false));
        bm.writeObject(new Byte((byte)2));
        bm.writeObject(new byte[]{1,2,3,4});
        bm.writeObject(new Character('g'));
        bm.writeObject(new Short((short) 29));
        bm.writeObject(new Integer(101));
        bm.writeObject(new Long(50003222L));
        bm.writeObject("Foobar");
        bm.writeObject(new Float(1.7f));
        bm.writeObject(new Double(8.7d));
        bm.reset();
        assertTrue(bm.readBoolean());
        assertTrue(!bm.readBoolean());
        assertEquals((byte)2, bm.readByte());
        byte[] bytes = new byte[4];
        bm.readBytes(bytes);
        assertEquals('g', bm.readChar());
        assertEquals((short) 29, bm.readShort());
        assertEquals(101, bm.readInt());
        assertEquals(50003222L, bm.readLong());
        assertEquals("Foobar", bm.readString());
        assertEquals(1.7f, bm.readFloat());
        assertEquals(8.7d, bm.readDouble());
    }

    public void testWriteObjectRejectsNonPrimitives() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeObject(new HashMap());
            fail("expected MessageFormatException was not thrown");
        }
        catch (MessageFormatException e)
        {
            // pass
        }
    }

    public void testWriteObjectThrowsNPE() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeObject(null);
            fail("expected exception did not occur");
        }
        catch (NullPointerException n)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected NullPointerException, got " + e);
        }
    }

    public void testReadBoolean() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.writeBoolean(true);
        bm.reset();
        boolean result = bm.readBoolean();
        assertTrue(result);
    }

    public void testReadBytesChecksNull() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.readBytes(null);
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
    }

    public void testReadBytesReturnsCorrectLengths() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        byte[] bytes = {2, 3};
        bm.writeBytes(bytes);
        bm.writeBytes(null);
        bm.writeBytes(new byte[]{});
        bm.reset();
        int len = bm.readBytes(bytes);
        assertEquals(2, len);
        len = bm.readBytes(bytes);
        assertEquals(-1, len);
        len = bm.readBytes(bytes);
        assertEquals(0, len);
    }

    public void testReadMultipleByteArrays() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        byte[] bytes = {2, 3, 4};
        bm.writeBytes(bytes);
        bm.writeBytes(bytes);
        bm.reset();
        byte[] result = new byte[2];
        int len = bm.readBytes(result);
        assertEquals(2, len);
        len = bm.readBytes(result);
        assertEquals(1, len);
        len = bm.readBytes(result);
        assertEquals(2, len);        
    }

    public void testEOFByte() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeByte((byte)1);
            bm.reset();
            bm.readByte();
            // should throw
            bm.readByte();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFBoolean() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeBoolean(true);
            bm.reset();
            bm.readBoolean();
            // should throw
            bm.readBoolean();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFChar() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeChar('A');
            bm.reset();
            bm.readChar();
            // should throw
            bm.readChar();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFDouble() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeDouble(1.3d);
            bm.reset();
            bm.readDouble();
            // should throw
            bm.readDouble();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFFloat() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeFloat(1.3f);
            bm.reset();
            bm.readFloat();
            // should throw
            bm.readFloat();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFInt() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeInt(99);
            bm.reset();
            bm.readInt();
            // should throw
            bm.readInt();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFLong() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeLong(4L);
            bm.reset();
            bm.readLong();
            // should throw
            bm.readLong();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }

    public void testEOFShort() throws Exception
    {
        try
        {
            JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
            bm.writeShort((short)4);
            bm.reset();
            bm.readShort();
            // should throw
            bm.readShort();
            fail("expected exception did not occur");
        }
        catch (MessageEOFException m)
        {
            // ok
        }
        catch (Exception e)
        {
            fail("expected MessageEOFException, got " + e);
        }
    }
    
    public void testToBodyStringWithNull() throws Exception
    {
        JMSStreamMessage bm = TestMessageHelper.newJMSStreamMessage();
        bm.reset();
        String result = bm.toBodyString();
        assertNull(result);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(StreamMessageTest.class);
    }
}
