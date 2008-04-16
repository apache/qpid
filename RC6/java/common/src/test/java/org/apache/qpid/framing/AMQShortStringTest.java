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


    public void testEquals()
    {
        assertEquals(GOODBYE, new AMQShortString("Goodbye"));
        assertEquals(new AMQShortString("A"), new AMQShortString("A"));
        assertFalse(new AMQShortString("A").equals(new AMQShortString("a")));
    }


}
