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
package org.apache.qpid.framing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import junit.framework.TestCase;
import org.junit.Assert;

import org.apache.qpid.AMQPInvalidClassException;

public class FieldTableTest extends TestCase
{
    /**
     * Test that setting a similar named value replaces any previous value set on that name
     */
    public void testReplacement()
    {
        FieldTable table1 = new FieldTable();
        // Set a boolean value
        table1.setBoolean("value", true);
        // Check length of table is correct (<Value length> + <type> + <Boolean length>)
        int size = EncodingUtils.encodedShortStringLength("value") + 1 + EncodingUtils.encodedBooleanLength();
        Assert.assertEquals(size, table1.getEncodedSize());

        // reset value to an integer
        table1.setInteger("value", Integer.MAX_VALUE);

        // Check the length has changed accordingly   (<Value length> + <type> + <Integer length>)
        size = EncodingUtils.encodedShortStringLength("value") + 1 + EncodingUtils.encodedIntegerLength();
        Assert.assertEquals(size, table1.getEncodedSize());

        // Check boolean value is null
        Assert.assertEquals(null, table1.getBoolean("value"));
        // ... and integer value is good
        Assert.assertEquals((Integer) Integer.MAX_VALUE, table1.getInteger("value"));
    }

    /**
     * Set a boolean and check that we can only get it back as a boolean and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testBoolean()
    {
        FieldTable table1 = new FieldTable();
        table1.setBoolean("value", true);
        Assert.assertTrue(table1.propertyExists("value"));

        // Test Getting right value back
        Assert.assertEquals((Boolean) true, table1.getBoolean("value"));

        // Check we don't get anything back for other gets
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // except value as a string
        Assert.assertEquals("true", table1.getString("value"));

        table1.remove("value");

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getBoolean("Rubbish"));
    }

    /**
     * Set a byte and check that we can only get it back as a byte and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testByte()
    {
        FieldTable table1 = new FieldTable();
        table1.setByte("value", Byte.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tests lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(Byte.valueOf(Byte.MAX_VALUE), table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Byte.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getByte("Rubbish"));
    }

    /**
     * Set a short and check that we can only get it back as a short and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testShort()
    {
        FieldTable table1 = new FieldTable();
        table1.setShort("value", Short.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tests lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(Short.valueOf(Short.MAX_VALUE), table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Short.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getShort("Rubbish"));
    }

    /**
     * Set a char and check that we can only get it back as a char
     * Check that attempting to lookup a non existent value returns null
     */
    public void testChar()
    {
        FieldTable table1 = new FieldTable();
        table1.setChar("value", 'c');
        Assert.assertTrue(table1.propertyExists("value"));

        // Tests lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(Character.valueOf('c'), table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("c", table1.getString("value"));

        table1.remove("value");

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getCharacter("Rubbish"));
    }

    /**
     * Set a double and check that we can only get it back as a double
     * Check that attempting to lookup a non existent value returns null
     */
    public void testDouble()
    {
        FieldTable table1 = new FieldTable();
        table1.setDouble("value", Double.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tests lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(Double.valueOf(Double.MAX_VALUE), table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Double.MAX_VALUE, table1.getString("value"));
        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getDouble("Rubbish"));
    }

    /**
     * Set a float and check that we can only get it back as a float
     * Check that attempting to lookup a non existent value returns null
     */
    public void testFloat()
    {
        FieldTable table1 = new FieldTable();
        table1.setFloat("value", Float.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tests lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(Float.valueOf(Float.MAX_VALUE), table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Float.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getFloat("Rubbish"));
    }

    /**
     * Set an int and check that we can only get it back as an int
     * Check that attempting to lookup a non existent value returns null
     */
    public void testInt()
    {
        FieldTable table1 = new FieldTable();
        table1.setInteger("value", Integer.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tets lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Integer.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getInteger("Rubbish"));
    }

    /**
     * Set a long and check that we can only get it back as a long
     * Check that attempting to lookup a non existent value returns null
     */
    public void testLong()
    {
        FieldTable table1 = new FieldTable();
        table1.setLong("value", Long.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tets lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));

        // ... and a the string value of it.
        Assert.assertEquals("" + Long.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getLong("Rubbish"));
    }

    /**
     * Set a double and check that we can only get it back as a double
     * Check that attempting to lookup a non existent value returns null
     */
    public void testBytes()
    {
        byte[] bytes = { 99, 98, 97, 96, 95 };

        FieldTable table1 = new FieldTable();
        table1.setBytes("value", bytes);
        Assert.assertTrue(table1.propertyExists("value"));

        // Tets lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        assertBytesEqual(bytes, table1.getBytes("value"));

        // ... and a the string value of it is null
        Assert.assertEquals(null, table1.getString("value"));

        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getBytes("Rubbish"));
    }

    /**
     * Calls all methods that can be used to check the table is empty
     * - getEncodedSize
     * - isEmpty
     * - length
     *
     * @param table to check is empty
     */
    private void checkEmpty(FieldTable table)
    {
        Assert.assertEquals(0, table.getEncodedSize());
        Assert.assertTrue(table.isEmpty());
        Assert.assertEquals(0, table.size());

        Assert.assertEquals(0, table.keySet().size());
    }

    /**
     * Set a String and check that we can only get it back as a String
     * Check that attempting to lookup a non existent value returns null
     */
    public void testString()
    {
        FieldTable table1 = new FieldTable();
        table1.setString("value", "Hello");
        Assert.assertTrue(table1.propertyExists("value"));

        // Test lookups we shouldn't get anything back for other gets
        // we should get right value back for this type ....
        Assert.assertEquals(null, table1.getBoolean("value"));
        Assert.assertEquals(null, table1.getByte("value"));
        Assert.assertEquals(null, table1.getShort("value"));
        Assert.assertEquals(null, table1.getCharacter("value"));
        Assert.assertEquals(null, table1.getDouble("value"));
        Assert.assertEquals(null, table1.getFloat("value"));
        Assert.assertEquals(null, table1.getInteger("value"));
        Assert.assertEquals(null, table1.getLong("value"));
        Assert.assertEquals(null, table1.getBytes("value"));
        Assert.assertEquals("Hello", table1.getString("value"));

        // Try setting a null value and read it back
        table1.setString("value", null);

        Assert.assertEquals(null, table1.getString("value"));

        // but still contains the value
        Assert.assertTrue(table1.containsKey("value"));

        table1.remove("value");
        // but after a removeKey it doesn't
        Assert.assertFalse(table1.containsKey("value"));

        checkEmpty(table1);

        // Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getString("Rubbish"));

        // Additional Test that haven't been covered for string
        table1.setObject("value", "Hello");
        // Check that it was set correctly
        Assert.assertEquals("Hello", table1.getString("value"));
    }

    /** Check that a nested field table parameter correctly encodes and decodes to a byte buffer. */
    public void testNestedFieldTable() throws IOException
    {
        byte[] testBytes = new byte[] { 0, 1, 2, 3, 4, 5 };

        FieldTable outerTable = new FieldTable();
        FieldTable innerTable = new FieldTable();

        // Put some stuff in the inner table.
        innerTable.setBoolean("bool", true);
        innerTable.setByte("byte", Byte.MAX_VALUE);
        innerTable.setBytes("bytes", testBytes);
        innerTable.setChar("char", 'c');
        innerTable.setDouble("double", Double.MAX_VALUE);
        innerTable.setFloat("float", Float.MAX_VALUE);
        innerTable.setInteger("int", Integer.MAX_VALUE);
        innerTable.setLong("long", Long.MAX_VALUE);
        innerTable.setShort("short", Short.MAX_VALUE);
        innerTable.setString("string", "hello");
        innerTable.setString("null-string", null);
        innerTable.setFieldArray("field-array",Arrays.asList("hello",Integer.valueOf(42), Collections.emptyList()));

        // Put the inner table in the outer one.
        outerTable.setFieldTable("innerTable", innerTable);

        // Write the outer table into the buffer.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        outerTable.writeToBuffer(new DataOutputStream(baos));

        byte[] data = baos.toByteArray();

        // Extract the table back from the buffer again.
        try
        {
            FieldTable extractedOuterTable = EncodingUtils.readFieldTable(new DataInputStream(new ByteArrayInputStream(data)));

            FieldTable extractedTable = extractedOuterTable.getFieldTable("innerTable");

            Assert.assertEquals(Boolean.TRUE, extractedTable.getBoolean("bool"));
            Assert.assertEquals(Byte.valueOf(Byte.MAX_VALUE), extractedTable.getByte("byte"));
            assertBytesEqual(testBytes, extractedTable.getBytes("bytes"));
            Assert.assertEquals(Character.valueOf('c'), extractedTable.getCharacter("char"));
            Assert.assertEquals(Double.valueOf(Double.MAX_VALUE), extractedTable.getDouble("double"));
            Assert.assertEquals(Float.valueOf(Float.MAX_VALUE), extractedTable.getFloat("float"));
            Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), extractedTable.getInteger("int"));
            Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), extractedTable.getLong("long"));
            Assert.assertEquals(Short.valueOf(Short.MAX_VALUE), extractedTable.getShort("short"));
            Assert.assertEquals("hello", extractedTable.getString("string"));
            Assert.assertNull(extractedTable.getString("null-string"));
            Collection fieldArray = (Collection) extractedTable.get("field-array");
            Assert.assertEquals(3, fieldArray.size());
            Iterator iter = fieldArray.iterator();
            assertEquals("hello",iter.next());
            assertEquals(Integer.valueOf(42), iter.next());
            assertTrue(((Collection)iter.next()).isEmpty());
        }
        catch (AMQFrameDecodingException e)
        {
            fail("Failed to decode field table with nested inner table.");
        }
    }

    public void testValues()
    {
        FieldTable table = new FieldTable();
        table.setBoolean("bool", true);
        table.setByte("byte", Byte.MAX_VALUE);
        byte[] bytes = { 99, 98, 97, 96, 95 };
        table.setBytes("bytes", bytes);
        table.setChar("char", 'c');
        table.setDouble("double", Double.MAX_VALUE);
        table.setFloat("float", Float.MAX_VALUE);
        table.setInteger("int", Integer.MAX_VALUE);
        table.setLong("long", Long.MAX_VALUE);
        table.setShort("short", Short.MAX_VALUE);
        table.setString("string", "Hello");
        table.setString("null-string", null);

        table.setObject("object-bool", true);
        table.setObject("object-byte", Byte.MAX_VALUE);
        table.setObject("object-bytes", bytes);
        table.setObject("object-char", 'c');
        table.setObject("object-double", Double.MAX_VALUE);
        table.setObject("object-float", Float.MAX_VALUE);
        table.setObject("object-int", Integer.MAX_VALUE);
        table.setObject("object-long", Long.MAX_VALUE);
        table.setObject("object-short", Short.MAX_VALUE);
        table.setObject("object-string", "Hello");

        try
        {
            table.setObject("Null-object", null);
            fail("null values are not allowed");
        }
        catch (AMQPInvalidClassException aice)
        {
            assertEquals("Null values are not allowed to be set",
                    AMQPInvalidClassException.INVALID_OBJECT_MSG + "null", aice.getMessage());
        }

        try
        {
            table.setObject("Unsupported-object", new Exception());
            fail("Non primitive values are not allowed");
        }
        catch (AMQPInvalidClassException aice)
        {
            assertEquals("Non primitive values are not allowed to be set",
                    AMQPInvalidClassException.INVALID_OBJECT_MSG + Exception.class, aice.getMessage());
        }

        Assert.assertEquals(Boolean.TRUE, table.getBoolean("bool"));
        Assert.assertEquals(Byte.valueOf(Byte.MAX_VALUE), table.getByte("byte"));
        assertBytesEqual(bytes, table.getBytes("bytes"));
        Assert.assertEquals(Character.valueOf('c'), table.getCharacter("char"));
        Assert.assertEquals(Double.valueOf(Double.MAX_VALUE), table.getDouble("double"));
        Assert.assertEquals(Float.valueOf(Float.MAX_VALUE), table.getFloat("float"));
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), table.getInteger("int"));
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), table.getLong("long"));
        Assert.assertEquals(Short.valueOf(Short.MAX_VALUE), table.getShort("short"));
        Assert.assertEquals("Hello", table.getString("string"));
        Assert.assertNull(table.getString("null-string"));

        Assert.assertEquals(true, table.getObject("object-bool"));
        Assert.assertEquals(Byte.MAX_VALUE, table.getObject("object-byte"));
        assertBytesEqual(bytes, (byte[]) table.getObject("object-bytes"));
        Assert.assertEquals('c', table.getObject("object-char"));
        Assert.assertEquals(Double.MAX_VALUE, table.getObject("object-double"));
        Assert.assertEquals(Float.MAX_VALUE, table.getObject("object-float"));
        Assert.assertEquals(Integer.MAX_VALUE, table.getObject("object-int"));
        Assert.assertEquals(Long.MAX_VALUE, table.getObject("object-long"));
        Assert.assertEquals(Short.MAX_VALUE, table.getObject("object-short"));
        Assert.assertEquals("Hello", table.getObject("object-string"));
    }

    public void testWriteBuffer() throws IOException
    {
        byte[] bytes = { 99, 98, 97, 96, 95 };

        FieldTable table = new FieldTable();
        table.setBoolean("bool", true);
        table.setByte("byte", Byte.MAX_VALUE);

        table.setBytes("bytes", bytes);
        table.setChar("char", 'c');
        table.setInteger("int", Integer.MAX_VALUE);
        table.setLong("long", Long.MAX_VALUE);
        table.setDouble("double", Double.MAX_VALUE);
        table.setFloat("float", Float.MAX_VALUE);
        table.setShort("short", Short.MAX_VALUE);
        table.setString("string", "hello");
        table.setString("null-string", null);


        ByteArrayOutputStream baos = new ByteArrayOutputStream((int) table.getEncodedSize() + 4);
        table.writeToBuffer(new DataOutputStream(baos));

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);


        long length = dis.readInt() & 0xFFFFFFFFL;

        FieldTable table2 = new FieldTable(dis, length);

        Assert.assertEquals((Boolean) true, table2.getBoolean("bool"));
        Assert.assertEquals((Byte) Byte.MAX_VALUE, table2.getByte("byte"));
        assertBytesEqual(bytes, table2.getBytes("bytes"));
        Assert.assertEquals((Character) 'c', table2.getCharacter("char"));
        Assert.assertEquals(Double.valueOf(Double.MAX_VALUE), table2.getDouble("double"));
        Assert.assertEquals(Float.valueOf(Float.MAX_VALUE), table2.getFloat("float"));
        Assert.assertEquals(Integer.valueOf(Integer.MAX_VALUE), table2.getInteger("int"));
        Assert.assertEquals(Long.valueOf(Long.MAX_VALUE), table2.getLong("long"));
        Assert.assertEquals(Short.valueOf(Short.MAX_VALUE), table2.getShort("short"));
        Assert.assertEquals("hello", table2.getString("string"));
        Assert.assertNull(table2.getString("null-string"));
    }

    public void testEncodingSize()
    {
        FieldTable result = new FieldTable();
        int size = 0;

        result.setBoolean("boolean", true);
        size += 1 + EncodingUtils.encodedShortStringLength("boolean") + EncodingUtils.encodedBooleanLength();
        Assert.assertEquals(size, result.getEncodedSize());

        result.setByte("byte", (byte) Byte.MAX_VALUE);
        size += 1 + EncodingUtils.encodedShortStringLength("byte") + EncodingUtils.encodedByteLength();
        Assert.assertEquals(size, result.getEncodedSize());

        byte[] _bytes = { 99, 98, 97, 96, 95 };

        result.setBytes("bytes", _bytes);
        size += 1 + EncodingUtils.encodedShortStringLength("bytes") + 4 + _bytes.length;
        Assert.assertEquals(size, result.getEncodedSize());

        result.setChar("char", (char) 'c');
        size += 1 + EncodingUtils.encodedShortStringLength("char") + EncodingUtils.encodedCharLength();
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
        size += 1 + EncodingUtils.encodedShortStringLength("object-bytes") + 4 + _bytes.length;
        Assert.assertEquals(size, result.getEncodedSize());

        result.setObject("object-char", 'c');
        size += 1 + EncodingUtils.encodedShortStringLength("object-char") + EncodingUtils.encodedCharLength();
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

    /**
     * Additional test for setObject
     */
    public void testSetObject()
    {
        FieldTable table = new FieldTable();

        // Try setting a non primative object

        try
        {
            table.setObject("value", this);
            fail("Only primative values allowed in setObject");
        }
        catch (AMQPInvalidClassException iae)
        {
            // normal path
        }
        // so length should be zero
        Assert.assertEquals(0, table.getEncodedSize());
    }

    /**
     * Additional test checkPropertyName doesn't accept Null
     */
    public void testCheckPropertyNameasNull()
    {
        FieldTable table = new FieldTable();

        try
        {
            table.setObject((String) null, "String");
            fail("Null property name is not allowed");
        }
        catch (IllegalArgumentException iae)
        {
            // normal path
        }
        // so length should be zero
        Assert.assertEquals(0, table.getEncodedSize());
    }

    /**
     * Additional test checkPropertyName doesn't accept an empty String
     */
    public void testCheckPropertyNameasEmptyString()
    {
        FieldTable table = new FieldTable();

        try
        {
            table.setObject("", "String");
            fail("empty property name is not allowed");
        }
        catch (IllegalArgumentException iae)
        {
            // normal path
        }
        // so length should be zero
        Assert.assertEquals(0, table.getEncodedSize());
    }

    /**
     * Additional test checkPropertyName doesn't accept an empty String
     */
    public void testCheckPropertyNamehasMaxLength()
    {
        FieldTable table = new FieldTable(true);

        StringBuffer longPropertyName = new StringBuffer(129);

        for (int i = 0; i < 129; i++)
        {
            longPropertyName.append("x");
        }

        try
        {
            table.setObject(longPropertyName.toString(), "String");
            fail("property name must be < 128 characters");
        }
        catch (IllegalArgumentException iae)
        {
            // normal path
        }
        // so length should be zero
        Assert.assertEquals(0, table.getEncodedSize());
    }

    /**
     * Additional test checkPropertyName starts with a letter
     */
    public void testCheckPropertyNameStartCharacterIsLetter()
    {
        FieldTable table = new FieldTable(true);

        // Try a name that starts with a number
        try
        {
            table.setObject("1", "String");
            fail("property name must start with a letter");
        }
        catch (IllegalArgumentException iae)
        {
            // normal path
        }
        // so length should be zero
        Assert.assertEquals(0, table.getEncodedSize());
    }

    /**
     * Additional test checkPropertyName starts with a hash or a dollar
     */
    public void testCheckPropertyNameStartCharacterIsHashorDollar()
    {
        FieldTable table = new FieldTable(true);

        // Try a name that starts with a number
        try
        {
            table.setObject("#", "String");
            table.setObject("$", "String");
        }
        catch (IllegalArgumentException iae)
        {
            fail("property name are allowed to start with # and $s");
        }
    }

    /**
     * Additional test to test the contents of the table
     */
    public void testContents()
    {
        FieldTable table = new FieldTable();

        table.setObject("StringProperty", "String");

        Assert.assertEquals("String", table.getString("StringProperty"));

        // Test Clear

        table.clear();

        checkEmpty(table);
    }

    /**
     * Test the contents of the sets
     */
    public void testSets()
    {

        FieldTable table = new FieldTable();

        table.setObject("n1", "1");
        table.setObject("n2", "2");
        table.setObject("n3", "3");

        Assert.assertEquals("1", table.getObject("n1"));
        Assert.assertEquals("2", table.getObject("n2"));
        Assert.assertEquals("3", table.getObject("n3"));
    }

    public void testAddAll()
    {
        final FieldTable table1 = new FieldTable();
        table1.setInteger("int1", 1);
        table1.setInteger("int2", 2);
        assertEquals("Unexpected number of entries in table1", 2, table1.size());

        final FieldTable table2 = new FieldTable();
        table2.setInteger("int3", 3);
        table2.setInteger("int4", 4);
        assertEquals("Unexpected number of entries in table2", 2, table2.size());

        table1.addAll(table2);
        assertEquals("Unexpected number of entries in table1 after addAll", 4, table1.size());
        assertEquals(Integer.valueOf(3), table1.getInteger("int3"));
    }

    public void testAddAllWithEmptyFieldTable()
    {
        final FieldTable table1 = new FieldTable();
        table1.setInteger("int1", 1);
        table1.setInteger("int2", 2);
        assertEquals("Unexpected number of entries in table1", 2, table1.size());

        final FieldTable emptyFieldTable = new FieldTable();

        table1.addAll(emptyFieldTable);
        assertEquals("Unexpected number of entries in table1 after addAll", 2, table1.size());
    }

    /**
     * Tests that when copying properties into a new FielTable using the addAll() method, the
     *  properties are successfully added to the destination table when the source FieldTable
     * was created from encoded input bytes,
     */
    public void testAddingAllFromFieldTableCreatedUsingEncodedBytes() throws Exception
    {
        AMQShortString myBooleanTestProperty = new AMQShortString("myBooleanTestProperty");

        //Create a new FieldTable and use it to encode data into a byte array.
        FieldTable encodeTable = new FieldTable();
        encodeTable.put(myBooleanTestProperty, true);
        byte[] data = encodeTable.getDataAsBytes();
        int length = data.length;

        //Verify we got the expected mount of encoded data (1B type hdr + 21B for name + 1B type hdr + 1B for boolean)
        assertEquals("unexpected data length", 24, length);

        //Create a second FieldTable from the encoded bytes
        FieldTable tableFromBytes = new FieldTable(new DataInputStream(new ByteArrayInputStream(data)), length);

        //Create a final FieldTable and addAll() from the table created with encoded bytes
        FieldTable destinationTable = new FieldTable();
        assertTrue("unexpected size", destinationTable.isEmpty());
        destinationTable.addAll(tableFromBytes);

        //Verify that the destination table now contains the expected entry
        assertEquals("unexpected size", 1, destinationTable.size());
        assertTrue("expected property not present", destinationTable.containsKey(myBooleanTestProperty));
        assertTrue("unexpected property value", destinationTable.getBoolean(myBooleanTestProperty));
    }

    private void assertBytesEqual(byte[] expected, byte[] actual)
    {
        Assert.assertEquals(expected.length, actual.length);

        for (int index = 0; index < expected.length; index++)
        {
            Assert.assertEquals(expected[index], actual[index]);
        }
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(FieldTableTest.class);
    }

}
