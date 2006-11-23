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
package org.apache.qpid.server.exchange;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;


import java.util.Map;
import java.util.HashMap;

import junit.framework.JUnit4TestAdapter;

/**
 */
public class HeadersBindingTest
{
    private Map<String, String> bindHeaders = new HashMap<String, String>();
    private Map<String, String> matchHeaders = new HashMap<String, String>();

    @Test public void default_1()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void default_2()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void default_3()
    {
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Altered value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void all_1()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void all_2()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void all_3()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void all_4()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void all_5()
    {
        bindHeaders.put("X-match", "all");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_1()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_2()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_3()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_4()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_5()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertTrue(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }

    @Test public void any_6()
    {
        bindHeaders.put("X-match", "any");
        bindHeaders.put("A", "Value of A");
        bindHeaders.put("B", "Value of B");

        matchHeaders.put("A", "Altered value of A");
        matchHeaders.put("B", "Altered value of B");
        matchHeaders.put("C", "Value of C");

        assertFalse(new HeadersBinding(bindHeaders).matches(matchHeaders));
    }
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(HeadersBindingTest.class);
    }

}
