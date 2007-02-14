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
import org.apache.qpid.AMQInvalidSelectorException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageEmptyBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import org.apache.log4j.Logger;

public class MessageGetHandler implements StateAwareMethodListener<MessageGetBody>
{
    private static final Logger _logger = Logger.getLogger(MessageGetHandler.class);

    private static MessageGetHandler _instance = new MessageGetHandler();

    public static MessageGetHandler getInstance()
    {
        return _instance;
    }

    private MessageGetHandler() {}
    
    public void methodReceived (AMQStateManager stateManager, AMQMethodEvent<MessageGetBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        final MessageGetBody body = evt.getMethod();
        final int channelId = evt.getChannelId();
        VirtualHost virtualHost = session.getVirtualHost();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        
        AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            _logger.error("Channel " + channelId + " not found");
            // TODO: either alert or error that the
        }
        else
        {
            AMQQueue queue = body.queue == null ? channel.getDefaultQueue() : queueRegistry.getQueue(body.queue);

            if (queue == null)
            {
                _logger.info("No queue for '" + body.queue + "'");
                if(body.queue != null)
                {
                    session.closeChannelRequest(evt.getChannelId(), AMQConstant.NOT_FOUND.getCode(),
                        new AMQShortString("No such queue, '" + body.queue + "'"));
                }
                else
                {
                    session.closeSessionRequest(AMQConstant.NOT_ALLOWED.getCode(),
                        new AMQShortString("No queue name provided, no default queue defined."),
                        body.getClazz(), body.getMethod());
                }
            }
            else
            {
                try
                {
                    if(queue.performGet(session, channel, !body.noAck))
                    {
                        session.writeResponse(evt, MessageOkBody.createMethodBody(
                            session.getProtocolMajorVersion(), // AMQP major version
                            session.getProtocolMinorVersion())); // AMQP minor version
                    }
                    else
                    {
                        session.writeResponse(evt, MessageEmptyBody.createMethodBody(
                            session.getProtocolMajorVersion(), // AMQP major version
                            session.getProtocolMinorVersion())); // AMQP minor version
                    }
                }
                catch (AMQInvalidSelectorException ise)
                {
                    _logger.info("Closing connection due to invalid selector: " + ise.getMessage());
                    session.closeChannelRequest(evt.getChannelId(), AMQConstant.INVALID_SELECTOR.getCode(),
                        new AMQShortString(ise.getMessage()));
                }
//                 catch (ConsumerTagNotUniqueException e)
//                 {
//                     _logger.info("Closing connection due to duplicate (non-unique) consumer tag: " + e.getMessage());
//                     session.closeSessionRequest(AMQConstant.NOT_ALLOWED.getCode(),
//                         new AMQShortString("Non-unique consumer tag, '" + body.destination + "'"),
//                         body.getClazz(), body.getMethod());
//                 }
                catch (AMQQueue.ExistingExclusiveSubscription e)
                {
                    throw body.getChannelException(AMQConstant.ACCESS_REFUSED.getCode(),
                                                  "Cannot subscribe to queue "
                                                          + queue.getName()
                                                          + " as it already has an existing exclusive consumer");
                }
                catch (AMQQueue.ExistingSubscriptionPreventsExclusive e)
                {
                    throw body.getChannelException(AMQConstant.ACCESS_REFUSED.getCode(),
                                                   "Cannot subscribe to queue "
                                                   + queue.getName()
                                                   + " exclusively as it already has a consumer");
                }
            }
        }
    }
}

