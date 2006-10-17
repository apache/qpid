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
package org.apache.qpid.server.util;

import junit.framework.JUnit4TestAdapter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.apache.qpid.AMQException;

import java.util.LinkedHashMap;
import java.util.Map;

public class OrderedMapHelperTest
{
    private final Object lock = new Object();
    private final Map<Integer, String> map = new LinkedHashMap<Integer, String>();
    private final OrderedMapHelper<Integer, String> helper = new OrderedMapHelper<Integer, String>(map, lock, 0);

    @Before
    public void setup() throws Exception
    {
        map.put(1, "One");
        map.put(2, "Two");
        map.put(5, "Five");
        map.put(8, "Eight");
        map.put(10, "Ten");
    }

    @Test
    public void specific()
    {
        Map<Integer, String> slice = helper.getValues(5, false);
        assertEquals(1, slice.size());
        assertTrue(slice.containsKey(5));
        assertTrue(slice.containsValue("Five"));
        assertEquals("Five", slice.get(5));
    }

    @Test
    public void multiple()
    {
        Map<Integer, String> slice = helper.getValues(5, true);
        assertEquals(3, slice.size());

        assertTrue(slice.containsKey(1));
        assertTrue(slice.containsKey(2));
        assertTrue(slice.containsKey(5));

        assertEquals("One", slice.get(1));
        assertEquals("Two", slice.get(2));
        assertEquals("Five", slice.get(5));
    }

    @Test
    public void all()
    {
        Map<Integer, String> slice = helper.getValues(0/*the 'wildcard'*/, true);
        assertEquals(5, slice.size());

        assertTrue(slice.containsKey(1));
        assertTrue(slice.containsKey(2));
        assertTrue(slice.containsKey(5));
        assertTrue(slice.containsKey(8));
        assertTrue(slice.containsKey(10));

        assertEquals("One", slice.get(1));
        assertEquals("Two", slice.get(2));
        assertEquals("Five", slice.get(5));
        assertEquals("Eight", slice.get(8));
        assertEquals("Ten", slice.get(10));
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(OrderedMapHelperTest.class);
    }
}
