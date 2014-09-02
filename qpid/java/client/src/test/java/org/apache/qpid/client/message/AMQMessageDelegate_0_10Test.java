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
package org.apache.qpid.client.message;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.jms.Destination;

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

public class AMQMessageDelegate_0_10Test extends QpidTestCase
{

    private static final String MAX_SHORT = "maxShort";
    private static final String MIN_SHORT = "minShort";
    private static final String MAX_INT = "maxInt";
    private static final String MIN_INT = "minInt";
    private static final String MAX_LONG = "maxLong";
    private static final String MIN_LONG = "minLong";

    /**
     * Tests that when two messages arrive with the same ReplyTo exchange and routingKey values,
     * the cache returns the same Destination object from getJMSReplyTo instead of a new one.
     */
    public void testDestinationCache() throws Exception
    {
        //create a message delegate and retrieve the replyTo Destination
        AMQMessageDelegate_0_10 delegate1 = generateMessageDelegateWithReplyTo();
        Destination dest1 = delegate1.getJMSReplyTo();

        //create a new message delegate with the same details, and retrieve the replyTo Destination
        AMQMessageDelegate_0_10 delegate2 = generateMessageDelegateWithReplyTo();
        Destination dest2 = delegate2.getJMSReplyTo();

        //verify that the destination cache means these are the same Destination object
        assertSame("Should have received the same Destination objects", dest1, dest2);
    }


    private AMQMessageDelegate_0_10 generateMessageDelegateWithReplyTo()
    {
        MessageProperties mesProps = new MessageProperties();
        ReplyTo reply = new ReplyTo("amq.direct", "myReplyQueue");
        mesProps.setReplyTo(reply);

        DeliveryProperties delProps = new DeliveryProperties();
        delProps.setExchange("amq.direct");
        delProps.setRoutingKey("myRequestQueue");

        AMQMessageDelegate_0_10 delegate = new AMQMessageDelegate_0_10(mesProps,delProps,1L);
        return delegate;
    }

    public void testMessageProperties() throws Exception
    {
        MessageProperties msgProps = new MessageProperties();

        Map<String, Object> appHeaders = new HashMap<String, Object>();
        appHeaders.put(MAX_SHORT, String.valueOf(Short.MAX_VALUE));
        appHeaders.put(MIN_SHORT, String.valueOf(Short.MIN_VALUE));
        appHeaders.put(MAX_INT, String.valueOf(Integer.MAX_VALUE));
        appHeaders.put(MIN_INT, String.valueOf(Integer.MIN_VALUE));
        appHeaders.put(MAX_LONG, String.valueOf(Long.MAX_VALUE));
        appHeaders.put(MIN_LONG, String.valueOf(Long.MIN_VALUE));

        msgProps.setApplicationHeaders(appHeaders);

        AMQMessageDelegate_0_10 delegate = new AMQMessageDelegate_0_10(msgProps,new DeliveryProperties(),1L);

        assertEquals("Max long value not retrieved successfully", Long.MAX_VALUE, delegate.getLongProperty(MAX_LONG));
        assertEquals("Min long value not retrieved successfully", Long.MIN_VALUE, delegate.getLongProperty(MIN_LONG));
        assertEquals("Max int value not retrieved successfully as long", (long) Integer.MAX_VALUE, delegate.getLongProperty(MAX_INT));
        assertEquals("Min int value not retrieved successfully as long", (long) Integer.MIN_VALUE, delegate.getLongProperty(MIN_INT));
        assertEquals("Max short value not retrieved successfully as long", (long) Short.MAX_VALUE, delegate.getLongProperty(MAX_SHORT));
        assertEquals("Min short value not retrieved successfully as long", (long) Short.MIN_VALUE, delegate.getLongProperty(MIN_SHORT));

        assertEquals("Max int value not retrieved successfully", Integer.MAX_VALUE, delegate.getIntProperty(MAX_INT));
        assertEquals("Min int value not retrieved successfully", Integer.MIN_VALUE, delegate.getIntProperty(MIN_INT));
        assertEquals("Max short value not retrieved successfully as int", (int) Short.MAX_VALUE, delegate.getIntProperty(MAX_SHORT));
        assertEquals("Min short value not retrieved successfully as int", (int) Short.MIN_VALUE, delegate.getIntProperty(MIN_SHORT));

        assertEquals("Max short value not retrieved successfully", Short.MAX_VALUE, delegate.getShortProperty(MAX_SHORT));
        assertEquals("Min short value not retrieved successfully", Short.MIN_VALUE, delegate.getShortProperty(MIN_SHORT));
    }

    // See QPID_3838
    public void testJMSComplainceForQpidProviderProperties() throws Exception
    {
        MessageProperties msgProps = new MessageProperties();
        Map<String, Object> appHeaders = new HashMap<String, Object>();
        appHeaders.put(QpidMessageProperties.QPID_SUBJECT, "Hello");
        msgProps.setApplicationHeaders(appHeaders);

        System.setProperty("strict-jms", "true");
        try
        {
            AMQMessageDelegate_0_10 delegate = new AMQMessageDelegate_0_10(AMQDestination.DestSyntax.ADDR,msgProps,new DeliveryProperties(),1L);

            boolean propFound = false;
            for (Enumeration props = delegate.getPropertyNames(); props.hasMoreElements();)
            {
                String key = (String)props.nextElement();
                if (key.equals(QpidMessageProperties.QPID_SUBJECT_JMS_PROPERTY))
                {
                    propFound = true;
                }
            }
            assertTrue("qpid.subject was not prefixed with 'JMS_' as expected",propFound);
            assertEquals("qpid.subject should still return the correct value","Hello",delegate.getStringProperty(QpidMessageProperties.QPID_SUBJECT));
        }
        finally
        {
            System.setProperty("strict-jms", "false");
        }
    }
}
