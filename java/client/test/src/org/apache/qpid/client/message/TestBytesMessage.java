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

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(TestBytesMessage.class);
    }
}
