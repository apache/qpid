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
package org.apache.qpid.client.message;

import junit.framework.JUnit4TestAdapter;
import org.junit.Test;
import org.junit.Assert;

import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import javax.jms.MessageEOFException;

public class TestBytesMessage
{
    /**
     * Tests that on creation a call to getBodyLength() throws an exception
     * if null was passed in during creation
     */
    @Test(expected=MessageNotReadableException.class)
    public void testNotReadableOnCreationWithNull() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.getBodyLength();
    }

    @Test(expected= MessageNotWriteableException.class)
    public void testResetMakesReadble() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeInt(10);
        bm.reset();
        bm.writeInt(12);
    }

    @Test
    public void testClearBodyMakesWritable() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeInt(10);
        bm.reset();
        bm.clearBody();
        bm.writeInt(10);
    }

    @Test
    public void testWriteInt() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeInt(10);
        bm.reset();
        long len = bm.getBodyLength();
        Assert.assertTrue(len == 4);
        int val = bm.readInt();
        Assert.assertTrue(val == 10);
    }

    @Test
    public void testWriteString() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeUTF("Bananas");
        bm.reset();
        String res = bm.readUTF();
        Assert.assertEquals("Bananas", res);
    }

    @Test(expected=NullPointerException.class)
    public void testWriteObjectThrowsNPE() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeObject(null);
    }

    @Test
    public void testReadBoolean() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeBoolean(true);
        bm.reset();
        boolean result = bm.readBoolean();
        Assert.assertTrue(result);        
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFByte() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)1);
        bm.reset();
        bm.readByte();
        // should throw
        bm.readByte();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFUnsignedByte() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)1);
        bm.reset();
        bm.readByte();
        // should throw
        bm.readUnsignedByte();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFBoolean() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeBoolean(true);
        bm.reset();
        bm.readBoolean();
        // should throw
        bm.readBoolean();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFChar() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeChar('A');
        bm.reset();
        bm.readChar();
        // should throw
        bm.readChar();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFDouble() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeDouble(1.3d);
        bm.reset();
        bm.readDouble();
        // should throw
        bm.readDouble();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFFloat() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeFloat(1.3f);
        bm.reset();
        bm.readFloat();
        // should throw
        bm.readFloat();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFInt() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeInt(99);
        bm.reset();
        bm.readInt();
        // should throw
        bm.readInt();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFLong() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeLong(4L);
        bm.reset();
        bm.readLong();
        // should throw
        bm.readLong();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFShort() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeShort((short)4);
        bm.reset();
        bm.readShort();
        // should throw
        bm.readShort();
    }

    @Test(expected=MessageEOFException.class)
    public void testEOFUnsignedShort() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeShort((short)4);
        bm.reset();
        bm.readUnsignedShort();
        // should throw
        bm.readUnsignedShort();
    }

    /**
     * Tests that the readBytes() method populates the passed in array
     * correctly
     * @throws Exception
     */
    @Test
    public void testReadBytes() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)3);
        bm.writeByte((byte)4);
        bm.reset();
        byte[] result = new byte[2];
        int count = bm.readBytes(result);
        Assert.assertEquals((byte)3, result[0]);
        Assert.assertEquals((byte)4, result[1]);
        Assert.assertEquals(2, count);
    }

    @Test
    public void testReadBytesEOF() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)3);
        bm.writeByte((byte)4);
        bm.reset();
        byte[] result = new byte[2];
        bm.readBytes(result);
        int count = bm.readBytes(result);
        Assert.assertEquals(-1, count);
    }

    @Test
    public void testReadBytesWithLargerArray() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)3);
        bm.writeByte((byte)4);
        bm.reset();
        byte[] result = new byte[3];
        int count = bm.readBytes(result);
        Assert.assertEquals(2, count);
        Assert.assertEquals((byte)3, result[0]);
        Assert.assertEquals((byte)4, result[1]);
        Assert.assertEquals((byte)0, result[2]);
    }

    @Test
    public void testReadBytesWithCount() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.writeByte((byte)3);
        bm.writeByte((byte)4);
        bm.writeByte((byte)5);
        bm.reset();
        byte[] result = new byte[3];
        int count = bm.readBytes(result, 2);
        Assert.assertEquals(2, count);
        Assert.assertEquals((byte)3, result[0]);
        Assert.assertEquals((byte)4, result[1]);
        Assert.assertEquals((byte)0, result[2]);
    }

    @Test
    public void testToBodyString() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        final String testText = "This is a test";
        bm.writeUTF(testText);
        bm.reset();
        String result = bm.toBodyString();
        Assert.assertEquals(testText, result);
    }

    @Test
    public void testToBodyStringWithNull() throws Exception
    {
        JMSBytesMessage bm = new JMSBytesMessage();
        bm.reset();
        String result = bm.toBodyString();
        Assert.assertNull(result);
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TestBytesMessage.class);
    }
}
