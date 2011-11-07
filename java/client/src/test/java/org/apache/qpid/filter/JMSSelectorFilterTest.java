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
package org.apache.qpid.filter;

import junit.framework.TestCase;

import org.apache.qpid.AMQInternalException;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.client.message.TestMessageHelper;

public class JMSSelectorFilterTest extends TestCase
{

    public void testEmptySelectorFilter() throws Exception
    {
        try
        {
            new JMSSelectorFilter("");
            fail("Should not be able to create a JMSSelectorFilter with an empty selector");
        }
        catch (IllegalArgumentException iae)
        {
            // pass
        }
    }

    public void testNullSelectorFilter() throws Exception
    {
        try
        {
            new JMSSelectorFilter(null);
            fail("Should not be able to create a JMSSelectorFilter with a null selector");
        }
        catch (IllegalArgumentException iae)
        {
            // pass
        }
    }

    public void testInvalidSelectorFilter() throws Exception
    {
        try
        {
            new JMSSelectorFilter("$%^");
            fail("Unparsable selector so expected AMQInternalException to be thrown");
        }
        catch (AMQInternalException amqie)
        {
            // pass
        }
    }

    public void testSimpleSelectorFilter() throws Exception
    {
        MessageFilter simpleSelectorFilter = new JMSSelectorFilter("select=5");

        assertNotNull("Filter object is null", simpleSelectorFilter);
        assertNotNull("Selector string is null", simpleSelectorFilter.getSelector());
        assertEquals("Unexpected selector", "select=5", simpleSelectorFilter.getSelector());
        assertTrue("Filter object is invalid", simpleSelectorFilter != null);

        final JMSTextMessage message = TestMessageHelper.newJMSTextMessage();

        message.setIntProperty("select", 4);
        assertFalse("Selector did match when not expected", simpleSelectorFilter.matches(message));
        message.setIntProperty("select", 5);
        assertTrue("Selector didnt match when expected", simpleSelectorFilter.matches(message));
        message.setIntProperty("select", 6);
        assertFalse("Selector did match when not expected", simpleSelectorFilter.matches(message));
    }

    public void testFailedMatchingFilter() throws Exception
    {
        MessageFilter simpleSelectorFilter = new JMSSelectorFilter("select>4");

        assertNotNull("Filter object is null", simpleSelectorFilter);
        assertNotNull("Selector string is null", simpleSelectorFilter.getSelector());
        assertEquals("Unexpected selector", "select>4", simpleSelectorFilter.getSelector());
        assertTrue("Filter object is invalid", simpleSelectorFilter != null);

        final JMSTextMessage message = TestMessageHelper.newJMSTextMessage();

        message.setStringProperty("select", "5");
        assertFalse("Selector matched when not expected", simpleSelectorFilter.matches(message));
        message.setStringProperty("select", "elephant");
        assertFalse("Selector matched when not expected", simpleSelectorFilter.matches(message));
        message.setBooleanProperty("select", false);
        assertFalse("Selector matched when not expected", simpleSelectorFilter.matches(message));
    }
}
