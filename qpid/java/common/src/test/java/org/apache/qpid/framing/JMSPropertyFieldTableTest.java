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

import org.apache.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;

public class JMSPropertyFieldTableTest extends TestCase
{

    private static final Logger _logger = Logger.getLogger(JMSPropertyFieldTableTest.class);


    public void setUp()
    {
        System.getProperties().setProperty("strict-jms", "true");
    }

    public void tearDown()
    {
        System.getProperties().remove("strict-jms");
    }

    /**
     * Test that setting a similar named value replaces any previous value set on that name
     */
    public void testReplacement() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        //Set a boolean value
        table1.setBoolean("value", true);

        // reset value to an integer
        table1.setInteger("value", Integer.MAX_VALUE);

        //Check boolean value is null
        try
        {
            table1.getBoolean("value");
        }
        catch (MessageFormatException mfe)
        {
            //normal execution
        }
        // ... and integer value is good
        Assert.assertEquals(Integer.MAX_VALUE, table1.getInteger("value"));
    }

    public void testRemoval() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        //Set a boolean value
        table1.setBoolean("value", true);

        Assert.assertTrue(table1.getBoolean("value"));

        table1.remove("value");

        //Check boolean value is null
        try
        {
            table1.getBoolean("value");
        }
        catch (MessageFormatException mfe)
        {
            //normal execution
        }
    }


    /**
     * Set a boolean and check that we can only get it back as a boolean and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testBoolean() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setBoolean("value", true);
        Assert.assertTrue(table1.propertyExists("value"));

        //Test Getting right value back
        Assert.assertEquals(true, table1.getBoolean("value"));

        //Check we don't get anything back for other gets
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getShort("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getDouble("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getInteger("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getLong("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        //except value as a string
        Assert.assertEquals("true", table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value will return false
        Assert.assertFalse(table1.getBoolean("Rubbish"));
    }

    /**
     * Set a byte and check that we can only get it back as a byte and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testByte() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setByte("value", Byte.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getDouble("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        Assert.assertEquals(Byte.MAX_VALUE, (byte) table1.getShort("value"));
        Assert.assertEquals(Byte.MAX_VALUE, (byte) table1.getInteger("value"));
        Assert.assertEquals(Byte.MAX_VALUE, (byte) table1.getLong("value"));
        Assert.assertEquals(Byte.MAX_VALUE, table1.getByte("value"));
        //... and a the string value of it.
        Assert.assertEquals("" + Byte.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value returns null
        try
        {
            table1.getByte("Rubbish");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException mfs)
        {
            //normal Execution
        }

    }


    /**
     * Set a short and check that we can only get it back as a short and a string
     * Check that attempting to lookup a non existent value returns null
     */
    public void testShort() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setShort("value", Short.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....

        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        try
        {
            table1.getDouble("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }


        Assert.assertEquals(Short.MAX_VALUE, (short) table1.getLong("value"));
        Assert.assertEquals(Short.MAX_VALUE, (short) table1.getInteger("value"));
        Assert.assertEquals(Short.MAX_VALUE, table1.getShort("value"));

        //... and a the string value of it.
        Assert.assertEquals("" + Short.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value returns null
        try
        {
            table1.getShort("Rubbish");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException mfe)
        {
            //normal path
        }
    }


    /**
     * Set a double and check that we can only get it back as a double
     * Check that attempting to lookup a non existent value returns null
     */
    public void testDouble() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setDouble("value", Double.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getShort("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getInteger("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getLong("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }

        Assert.assertEquals(Double.MAX_VALUE, table1.getDouble("value"));
        //... and a the string value of it.
        Assert.assertEquals("" + Double.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value returns null
        try
        {
            table1.getDouble("Rubbish");
            fail("Should throw NullPointerException as float.valueOf will try sunreadJavaFormatString");
        }
        catch (NullPointerException mfe)
        {
            //normal path
        }

    }


    /**
     * Set a float and check that we can only get it back as a float
     * Check that attempting to lookup a non existent value returns null
     */
    public void testFloat() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setFloat("value", Float.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getShort("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getInteger("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getLong("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }


        Assert.assertEquals(Float.MAX_VALUE, table1.getFloat("value"));
        Assert.assertEquals(Float.MAX_VALUE, (float) table1.getDouble("value"));

        //... and a the string value of it.
        Assert.assertEquals("" + Float.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value returns null
        try
        {
            table1.getFloat("Rubbish");
            fail("Should throw NullPointerException as float.valueOf will try sunreadJavaFormatString");
        }
        catch (NullPointerException mfe)
        {
            //normal path
        }
    }


    /**
     * Set an int and check that we can only get it back as an int
     * Check that attempting to lookup a non existent value returns null
     */
    public void testInt() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setInteger("value", Integer.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getShort("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getDouble("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }


        Assert.assertEquals(Integer.MAX_VALUE, table1.getLong("value"));

        Assert.assertEquals(Integer.MAX_VALUE, table1.getInteger("value"));

        //... and a the string value of it.
        Assert.assertEquals("" + Integer.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value returns null
        try
        {
            table1.getInteger("Rubbish");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException mfe)
        {
            //normal path
        }
    }


    /**
     * Set a long and check that we can only get it back as a long
     * Check that attempting to lookup a non existent value returns null
     */
    public void testLong() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setLong("value", Long.MAX_VALUE);
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        try
        {
            table1.getBoolean("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getByte("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getShort("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getDouble("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }
        try
        {
            table1.getInteger("value");
            fail("Should throw MessageFormatException");
        }
        catch (MessageFormatException mfs)
        {
            //normal Execution
        }


        Assert.assertEquals(Long.MAX_VALUE, table1.getLong("value"));

        //... and a the string value of it.
        Assert.assertEquals("" + Long.MAX_VALUE, table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        // Table should now have zero length for encoding
        checkEmpty(table1);

        //Looking up an invalid value
        try
        {
            table1.getLong("Rubbish");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException mfs)
        {
            //normal Execution
        }

    }


    /**
     * Calls all methods that can be used to check the table is empty
     * - getEncodedSize
     * - isEmpty
     * - length
     *
     * @param table to check is empty
     */
    private void checkEmpty(JMSPropertyFieldTable table)
    {
        Assert.assertFalse(table.getPropertyNames().hasMoreElements());
    }


    /**
     * Set a String and check that we can only get it back as a String
     * Check that attempting to lookup a non existent value returns null
     */
    public void testString() throws JMSException
    {
        JMSPropertyFieldTable table1 = new JMSPropertyFieldTable(new FieldTable());
        table1.setString("value", "Hello");
        Assert.assertTrue(table1.propertyExists("value"));

        //Tets lookups we shouldn't get anything back for other gets
        //we should get right value back for this type ....
        Assert.assertEquals(false, table1.getBoolean("value"));

        try
        {
            table1.getByte("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }
        try
        {
            table1.getShort("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }
        try
        {
            table1.getDouble("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }
        try
        {
            table1.getFloat("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }
        try
        {
            table1.getInteger("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }
        try
        {
            table1.getLong("value");
            fail("Should throw NumberFormatException");
        }
        catch (NumberFormatException nfs)
        {
            //normal Execution
        }

        Assert.assertEquals("Hello", table1.getString("value"));

        table1.remove("value");
        //but after a remove it doesn't
        Assert.assertFalse(table1.propertyExists("value"));

        checkEmpty(table1);

        //Looking up an invalid value returns null
        Assert.assertEquals(null, table1.getString("Rubbish"));

        //Additional Test that haven't been covered for string
        table1.setObject("value", "Hello");
        //Check that it was set correctly
        Assert.assertEquals("Hello", table1.getString("value"));
    }


    public void testValues() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());
        table.setBoolean("bool", true);
        table.setDouble("double", Double.MAX_VALUE);
        table.setFloat("float", Float.MAX_VALUE);
        table.setInteger("int", Integer.MAX_VALUE);
        table.setLong("long", Long.MAX_VALUE);
        table.setShort("short", Short.MAX_VALUE);
        table.setString("string", "Hello");
        table.setString("nullstring", null);

        table.setObject("objectbool", true);
        table.setObject("objectdouble", Double.MAX_VALUE);
        table.setObject("objectfloat", Float.MAX_VALUE);
        table.setObject("objectint", Integer.MAX_VALUE);
        table.setObject("objectlong", Long.MAX_VALUE);
        table.setObject("objectshort", Short.MAX_VALUE);
        table.setObject("objectstring", "Hello");


        Assert.assertEquals(true, table.getBoolean("bool"));

        Assert.assertEquals(Double.MAX_VALUE, table.getDouble("double"));
        Assert.assertEquals(Float.MAX_VALUE, table.getFloat("float"));
        Assert.assertEquals(Integer.MAX_VALUE, table.getInteger("int"));
        Assert.assertEquals(Long.MAX_VALUE, table.getLong("long"));
        Assert.assertEquals(Short.MAX_VALUE, table.getShort("short"));
        Assert.assertEquals("Hello", table.getString("string"));
        Assert.assertEquals(null, table.getString("null-string"));

        Assert.assertEquals(true, table.getObject("objectbool"));
        Assert.assertEquals(Double.MAX_VALUE, table.getObject("objectdouble"));
        Assert.assertEquals(Float.MAX_VALUE, table.getObject("objectfloat"));
        Assert.assertEquals(Integer.MAX_VALUE, table.getObject("objectint"));
        Assert.assertEquals(Long.MAX_VALUE, table.getObject("objectlong"));
        Assert.assertEquals(Short.MAX_VALUE, table.getObject("objectshort"));
        Assert.assertEquals("Hello", table.getObject("objectstring"));
    }

    /**
     * Additional test checkPropertyName doesn't accept Null
     */
    public void testCheckPropertyNameasNull() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        try
        {
            table.setObject(null, "String");
            fail("Null property name is not allowed");
        }
        catch (IllegalArgumentException iae)
        {
            //normal path
        }
        checkEmpty(table);
    }


    /**
     * Additional test checkPropertyName doesn't accept an empty String
     */
    public void testCheckPropertyNameasEmptyString() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        try
        {
            table.setObject("", "String");
            fail("empty property name is not allowed");
        }
        catch (IllegalArgumentException iae)
        {
            //normal path
        }
        checkEmpty(table);
    }


    /**
     * Additional test checkPropertyName doesn't accept an empty String
     */
    public void testCheckPropertyNamehasMaxLength() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

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
            _logger.warn("JMS requires infinite property names AMQP limits us to 128 characters");
        }

        checkEmpty(table);
    }


    /**
     * Additional test checkPropertyName starts with a letter
     */
    public void testCheckPropertyNameStartCharacterIsLetter() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        //Try a name that starts with a number
        try
        {
            table.setObject("1", "String");
            fail("property name must start with a letter");
        }
        catch (IllegalArgumentException iae)
        {
            //normal path
        }

        checkEmpty(table);
    }

    /**
     * Additional test checkPropertyName starts with a letter
     */
    public void testCheckPropertyNameContainsInvalidCharacter() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        //Try a name that starts with a number
        try
        {
            table.setObject("hello there", "String");
            fail("property name cannot contain spaces");
        }
        catch (IllegalArgumentException iae)
        {
            //normal path
        }

        checkEmpty(table);
    }


    /**
     * Additional test checkPropertyName starts with a letter
     */
    public void testCheckPropertyNameIsInvalid() throws JMSException
    {
        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        //Try a name that starts with a number
        try
        {
            table.setObject("ESCAPE", "String");
            fail("property name must not contains spaces");
        }
        catch (IllegalArgumentException iae)
        {
            //normal path
        }

        checkEmpty(table);
    }

    /**
     * Additional test checkPropertyName starts with a hash or a dollar
     */
    public void testCheckPropertyNameStartCharacterIsHashorDollar() throws JMSException
    {
        _logger.warn("Test:testCheckPropertyNameStartCharacterIsHashorDollar will fail JMS compilance as # and $ are not valid in a jms identifier");
//        JMSPropertyFieldTable table = new JMSPropertyFieldTable();
//
//        //Try a name that starts with a number
//        try
//        {
//            table.setObject("#", "String");
//            table.setObject("$", "String");
//        }
//        catch (IllegalArgumentException iae)
//        {
//            fail("property name are allowed to start with # and $s in AMQP");
//        }
    }

    /**
     * Test the contents of the sets
     */
    public void testSets()
    {

        JMSPropertyFieldTable table = new JMSPropertyFieldTable(new FieldTable());

        table.put("n1", "1");
        table.put("n2", "2");
        table.put("n3", "3");

        Enumeration enumerator = table.getPropertyNames();
        Assert.assertEquals("n1", enumerator.nextElement());
        Assert.assertEquals("n2", enumerator.nextElement());
        Assert.assertEquals("n3", enumerator.nextElement());
        Assert.assertFalse(enumerator.hasMoreElements());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(JMSPropertyFieldTableTest.class);
    }

}
