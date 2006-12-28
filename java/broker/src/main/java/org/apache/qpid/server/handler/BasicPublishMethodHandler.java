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
package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class BasicPublishMethodHandler  implements StateAwareMethodListener<BasicPublishBody>
{
    private static final BasicPublishMethodHandler _instance = new BasicPublishMethodHandler();

    public static BasicPublishMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicPublishMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<BasicPublishBody> evt) throws AMQException
    {
        final BasicPublishBody body = evt.getMethod();

        // TODO: check the delivery tag field details - is it unique across the broker or per subscriber?
        if (body.exchange == null)
        {
            body.exchange = "amq.direct";
        }
        Exchange e = exchangeRegistry.getExchange(body.exchange);
        // if the exchange does not exist we raise a channel exception
        if (e == null)
        {
            protocolSession.closeChannel(evt.getChannelId());
            // TODO: modify code gen to make getClazz and getMethod public methods rather than protected
            // then we can remove the hardcoded 0,0
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQFrame cf = ChannelCloseBody.createAMQFrame(evt.getChannelId(),
                (byte)8, (byte)0,	// AMQP version (major, minor)
                ChannelCloseBody.getClazz((byte)8, (byte)0),	// classId
                ChannelCloseBody.getMethod((byte)8, (byte)0),	// methodId
                500,	// replyCode
                "Unknown exchange name");	// replyText
            protocolSession.writeFrame(cf);
        }
        else
        {
            // The partially populated BasicDeliver frame plus the received route body
            // is stored in the channel. Once the final body frame has been received
            // it is routed to the exchange.
            AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
            channel.setPublishFrame(body, protocolSession);
        }
    }
}

