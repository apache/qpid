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
package org.apache.qpid.server.exchange;

import java.util.Map;
import java.util.HashMap;

import junit.framework.TestCase;
import org.apache.qpid.framing.FieldTable;

/**
 */
public class HeadersBindingTest extends TestCase
{
    private FieldTable bindHeaders = new FieldTable();
    private FieldTable matchHeaders = new FieldTable();

    public void testDefault_1()
    {
        bindHeaders.setString("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testDefault_2()
    {
        bindHeaders.setString("A", "Value of A");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testDefault_3()
    {
        bindHeaders.setString("A", "Value of A");

        matchHeaders.setString("A", "Altered value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_1()
    {
        bindHeaders.setString("X-match", "all");
        bindHeaders.setString("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_2()
    {
        bindHeaders.setString("X-match", "all");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_3()
    {
        bindHeaders.setString("X-match", "all");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_4()
    {
        bindHeaders.setString("X-match", "all");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");
        matchHeaders.setString("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAll_5()
    {
        bindHeaders.setString("X-match", "all");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_1()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");

        matchHeaders.setString("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_2()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_3()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_4()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Value of B");
        matchHeaders.setString("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_5()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public void testAny_6()
    {
        bindHeaders.setString("X-match", "any");
        bindHeaders.setString("A", "Value of A");
        bindHeaders.setString("B", "Value of B");

        matchHeaders.setString("A", "Altered value of A");
        matchHeaders.setString("B", "Altered value of B");
        matchHeaders.setString("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(HeadersBindingTest.class);
    }
}
