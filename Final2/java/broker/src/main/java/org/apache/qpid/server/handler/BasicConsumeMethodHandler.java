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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.BasicConsumeOkBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.ConsumerTagNotUniqueException;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class BasicConsumeMethodHandler implements StateAwareMethodListener<BasicConsumeBody>
{
    private static final Logger _log = Logger.getLogger(BasicConsumeMethodHandler.class);

    private static final BasicConsumeMethodHandler _instance = new BasicConsumeMethodHandler();

    public static BasicConsumeMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicConsumeMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<BasicConsumeBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();

        BasicConsumeBody body = evt.getMethod();
        final int channelId = evt.getChannelId();

        AMQChannel channel = session.getChannel(channelId);

        VirtualHost vHost = session.getVirtualHost();

        if (channel == null)
        {
            throw body.getChannelNotFoundException(evt.getChannelId());
        }
        else
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("BasicConsume: from '" + body.queue +
                           "' for:" + body.consumerTag +
                           " nowait:" + body.nowait +
                           " args:" + body.arguments);
            }

            AMQQueue queue = body.queue == null ? channel.getDefaultQueue() : vHost.getQueueRegistry().getQueue(body.queue);

            if (queue == null)
            {
                if (_log.isTraceEnabled())
                {
                    _log.trace("No queue for '" + body.queue + "'");
                }
                if (body.queue != null)
                {
                    String msg = "No such queue, '" + body.queue + "'";
                    throw body.getChannelException(AMQConstant.NOT_FOUND, msg);
                }
                else
                {
                    String msg = "No queue name provided, no default queue defined.";
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED, msg);
                }
            }
            else
            {

                if (body.consumerTag != null)
                {
                    body.consumerTag = body.consumerTag.intern();
                }

                try
                {
                    AMQShortString consumerTag = channel.subscribeToQueue(body.consumerTag, queue, session, !body.noAck,
                                                                          body.arguments, body.noLocal, body.exclusive);
                    if (!body.nowait)
                    {
                        // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                        // Be aware of possible changes to parameter order as versions change.
                        session.writeFrame(BasicConsumeOkBody.createAMQFrame(channelId,
                                                                             (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                             consumerTag));        // consumerTag
                    }

                    //now allow queue to start async processing of any backlog of messages
                    queue.deliverAsync();
                }
                catch (org.apache.qpid.AMQInvalidArgumentException ise)
                {
                    _log.debug("Closing connection due to invalid selector");
                    // Why doesn't this ChannelException work.
//                    throw body.getChannelException(AMQConstant.INVALID_ARGUMENT, ise.getMessage());
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    session.writeFrame(ChannelCloseBody.createAMQFrame(channelId,
                                                                       (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                       BasicConsumeBody.getClazz((byte) 8, (byte) 0),    // classId
                                                                       BasicConsumeBody.getMethod((byte) 8, (byte) 0),    // methodId
                                                                       AMQConstant.INVALID_ARGUMENT.getCode(),    // replyCode
                                                                       new AMQShortString(ise.getMessage())));        // replyText
                }
                catch (ConsumerTagNotUniqueException e)
                {
                    AMQShortString msg = new AMQShortString("Non-unique consumer tag, '" + body.consumerTag + "'");
                    // If the above doesn't work then perhaps this is wrong too.
//                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
//                                                      "Non-unique consumer tag, '" + body.consumerTag + "'");
                    // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                    // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                    // Be aware of possible changes to parameter order as versions change.
                    session.writeFrame(ConnectionCloseBody.createAMQFrame(channelId,
                                                                          (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                          BasicConsumeBody.getClazz((byte) 8, (byte) 0),    // classId
                                                                          BasicConsumeBody.getMethod((byte) 8, (byte) 0),    // methodId
                                                                          AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                          msg));    // replyText
                }
                catch (AMQQueue.ExistingExclusiveSubscription e)
                {
                    throw body.getChannelException(AMQConstant.ACCESS_REFUSED,
                                                   "Cannot subscribe to queue "
                                                   + queue.getName()
                                                   + " as it already has an existing exclusive consumer");
                }
                catch (AMQQueue.ExistingSubscriptionPreventsExclusive e)
                {
                    throw body.getChannelException(AMQConstant.ACCESS_REFUSED,
                                                   "Cannot subscribe to queue "
                                                   + queue.getName()
                                                   + " exclusively as it already has a consumer");
                }

            }
        }
    }
}
