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

    AMQShortString Hello = new AMQShortString("Hello");
    AMQShortString Hell = new AMQShortString("Hell");
    AMQShortString Goodbye = new AMQShortString("Goodbye");
    AMQShortString Good = new AMQShortString("Good");
    AMQShortString Bye = new AMQShortString("Bye");

    public void testStartsWith()
    {
        assertTrue(Hello.startsWith(Hell));

        assertFalse(Hell.startsWith(Hello));

        assertTrue(Goodbye.startsWith(Good));

        assertFalse(Good.startsWith(Goodbye));
    }

    public void testEndWith()
    {
        assertFalse(Hell.endsWith(Hello));

        assertTrue(Goodbye.endsWith(new AMQShortString("bye")));

        assertFalse(Goodbye.endsWith(Bye));
    }


    public void testEquals()
    {
        assertEquals(Goodbye, new AMQShortString("Goodbye"));
        assertEquals(new AMQShortString("A"), new AMQShortString("A"));
        assertFalse(new AMQShortString("A").equals(new AMQShortString("a")));
    }


}
