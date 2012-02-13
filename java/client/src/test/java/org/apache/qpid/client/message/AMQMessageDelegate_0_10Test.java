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

import javax.jms.Destination;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

public class AMQMessageDelegate_0_10Test extends QpidTestCase
{
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
}
