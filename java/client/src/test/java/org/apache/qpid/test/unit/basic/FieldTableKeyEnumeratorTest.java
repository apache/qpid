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
package org.apache.qpid.test.unit.basic;

import org.junit.Test;
import org.junit.Assert;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.TestMessageHelper;


import java.util.Enumeration;

import junit.framework.JUnit4TestAdapter;

import javax.jms.JMSException;

public class FieldTableKeyEnumeratorTest
{
    @Test
    public void testKeyEnumeration()
    {
        FieldTable result = new FieldTable();
        result.put("one", 1L);
        result.put("two", 2L);
        result.put("three", 3L);
        result.put("four", 4L);
        result.put("five", 5L);

        Enumeration e = result.keys();

        Assert.assertTrue("one".equals(e.nextElement()));
        Assert.assertTrue("two".equals(e.nextElement()));
        Assert.assertTrue("three".equals(e.nextElement()));
        Assert.assertTrue("four".equals(e.nextElement()));
        Assert.assertTrue("five".equals(e.nextElement()));
    }

    @Test
    public void testPropertEnu()
    {
        try
        {
            JMSTextMessage text = TestMessageHelper.newJMSTextMessage();

            text.setBooleanProperty("Boolean1", true);
            text.setBooleanProperty("Boolean2", true);
            text.setIntProperty("Int", 2);
            text.setLongProperty("Long", 2);

            Enumeration e = text.getPropertyNames();

            Assert.assertTrue("Boolean1".equals(e.nextElement()));
            Assert.assertTrue("Boolean2".equals(e.nextElement()));
            Assert.assertTrue("Int".equals(e.nextElement()));
            Assert.assertTrue("Long".equals(e.nextElement()));
        }
        catch (JMSException e)
        {

        }
    }

    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(FieldTableKeyEnumeratorTest.class);
    }


}
