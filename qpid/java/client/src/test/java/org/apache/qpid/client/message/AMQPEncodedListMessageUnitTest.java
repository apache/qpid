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
package org.apache.qpid.client.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.MessageFormatException;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.codec.BBEncoder;

public class AMQPEncodedListMessageUnitTest extends QpidTestCase
{

    Map<String,String> _map = new HashMap<String,String>();
    List<Object> _list = new ArrayList<Object>();
    UUID _uuid = UUID.randomUUID();

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _map.put("Key1","String1");
        _map.put("Key2","String2");
        _map.put("Key3","String3");

        _list.add(1);
        _list.add(2);
        _list.add(3);
    }

    /**
     * Test whether we accept the correct types while rejecting invalid types.
     */
    public void testAddObject() throws Exception
    {
        AMQPEncodedListMessage m = new AMQPEncodedListMessage(AMQMessageDelegateFactory.FACTORY_0_10);
        m.add(true);
        m.add((byte)256);
        m.add(Short.MAX_VALUE);
        m.add(Integer.MAX_VALUE);
        m.add(Long.MAX_VALUE);
        m.add(10.22);
        m.add("Msg");
        m.add("Msg".getBytes());
        m.add(_list);
        m.add(_map);
        m.add(_uuid);

        try
        {
            m.add(new Object());
            fail("Validation for element type failed");
        }
        catch (MessageFormatException e)
        {
        }
    }

    public void testListBehaviorForIncommingMsg() throws Exception
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeList(_list);
        AMQPEncodedListMessage m = new AMQPEncodedListMessage(new AMQMessageDelegate_0_10(),encoder.segment());

        assertTrue("contains(Object) method did not return true as expected",m.contains(1));
        assertFalse("contains(Object) method did not return false as expected",m.contains(5));
        assertEquals("get(index) method returned incorrect value",((Integer)m.get(1)).intValue(),2);
        assertEquals("indexOf(Object) method returned incorrect index",m.indexOf(2),1);
        try
        {
            m.get(10);
        }
        catch (MessageFormatException e)
        {
            assertTrue("Incorrect exception type. Expected IndexOutOfBoundsException", e.getCause() instanceof IndexOutOfBoundsException);
        }
    }

    public void testStreamMessageInterfaceForIncommingMsg() throws Exception
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeList(getList());
        AMQPEncodedListMessage m = new AMQPEncodedListMessage(new AMQMessageDelegate_0_10(),encoder.segment());

        assertEquals(true,m.readBoolean());
        assertEquals((byte)256,m.readByte());
        assertEquals(Short.MAX_VALUE,m.readShort());
        assertEquals(Integer.MAX_VALUE,m.readInt());
        assertEquals(Long.MAX_VALUE,m.readLong());
        assertEquals(10.22,m.readDouble());
        assertEquals("Msg",m.readString());
        assertEquals(_list,(List)m.readObject());
        assertEquals(_map,(Map)m.readObject());
        assertEquals(_uuid,(UUID)m.readObject());
    }

    public void testMapMessageInterfaceForIncommingMsg() throws Exception
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeList(getList());
        AMQPEncodedListMessage m = new AMQPEncodedListMessage(new AMQMessageDelegate_0_10(),encoder.segment());

        assertEquals(true,m.getBoolean("0"));
        assertEquals((byte)256,m.getByte("1"));
        assertEquals(Short.MAX_VALUE,m.getShort("2"));
        assertEquals(Integer.MAX_VALUE,m.getInt("3"));
        assertEquals(Long.MAX_VALUE,m.getLong("4"));
        assertEquals(10.22,m.getDouble("5"));
        assertEquals("Msg",m.getString("6"));
        assertEquals(_list,(List)m.getObject("7"));
        assertEquals(_map,(Map)m.getObject("8"));
        assertEquals(_uuid,(UUID)m.getObject("9"));
    }

    public List<Object> getList()
    {
        List<Object> myList = new ArrayList<Object>();
        myList.add(true);
        myList.add((byte)256);
        myList.add(Short.MAX_VALUE);
        myList.add(Integer.MAX_VALUE);
        myList.add(Long.MAX_VALUE);
        myList.add(10.22);
        myList.add("Msg");
        myList.add(_list);
        myList.add(_map);
        myList.add(_uuid);
        return myList;
    }
}
