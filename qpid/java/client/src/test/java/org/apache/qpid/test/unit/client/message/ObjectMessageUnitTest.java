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
package org.apache.qpid.test.unit.client.message;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.qpid.client.message.JMSObjectMessage;
import org.apache.qpid.client.message.TestMessageHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class ObjectMessageUnitTest extends QpidTestCase
{
    private JMSObjectMessage _om;

    protected void setUp() throws Exception
    {
        super.setUp();
        _om = TestMessageHelper.newJMSObjectMessage();
    }

    /**
     * Test that setObject with a primitive works
     */
    public void testSetObjectWithBooleanPrimitive() throws Exception
    {
        _om.setObject(true);

        //make the message readable
        Object object = _om.getObject();

        assertTrue("Unexpected type returned", object instanceof Boolean);
        assertEquals("Unexpected value returned", true, object);
    }

    /**
     * Test that setObject with a serializable Object works
     */
    public void testSetObjectWithString() throws Exception
    {
        _om.setObject("test string");

        //make the message readable
        Object object = _om.getObject();

        assertTrue("Unexpected type returned", object instanceof String);
        assertEquals("Unexpected value returned", "test string", object);
    }

    /**
     * Test that setObject with a Collection of serializable's works, returning
     * the items in the list when deserialized and ignoring any values
     * added to the collection after setObject() is called on the message.
     */
    public void testSetObjectWithArrayListOfInteger() throws Exception
    {
        ArrayList<Integer> list = new ArrayList<Integer>();
        list.add(1234);
        list.add(Integer.MIN_VALUE);
        list.add(Integer.MAX_VALUE);

        _om.setObject(list);

        //add something extra to the list now, and check it isn't in the value read back
        list.add(0);

        //make the message readable

        //retrieve the Object
        Object object = _om.getObject();

        ArrayList<?> returnedList = null;
        if(object instanceof ArrayList<?>)
        {
            returnedList = (ArrayList<?>) object;
        }
        else
        {
            fail("returned object was not an ArrayList");
        }

        //verify the extra added Integer was not present, then remove it from original list again and compare contents with the returned list
        assertFalse("returned list should not have had the value added after setObject() was used", returnedList.contains(0));
        list.remove(Integer.valueOf(0));
        assertTrue("list contents were not equal", Arrays.equals(list.toArray(), returnedList.toArray()));
    }
}
