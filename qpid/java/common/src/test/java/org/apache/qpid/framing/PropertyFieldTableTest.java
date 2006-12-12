/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.framing;

import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.Enumeration;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ByteBufferProxy;
import org.apache.mina.common.support.BaseByteBuffer;

public class PropertyFieldTableTest extends TestCase
{

    //Test byte modification

    public void testByteModification()
    {
        PropertyFieldTable table = new PropertyFieldTable();
        byte[] bytes = {99, 98, 97, 96, 95};
        table.setBytes("bytes", bytes);
        bytes[0] = 1;
        bytes[1] = 2;
        bytes[2] = 3;
        bytes[3] = 4;
        bytes[4] = 5;

        assertBytesNotEqual(bytes, table.getBytes("bytes"));
    }

    //Test replacement

    public void testReplacement()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setBoolean("value", true);
        table1.setInteger("value", Integer.MAX_VALUE);
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals((Integer) Integer.MAX_VALUE, table1.getInteger("value"));
    }

    //Test Lookups

    public void testBooleanLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setBoolean("value", true);
        //Test Getting right value back
        Assert.assertEquals((Boolean) true, table1.getBoolean("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getBoolean("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("true", table1.getString("value"));
    }

    public void testByteLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setByte("value", (byte) 1);
        Assert.assertEquals((Byte) (byte) 1, table1.getByte("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getByte("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("1", table1.getString("value"));
    }

    public void testShortLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setShort("value", Short.MAX_VALUE);
        Assert.assertEquals((Short) Short.MAX_VALUE, table1.getShort("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getShort("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("" + Short.MAX_VALUE, table1.getString("value"));
    }


    public void testCharLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setChar("value", 'b');
        Assert.assertEquals((Character) 'b', table1.getCharacter("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getCharacter("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("b", table1.getString("value"));
    }

    public void testDoubleLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setDouble("value", Double.MAX_VALUE);
        Assert.assertEquals(Double.MAX_VALUE, table1.getDouble("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getDouble("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("" + Double.MAX_VALUE, table1.getString("value"));

    }

    public void testFloatLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setFloat("value", Float.MAX_VALUE);
        Assert.assertEquals(Float.MAX_VALUE, table1.getFloat("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getFloat("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("" + Float.MAX_VALUE, table1.getString("value"));

    }

    public void testIntLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setInteger("value", Integer.MAX_VALUE);
        Assert.assertEquals((Integer) Integer.MAX_VALUE, table1.getInteger("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getInteger("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("" + Integer.MAX_VALUE, table1.getString("value"));

    }

    public void testLongLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setLong("value", Long.MAX_VALUE);
        Assert.assertEquals((Long) Long.MAX_VALUE, table1.getLong("value"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getLong("Rubbish") == null);

        //Try reading value as a string
        Assert.assertEquals("" + Long.MAX_VALUE, table1.getString("value"));

    }

    public void testBytesLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        byte[] bytes = {99, 98, 97, 96, 95};
        table1.setBytes("bytes", bytes);
        assertBytesEqual(bytes, table1.getBytes("bytes"));

        //Looking up an invalid value returns null
        Assert.assertTrue(table1.getBytes("Rubbish") == null);
    }

    // Failed Lookups

    public void testFailedBooleanLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getBoolean("int"));
    }

    public void testFailedByteLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getByte("int"));
    }

    public void testFailedBytesLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getBytes("int"));
    }

    public void testFailedCharLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getCharacter("int"));
    }

    public void testFailedDoubleLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getDouble("int"));
    }

    public void testFailedFloatLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getFloat("int"));
    }

    public void testFailedIntLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getInteger("int"));
    }

    public void testFailedLongLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getLong("int"));
    }

    public void testFailedShortLookup()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        Assert.assertEquals(null, table1.getShort("int"));
    }

    public void testXML()
    {
        PropertyFieldTable table1 = new PropertyFieldTable();
        table1.setBoolean("bool", true);
        table1.setByte("byte", Byte.MAX_VALUE);
        byte[] bytes = {99, 98, 97, 96, 95};
        table1.setBytes("bytes", bytes);
        table1.setChar("char", 'c');
        table1.setDouble("double", Double.MAX_VALUE);
        table1.setFloat("float", Float.MAX_VALUE);
        table1.setInteger("int", Integer.MAX_VALUE);
        table1.setLong("long", Long.MAX_VALUE);
        table1.setShort("short", Short.MAX_VALUE);

        table1.setObject("object-bool", true);
        table1.setObject("object-byte", Byte.MAX_VALUE);
        table1.setObject("object-bytes", bytes);
        table1.setObject("object-char", 'c');
        table1.setObject("object-double", Double.MAX_VALUE);
        table1.setObject("object-float", Float.MAX_VALUE);
        table1.setObject("object-int", Integer.MAX_VALUE);
        table1.setObject("object-long", Long.MAX_VALUE);
        table1.setObject("object-short", Short.MAX_VALUE);

        String table1XML = table1.toString();

        PropertyFieldTable table2 = new PropertyFieldTable(table1XML);

        Assert.assertEquals(table1XML, table2.toString());
    }

    public void testKeyEnumeration()
    {
        PropertyFieldTable table = new PropertyFieldTable();
        table.setLong("one", 1L);
        table.setLong("two", 2L);
        table.setLong("three", 3L);
        table.setLong("four", 4L);
        table.setLong("five", 5L);

        Enumeration e = table.getPropertyNames();

        Assert.assertTrue("one".equals(e.nextElement()));
        Assert.assertTrue("two".equals(e.nextElement()));
        Assert.assertTrue("three".equals(e.nextElement()));
        Assert.assertTrue("four".equals(e.nextElement()));
        Assert.assertTrue("five".equals(e.nextElement()));
    }

    public void testValues()
    {
        PropertyFieldTable table = new PropertyFieldTable();
        table.setBoolean("bool", true);
        table.setByte("byte", Byte.MAX_VALUE);
        byte[] bytes = {99, 98, 97, 96, 95};
        table.setBytes("bytes", bytes);
        table.setChar("char", 'c');
        table.setDouble("double", Double.MAX_VALUE);
        table.setFloat("float", Float.MAX_VALUE);
        table.setInteger("int", Integer.MAX_VALUE);
        table.setLong("long", Long.MAX_VALUE);
        table.setShort("short", Short.MAX_VALUE);

        table.setObject("object-bool", true);
        table.setObject("object-byte", Byte.MAX_VALUE);
        table.setObject("object-bytes", bytes);
        table.setObject("object-char", 'c');
        table.setObject("object-double", Double.MAX_VALUE);
        table.setObject("object-float", Float.MAX_VALUE);
        table.setObject("object-int", Integer.MAX_VALUE);
        table.setObject("object-long", Long.MAX_VALUE);
        table.setObject("object-short", Short.MAX_VALUE);


        Assert.assertEquals((Boolean) true, table.getBoolean("bool"));
        Assert.assertEquals((Byte) Byte.MAX_VALUE, table.getByte("byte"));
        assertBytesEqual(bytes, table.getBytes("bytes"));
        Assert.assertEquals((Character) 'c', table.getCharacter("char"));
        Assert.assertEquals(Double.MAX_VALUE, table.getDouble("double"));
        Assert.assertEquals(Float.MAX_VALUE, table.getFloat("float"));
        Assert.assertEquals((Integer) Integer.MAX_VALUE, table.getInteger("int"));
        Assert.assertEquals((Long) Long.MAX_VALUE, table.getLong("long"));
        Assert.assertEquals((Short) Short.MAX_VALUE, table.getShort("short"));

        Assert.assertEquals(true, table.getObject("object-bool"));
        Assert.assertEquals(Byte.MAX_VALUE, table.getObject("object-byte"));
        assertBytesEqual(bytes, (byte[]) table.getObject("object-bytes"));
        Assert.assertEquals('c', table.getObject("object-char"));
        Assert.assertEquals(Double.MAX_VALUE, table.getObject("object-double"));
        Assert.assertEquals(Float.MAX_VALUE, table.getObject("object-float"));
        Assert.assertEquals(Integer.MAX_VALUE, table.getObject("object-int"));
        Assert.assertEquals(Long.MAX_VALUE, table.getObject("object-long"));
        Assert.assertEquals(Short.MAX_VALUE, table.getObject("object-short"));
    }


    public void testwriteBuffer()
    {
        byte[] bytes = {99, 98, 97, 96, 95};

        PropertyFieldTable table = new PropertyFieldTable();
        table.setBoolean("bool", true);
        table.setByte("byte", Byte.MAX_VALUE);

        table.setBytes("bytes", bytes);
        table.setChar("char", 'c');
        table.setDouble("double", Double.MAX_VALUE);
        table.setFloat("float", Float.MAX_VALUE);
        table.setInteger("int", Integer.MAX_VALUE);
        table.setLong("long", Long.MAX_VALUE);
        table.setShort("short", Short.MAX_VALUE);


        final ByteBuffer buffer = ByteBuffer.allocate((int) table.getEncodedSize()); // FIXME XXX: Is cast a problem?

        table.writeToBuffer(buffer);

        buffer.flip();

        long length = buffer.getUnsignedInt();

        try
        {
            PropertyFieldTable table2 = new PropertyFieldTable(buffer, length);

            Assert.assertEquals((Boolean) true, table2.getBoolean("bool"));
            Assert.assertEquals((Byte) Byte.MAX_VALUE, table2.getByte("byte"));
            assertBytesEqual(bytes, table2.getBytes("bytes"));
            Assert.assertEquals((Character) 'c', table2.getCharacter("char"));
            Assert.assertEquals(Double.MAX_VALUE, table2.getDouble("double"));
            Assert.assertEquals(Float.MAX_VALUE, table2.getFloat("float"));
            Assert.assertEquals((Integer) Integer.MAX_VALUE, table2.getInteger("int"));
            Assert.assertEquals((Long) Long.MAX_VALUE, table2.getLong("long"));
            Assert.assertEquals((Short) Short.MAX_VALUE, table2.getShort("short"));
        }
        catch (AMQFrameDecodingException e)
        {
            e.printStackTrace();
            fail("PFT should be instantiated from bytes." + e.getCause());
        }
    }

    public void testEncodingSize()
    {
        FieldTable result = FieldTableFactory.newFieldTable();
        int size = 0;

        result.setBoolean("boolean", true);
        size += 1 + EncodingUtils.encodedShortStringLength("boolean") + EncodingUtils.encodedBooleanLength();
        Assert.assertEquals(size, result.getEncodedSize());


        result.setByte("byte", (byte) Byte.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("byte") + EncodingUtils.encodedByteLength();
        Assert.assertEquals(size, result.getEncodedSize());


        byte[] _bytes = {99, 98, 97, 96, 95};

        result.setBytes("bytes", _bytes);
        size += 1 + EncodingUtils.encodedShortStringLength("bytes") + 1 + EncodingUtils.encodedByteLength() * _bytes.length;
        Assert.assertEquals(size, result.getEncodedSize());

        result.setChar("char", (char) 'c');
        size += 1 + EncodingUtils.encodedShortStringLength("char") + EncodingUtils.encodedCharacterLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setDouble("double", (double) Double.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("double") + EncodingUtils.encodedDoubleLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setFloat("float", (float) Float.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("float") + EncodingUtils.encodedFloatLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setInteger("int", (int) Integer.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("int") + EncodingUtils.encodedIntegerLength();
        Assert.assertEquals(size, result.getEncodedSize());


        result.setLong("long", (long) Long.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("long") + EncodingUtils.encodedLongLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setShort("short", (short) Short.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("short") + EncodingUtils.encodedShortLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setString("result", "Hello");
        size += 1 + EncodingUtils.encodedShortStringLength("result") + EncodingUtils.encodedLongStringLength("Hello");
        Assert.assertEquals(size, result.getEncodedSize());


        result.setObject("object-bool", true);
        size += 1 + EncodingUtils.encodedShortStringLength("object-bool") + EncodingUtils.encodedBooleanLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-byte", Byte.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-byte") + EncodingUtils.encodedByteLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-bytes", _bytes);
        size += 1 + EncodingUtils.encodedShortStringLength("object-bytes") + 1 + EncodingUtils.encodedByteLength() * _bytes.length;
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-char", 'c');
        size += 1 + EncodingUtils.encodedShortStringLength("object-char") + EncodingUtils.encodedCharacterLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-double", Double.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-double") + EncodingUtils.encodedDoubleLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-float", Float.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-float") + EncodingUtils.encodedFloatLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-int", Integer.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-int") + EncodingUtils.encodedIntegerLength();
        Assert.assertEquals(size, result.getEncodedSize());


        result.setObject("object-long", Long.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-long") + EncodingUtils.encodedLongLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-short", Short.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("object-short") + EncodingUtils.encodedShortLength();
        Assert.assertEquals(size, result.getEncodedSize());

    }

    public void testEncodingSize1()
    {
        FieldTable result = FieldTableFactory.newFieldTable();
        int size = 0;
        result.put("one", 1L);
        size = EncodingUtils.encodedShortStringLength("one");
        size += 1 + EncodingUtils.encodedLongLength();
        assertEquals(size, result.getEncodedSize());

        result.put("two", 2L);
        size += EncodingUtils.encodedShortStringLength("two");
        size += 1 + EncodingUtils.encodedLongLength();
        assertEquals(size, result.getEncodedSize());

        result.put("three", 3L);
        size += EncodingUtils.encodedShortStringLength("three");
        size += 1 + EncodingUtils.encodedLongLength();
        assertEquals(size, result.getEncodedSize());

        result.put("four", 4L);
        size += EncodingUtils.encodedShortStringLength("four");
        size += 1 + EncodingUtils.encodedLongLength();
        assertEquals(size, result.getEncodedSize());

        result.put("five", 5L);
        size += EncodingUtils.encodedShortStringLength("five");
        size += 1 + EncodingUtils.encodedLongLength();
        assertEquals(size, result.getEncodedSize());

        //fixme should perhaps be expanded to incorporate all types.

        final ByteBuffer buffer = ByteBuffer.allocate((int) result.getEncodedSize()); // FIXME XXX: Is cast a problem?

        result.writeToBuffer(buffer);

        buffer.flip();

        long length = buffer.getUnsignedInt();

        try
        {
            PropertyFieldTable table2 = new PropertyFieldTable(buffer, length);

            Assert.assertEquals((Long) 1L, table2.getLong("one"));
            Assert.assertEquals((Long) 2L, table2.getLong("two"));
            Assert.assertEquals((Long) 3L, table2.getLong("three"));
            Assert.assertEquals((Long) 4L, table2.getLong("four"));
            Assert.assertEquals((Long) 5L, table2.getLong("five"));
        }
        catch (AMQFrameDecodingException e)
        {
            e.printStackTrace();
            fail("PFT should be instantiated from bytes." + e.getCause());
        }

    }

    private void assertBytesEqual(byte[] expected, byte[] actual)
    {
        Assert.assertEquals(expected.length, actual.length);

        for (int index = 0; index < expected.length; index++)
        {
            Assert.assertEquals(expected[index], actual[index]);
        }
    }

    private void assertBytesNotEqual(byte[] expected, byte[] actual)
    {
        Assert.assertEquals(expected.length, actual.length);

        for (int index = 0; index < expected.length; index++)
        {
            Assert.assertFalse(expected[index] == actual[index]);
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(PropertyFieldTableTest.class);
    }

}
