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
package org.apache.qpid.server.protocol.v0_8.handler;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.HashSet;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicConsumeBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class BasicConsumeMethodHandler implements StateAwareMethodListener<BasicConsumeBody>
{
    private static final Logger _logger = Logger.getLogger(BasicConsumeMethodHandler.class);

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
        AMQProtocolSession protocolConnection = stateManager.getProtocolSession();

        AMQChannel channel = protocolConnection.getChannel(channelId);
        VirtualHostImpl<?,?,?> vHost = protocolConnection.getVirtualHost();

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        else
        {
            channel.sync();
            String queueName = body.getQueue() == null ? null : body.getQueue().asString();
            if (_logger.isDebugEnabled())
            {
                _logger.debug("BasicConsume: from '" + queueName +
                              "' for:" + body.getConsumerTag() +
                              " nowait:" + body.getNowait() +
                              " args:" + body.getArguments());
            }

            MessageSource queue = queueName == null ? channel.getDefaultQueue() : vHost.getQueue(queueName);
            final Collection<MessageSource> sources = new HashSet<>();
            if(queue != null)
            {
                sources.add(queue);
            }
            else if(vHost.getContextValue(Boolean.class, "qpid.enableMultiQueueConsumers")
                    && body.getArguments() != null
                    && body.getArguments().get("x-multiqueue") instanceof Collection)
            {
                for(Object object : (Collection<Object>)body.getArguments().get("x-multiqueue"))
                {
                    String sourceName = String.valueOf(object);
                    sourceName = sourceName.trim();
                    if(sourceName.length() != 0)
                    {
                        MessageSource source = vHost.getMessageSource(sourceName);
                        if(source == null)
                        {
                            sources.clear();
                            break;
                        }
                        else
                        {
                            sources.add(source);
                        }
                    }
                }
                queueName = body.getArguments().get("x-multiqueue").toString();
            }

            if (sources.isEmpty())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("No queue for '" + queueName + "'");
                }
                if (queueName != null)
                {
                    String msg = "No such queue, '" + queueName + "'";
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
                    consumerTagName = body.getConsumerTag().intern(false);
                }
                else
                {
                    consumerTagName = null;
                }

                try
                {
                    if(consumerTagName == null || channel.getSubscription(consumerTagName) == null)
                    {

                        AMQShortString consumerTag = channel.consumeFromSource(consumerTagName,
                                                                               sources,
                                                                               !body.getNoAck(),
                                                                               body.getArguments(),
                                                                               body.getExclusive(),
                                                                               body.getNoLocal());
                        if (!body.getNowait())
                        {
                            MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
                            AMQMethodBody responseBody = methodRegistry.createBasicConsumeOkBody(consumerTag);
                            protocolConnection.writeFrame(responseBody.generateFrame(channelId));

                        }
                    }
                    else
                    {
                        AMQShortString msg = AMQShortString.validValueOf("Non-unique consumer tag, '" + body.getConsumerTag() + "'");

                        MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                 msg,               // replytext
                                                                 body.getClazz(),
                                                                 body.getMethod());
                        protocolConnection.writeFrame(responseBody.generateFrame(0));
                    }

                }
                catch (AMQInvalidArgumentException ise)
                {
                    _logger.debug("Closing connection due to invalid selector");

                    MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createChannelCloseBody(AMQConstant.ARGUMENT_INVALID.getCode(),
                                                                                       AMQShortString.validValueOf(ise.getMessage()),
                                                                                       body.getClazz(),
                                                                                       body.getMethod());
                    protocolConnection.writeFrame(responseBody.generateFrame(channelId));


                }
                catch (AMQQueue.ExistingExclusiveConsumer e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " as it already has an existing exclusive consumer");
                }
                catch (AMQQueue.ExistingConsumerPreventsExclusive e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " exclusively as it already has a consumer");
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " permission denied");
                }
                catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " as it already has an incompatible exclusivity policy");
                }

            }
        }
    }
}
