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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidSelectorException;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQMethodListener;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

public class MessageConsumeHandler implements StateAwareMethodListener<MessageConsumeBody>
{
    private static final Logger _log = Logger.getLogger(MessageConsumeHandler.class);

    private static MessageConsumeHandler _instance = new MessageConsumeHandler();

    public static MessageConsumeHandler getInstance()
    {
        return _instance;
    }

    private MessageConsumeHandler() {}


    public void methodReceived (AMQStateManager stateManager,
                                QueueRegistry queueRegistry,
                              	ExchangeRegistry exchangeRegistry,
                                AMQProtocolSession session,
                               	AMQMethodEvent<MessageConsumeBody> evt)
                                throws AMQException
    {
        MessageConsumeBody body = evt.getMethod();
        final int channelId = evt.getChannelId();

        AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            _log.error("Channel " + channelId + " not found");
            // TODO: either alert or error that the
        }
        else
        {
            AMQQueue queue = body.queue == null ? channel.getDefaultQueue() : queueRegistry.getQueue(body.queue);

            if (queue == null)
            {
                _log.info("No queue for '" + body.queue + "'");
                if(body.queue!=null)
                {
                    channelClose(session, channelId, stateManager,
                                 "No such queue, '" + body.queue + "'", AMQConstant.NOT_FOUND);
                }
                else
                {
                    connectionClose(session, channelId, stateManager,
                                    "No queue name provided, no default queue defined.",
                                    AMQConstant.NOT_ALLOWED);
                }
            }
            else
            {
                try
                {
                    /*AMQShort*/String destination = channel.subscribeToQueue
                        (body.destination, queue, session, !body.noAck, /*XXX*/null, body.noLocal);
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    session.writeResponse(evt, MessageOkBody.createMethodBody((byte)0, (byte)9));

                    //now allow queue to start async processing of any backlog of messages
                    queue.deliverAsync();
                }
                catch (AMQInvalidSelectorException ise)
                {
                    _log.info("Closing connection due to invalid selector");
                    channelClose(session, channelId, stateManager, ise.getMessage(), AMQConstant.INVALID_SELECTOR);
                }
                catch (ConsumerTagNotUniqueException e)
                {
                    connectionClose(session, channelId, stateManager,
                                    "Non-unique consumer tag, '" + body.destination + "'",
                                    AMQConstant.NOT_ALLOWED);
                }
            }
        }
    }

    private void channelClose(AMQProtocolSession session, int channelId, AMQMethodListener listener,
                              String message, AMQConstant code)
        throws AMQException
    {
        /*AMQShort*/String msg = new /*AMQShort*/String(message);
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        session.writeRequest(channelId, ChannelCloseBody.createMethodBody
                             ((byte)0, (byte)9,	// AMQP version (major, minor)
                              MessageConsumeBody.getClazz((byte)0, (byte)9),	// classId
                              MessageConsumeBody.getMethod((byte)0, (byte)9),	// methodId
                              code.getCode(),	// replyCode
                              msg),	// replyText
                             listener);
    }

    private void connectionClose(AMQProtocolSession session, int channelId, AMQMethodListener listener,
                                 String message, AMQConstant code)
        throws AMQException
    {
        /*AMQShort*/String msg = new /*AMQShort*/String(message);
        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        session.writeRequest(channelId, ConnectionCloseBody.createMethodBody
                             ((byte)0, (byte)9,	// AMQP version (major, minor)
                              MessageConsumeBody.getClazz((byte)0, (byte)9),	// classId
                              MessageConsumeBody.getMethod((byte)0, (byte)9),	// methodId
                              code.getCode(),	// replyCode
                              msg),	// replyText
                             listener);
    }

}

