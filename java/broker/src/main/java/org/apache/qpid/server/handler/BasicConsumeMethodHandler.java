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
import org.apache.qpid.framing.*;
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

    public void methodReceived(AMQStateManager stateManager, BasicConsumeBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();




        AMQChannel channel = session.getChannel(channelId);

        VirtualHost vHost = session.getVirtualHost();

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        else
        {
            if (_log.isDebugEnabled())
            {
                _log.debug("BasicConsume: from '" + body.getQueue() +
                           "' for:" + body.getConsumerTag() +
                           " nowait:" + body.getNowait() +
                           " args:" + body.getArguments());
            }

            AMQQueue queue = body.getQueue() == null ? channel.getDefaultQueue() : vHost.getQueueRegistry().getQueue(body.getQueue().intern());

            if (queue == null)
            {
                if (_log.isDebugEnabled())
                {
                    _log.debug("No queue for '" + body.getQueue() + "'");
                }
                if (body.getQueue() != null)
                {
                    String msg = "No such queue, '" + body.getQueue() + "'";
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

                final AMQShortString consumerTagName;

                if (body.getConsumerTag() != null)
                {
                    consumerTagName = body.getConsumerTag().intern();
                }
                else
                {
                    consumerTagName = null;
                }

                try
                {
                    AMQShortString consumerTag = channel.subscribeToQueue(consumerTagName, queue, session, !body.getNoAck(),
                                                                          body.getArguments(), body.getNoLocal(), body.getExclusive());
                    if (!body.getNowait())
                    {
                        MethodRegistry methodRegistry = session.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createBasicConsumeOkBody(consumerTag);
                        session.writeFrame(responseBody.generateFrame(channelId));

                    }

                    //now allow queue to start async processing of any backlog of messages
                    queue.deliverAsync();
                }
                catch (org.apache.qpid.AMQInvalidArgumentException ise)
                {
                    _log.debug("Closing connection due to invalid selector");

                    MethodRegistry methodRegistry = session.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createChannelCloseBody(AMQConstant.INVALID_ARGUMENT.getCode(),
                                                                                       new AMQShortString(ise.getMessage()),
                                                                                       body.getClazz(),
                                                                                       body.getMethod());
                    session.writeFrame(responseBody.generateFrame(channelId));


                }
                catch (ConsumerTagNotUniqueException e)
                {
                    AMQShortString msg = new AMQShortString("Non-unique consumer tag, '" + body.getConsumerTag() + "'");

                    MethodRegistry methodRegistry = session.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                             msg,               // replytext
                                                             body.getClazz(),
                                                             body.getMethod());
                    session.writeFrame(responseBody.generateFrame(0));
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
