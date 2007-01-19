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

import org.apache.qpid.framing.MessageOkBody;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class MessageTransferHandler implements StateAwareMethodListener<MessageTransferBody>
{
    private static final Logger _log = Logger.getLogger(MessageTransferHandler.class);

    private static MessageTransferHandler _instance = new MessageTransferHandler();

    public static MessageTransferHandler getInstance()
    {
        return _instance;
    }

    private static final String UNKNOWN_EXCHANGE_NAME = "Unknown exchange name";

    private MessageTransferHandler() {}

    public void methodReceived (AMQStateManager stateManager,
    							QueueRegistry queueRegistry,
                              	ExchangeRegistry exchangeRegistry,
                                AMQProtocolSession protocolSession,
                               	AMQMethodEvent<MessageTransferBody> evt)
                                throws AMQException
    {
        final MessageTransferBody body = evt.getMethod();

        if (_log.isDebugEnabled()) {
            _log.debug("Publish received on channel " + evt.getChannelId());
        }

        // TODO: check the delivery tag field details - is it unique across the broker or per subscriber?
        if (body.destination == null) {
            body.destination = ExchangeDefaults.DIRECT_EXCHANGE_NAME;
        }
        Exchange e = exchangeRegistry.getExchange(body.destination);
        // if the exchange does not exist we raise a channel exception
        if (e == null) {
            protocolSession.closeChannel(evt.getChannelId());
            // TODO: modify code gen to make getClazz and getMethod public methods rather than protected
            // then we can remove the hardcoded 0,0
            // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQMethodBody cf = ChannelCloseBody.createMethodBody
                ((byte)0, (byte)9,	// AMQP version (major, minor)
                 MessageTransferBody.getClazz((byte)0, (byte)9),	// classId
                 MessageTransferBody.getMethod((byte)0, (byte)9),	// methodId
                 500,	// replyCode
                 UNKNOWN_EXCHANGE_NAME);	// replyText
            protocolSession.writeRequest(evt.getChannelId(), cf, stateManager);
        } else {
            // The partially populated BasicDeliver frame plus the received route body
            // is stored in the channel. Once the final body frame has been received
            // it is routed to the exchange.
            AMQChannel channel = protocolSession.getChannel(evt.getChannelId());
            channel.addMessageTransfer(body, protocolSession);
            protocolSession.writeResponse(evt, MessageOkBody.createMethodBody((byte)0, (byte)9));
        }
    }
}

