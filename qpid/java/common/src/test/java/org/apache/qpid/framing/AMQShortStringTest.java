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
 */

package org.apache.qpid.framing;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
public class AMQShortStringTest extends TestCase
{

    public static final AMQShortString HELLO = new AMQShortString("Hello");
    public static final AMQShortString HELL = new AMQShortString("Hell");
    public static final AMQShortString GOODBYE = new AMQShortString("Goodbye");
    public static final AMQShortString GOOD = new AMQShortString("Good");
    public static final AMQShortString BYE = new AMQShortString("BYE");

    public void testStartsWith()
    {
        assertTrue(HELLO.startsWith(HELL));

        assertFalse(HELL.startsWith(HELLO));

        assertTrue(GOODBYE.startsWith(GOOD));

        assertFalse(GOOD.startsWith(GOODBYE));
    }

    public void testEndWith()
    {
        assertFalse(HELL.endsWith(HELLO));

        assertTrue(GOODBYE.endsWith(new AMQShortString("bye")));

        assertFalse(GOODBYE.endsWith(BYE));
    }


    public void testTokenize()
    {
        AMQShortString dotSeparatedWords = new AMQShortString("this.is.a.test.with.1.2.3.-numbers-and-then--dashes-");
        AMQShortStringTokenizer dotTokenizer = dotSeparatedWords.tokenize((byte) '.');

        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(new AMQShortString("this"),(dotTokenizer.nextToken()));
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(new AMQShortString("is"),(dotTokenizer.nextToken()));
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(new AMQShortString("a"),(dotTokenizer.nextToken()));
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(new AMQShortString("test"),(dotTokenizer.nextToken()));
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(new AMQShortString("with"),(dotTokenizer.nextToken()));
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(dotTokenizer.nextToken().toIntValue() , 1);
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(dotTokenizer.nextToken().toIntValue() , 2);
        assertTrue(dotTokenizer.hasMoreTokens());
        assertEquals(dotTokenizer.nextToken().toIntValue() , 3);
        assertTrue(dotTokenizer.hasMoreTokens());
        AMQShortString dashString = dotTokenizer.nextToken();
        assertEquals(new AMQShortString("-numbers-and-then--dashes-"),(dashString));

        AMQShortStringTokenizer dashTokenizer = dashString.tokenize((byte)'-');
        assertEquals(dashTokenizer.countTokens(), 7);

        AMQShortString[] expectedResults = new AMQShortString[]
                                                { AMQShortString.EMPTY_STRING,
                                                  new AMQShortString("numbers"),
                                                  new AMQShortString("and"),
                                                  new AMQShortString("then"),
                                                  AMQShortString.EMPTY_STRING,
                                                  new AMQShortString("dashes"),
                                                  AMQShortString.EMPTY_STRING };

        for(int i = 0; i < 7; i++)
        {
            assertTrue(dashTokenizer.hasMoreTokens());
            assertEquals(dashTokenizer.nextToken(), expectedResults[i]);
        }

        assertFalse(dotTokenizer.hasMoreTokens());
    }


