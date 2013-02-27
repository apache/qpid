/*
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
 */
package org.apache.qpid.disttest.client.property;

import org.apache.qpid.test.utils.QpidTestCase;

public class RandomPropertyValueTest extends QpidTestCase
{
    private RandomPropertyValue _generator;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _generator = new RandomPropertyValue();
        _generator.setUpper(20.0);
        _generator.setLower(10.0);
        _generator.setType("double");
    }

    public void testGetters()
    {
        assertEquals("Unexpected upper boundary", new Double(20.0), _generator.getUpper());
        assertEquals("Unexpected lower boundary", new Double(10.0), _generator.getLower());
        assertEquals("Unexpected type", "double", _generator.getType());
    }

    public void testGetValue()
    {
        Object value = _generator.getValue();
        assertTrue("Unexpected type", value instanceof Double);
        assertTrue("Unexpected value", ((Double) value).doubleValue() >= 10.0
                && ((Double) value).doubleValue() <= 20.0);
    }

    public void testGetValueInt()
    {
        _generator.setType("int");
        Object value = _generator.getValue();
        assertTrue("Unexpected type", value instanceof Integer);
        assertTrue("Unexpected value", ((Integer) value).intValue() >= 10 && ((Integer) value).intValue() <= 20);
    }

    public void testGetValueLong()
    {
        _generator.setType("long");
        Object value = _generator.getValue();
        assertTrue("Unexpected type", value instanceof Long);
        assertTrue("Unexpected value", ((Long) value).longValue() >= 10 && ((Long) value).longValue() <= 20);
    }

    public void testGetValueFloat()
    {
        _generator.setType("float");
        Object value = _generator.getValue();
        assertTrue("Unexpected type", value instanceof Float);
        assertTrue("Unexpected value", ((Float) value).floatValue() >= 10.0 && ((Float) value).floatValue() <= 20.0);
    }
}
