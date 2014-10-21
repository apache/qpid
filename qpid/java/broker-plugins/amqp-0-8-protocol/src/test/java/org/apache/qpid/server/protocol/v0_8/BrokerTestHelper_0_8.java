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
package org.apache.qpid.server.protocol.v0_8;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class BrokerTestHelper_0_8 extends BrokerTestHelper
{

    public static AMQChannel createChannel(int channelId, AMQProtocolEngine session) throws AMQException
    {
        AMQChannel channel = new AMQChannel(session, channelId, session.getVirtualHost().getMessageStore());
        session.addChannel(channel);
        return channel;
    }

    public static AMQChannel createChannel(int channelId) throws Exception
    {
        InternalTestProtocolSession session = createProtocolSession();
        return createChannel(channelId, session);
    }

    public static AMQChannel createChannel() throws Exception
    {
        return createChannel(1);
    }

    public static InternalTestProtocolSession createProtocolSession() throws Exception
    {
        return createProtocolSession("test");
    }

    public static InternalTestProtocolSession createProtocolSession(String hostName) throws Exception
    {
        VirtualHostImpl virtualHost = createVirtualHost(hostName);

        AmqpPort port = mock(AmqpPort.class);
        when(port.getContextValue(eq(Integer.class), eq(AmqpPort.PORT_MAX_MESSAGE_SIZE))).thenReturn(AmqpPort.DEFAULT_MAX_MESSAGE_SIZE);
        return new InternalTestProtocolSession(virtualHost, createBrokerMock(), port);
    }

    public static void publishMessages(AMQChannel channel, int numberOfMessages, String queueName, String exchangeName)
            throws AMQException
    {
        AMQShortString routingKey = new AMQShortString(queueName);
        AMQShortString exchangeNameAsShortString = new AMQShortString(exchangeName);
        MessagePublishInfo info = new MessagePublishInfo(exchangeNameAsShortString, false, false, routingKey);

        MessageDestination destination;
        if(exchangeName == null || "".equals(exchangeName))
        {
            destination = channel.getVirtualHost().getDefaultDestination();
        }
        else
        {
            destination = channel.getVirtualHost().getExchange(exchangeName);
        }
        for (int count = 0; count < numberOfMessages; count++)
        {
            channel.setPublishFrame(info, destination);


            // Set Minimum properties
            BasicContentHeaderProperties properties = new BasicContentHeaderProperties();


            properties.setExpiration(0L);
            properties.setTimestamp(System.currentTimeMillis());

            // Make Message Persistent
            properties.setDeliveryMode((byte) 2);

            ContentHeaderBody headerBody = new ContentHeaderBody(properties, 0);

            channel.publishContentHeader(headerBody);
        }
        channel.sync();
    }
}