    public void testEquals()
    {
        assertEquals(GOODBYE, new AMQShortString("Goodbye"));
        assertEquals(new AMQShortString("A"), new AMQShortString("A"));
        assertFalse(new AMQShortString("A").equals(new AMQShortString("a")));
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(byte[])}.
     */
    public void testCreateAMQShortStringByteArray()
    {
        byte[] bytes = null;
        try
        {
            bytes = "test".getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            fail("UTF-8 encoding is not supported anymore by JVM:" + e.getMessage());
        }
        AMQShortString string = new AMQShortString(bytes);
        assertEquals("constructed amq short string length differs from expected", 4, string.length());
        assertTrue("constructed amq short string differs from expected", string.equals("test"));
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(java.lang.String)}
     * <p>
     * Tests short string construction from string with length less than 255.
     */
    public void testCreateAMQShortStringString()
    {
        AMQShortString string = new AMQShortString("test");
        assertEquals("constructed amq short string length differs from expected", 4, string.length());
        assertTrue("constructed amq short string differs from expected", string.equals("test"));
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(char[])}.
     * <p>
     * Tests short string construction from char array with length less than 255.
     */
    public void testCreateAMQShortStringCharArray()
    {
        char[] chars = "test".toCharArray();
        AMQShortString string = new AMQShortString(chars);
        assertEquals("constructed amq short string length differs from expected", 4, string.length());
        assertTrue("constructed amq short string differs from expected", string.equals("test"));
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(java.lang.CharSequence)}
     * <p>
     * Tests short string construction from char sequence with length less than 255.
     */
    public void testCreateAMQShortStringCharSequence()
    {
        AMQShortString string = new AMQShortString((CharSequence) "test");
        assertEquals("constructed amq short string length differs from expected", 4, string.length());
        assertTrue("constructed amq short string differs from expected", string.equals("test"));
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(byte[])}.
     * <p>
     * Tests an attempt to create an AMQP short string from byte array with length over 255.
     */
    public void testCreateAMQShortStringByteArrayOver255()
    {
        String test = buildString('a', 256);
        byte[] bytes = null;
        try
        {
            bytes = test.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            fail("UTF-8 encoding is not supported anymore by JVM:" + e.getMessage());
        }
        try
        {
            new AMQShortString(bytes);
            fail("It should not be possible to create AMQShortString with length over 255");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Exception message differs from expected",
                    "Cannot create AMQShortString with number of octets over 255!", e.getMessage());
        }
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(java.lang.String)}
     * <p>
     * Tests an attempt to create an AMQP short string from string with length over 255
     */
    public void testCreateAMQShortStringStringOver255()
    {
        String test = buildString('a', 256);
        try
        {
            new AMQShortString(test);
            fail("It should not be possible to create AMQShortString with length over 255");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Exception message differs from expected",
                    "Cannot create AMQShortString with number of octets over 255!", e.getMessage());
        }
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(char[])}.
     * <p>
     * Tests an attempt to create an AMQP short string from char array with length over 255.
     */
    public void testCreateAMQShortStringCharArrayOver255()
    {
        String test = buildString('a', 256);
        char[] chars = test.toCharArray();
        try
        {
            new AMQShortString(chars);
            fail("It should not be possible to create AMQShortString with length over 255");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Exception message differs from expected",
                    "Cannot create AMQShortString with number of octets over 255!", e.getMessage());
        }
    }

    /**
     * Test method for
     * {@link org.apache.qpid.framing.AMQShortString#AMQShortString(java.lang.CharSequence)}
     * <p>
     * Tests an attempt to create an AMQP short string from char sequence with length over 255.
     */
    public void testCreateAMQShortStringCharSequenceOver255()
    {
        String test = buildString('a', 256);
        try
        {
            new AMQShortString((CharSequence) test);
            fail("It should not be possible to create AMQShortString with length over 255");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Exception message differs from expected",
                    "Cannot create AMQShortString with number of octets over 255!", e.getMessage());
        }
    }

    /**
     * Tests joining of short strings into a short string with length over 255.
     */
    public void testJoinOverflow()
    {
        List<AMQShortString> data = new ArrayList<AMQShortString>();
        for (int i = 0; i < 25; i++)
        {
            data.add(new AMQShortString("test data!"));
        }
        try
        {
            AMQShortString.join(data, new AMQShortString(" "));
            fail("It should not be possible to create AMQShortString with length over 255");
        }
        catch (IllegalArgumentException e)
        {
            assertEquals("Exception message differs from expected",
                    "Cannot create AMQShortString with number of octets over 255!", e.getMessage());
        }
    }

    /**
     * Tests joining of short strings into a short string with length less than 255.
     */
    public void testJoin()
    {
        StringBuilder expected = new StringBuilder();
        List<AMQShortString> data = new ArrayList<AMQShortString>();
        data.add(new AMQShortString("test data 1"));
        expected.append("test data 1");
        data.add(new AMQShortString("test data 2"));
        expected.append(" test data 2");
        AMQShortString result = AMQShortString.join(data, new AMQShortString(" "));
        assertEquals("join result differs from expected", expected.toString(), result.asString());
    }

    /**
     * A helper method to generate a string with given length containing given
     * character
     *
     * @param ch
     *            char to build string with
     * @param length
     *            target string length
     * @return string
     */
    private String buildString(char ch, int length)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            sb.append(ch);
        }
        return sb.toString();
    }

}
