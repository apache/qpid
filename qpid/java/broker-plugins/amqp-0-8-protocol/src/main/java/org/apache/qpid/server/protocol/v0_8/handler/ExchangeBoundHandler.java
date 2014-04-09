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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ExchangeBoundBody;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

/**
 * @author Apache Software Foundation
 *
 *
 */
public class ExchangeBoundHandler implements StateAwareMethodListener<ExchangeBoundBody>
{
    private static final ExchangeBoundHandler _instance = new ExchangeBoundHandler();

    public static final int OK = 0;

    public static final int EXCHANGE_NOT_FOUND = 1;

    public static final int QUEUE_NOT_FOUND = 2;

    public static final int NO_BINDINGS = 3;

    public static final int QUEUE_NOT_BOUND = 4;

    public static final int NO_QUEUE_BOUND_WITH_RK = 5;

    public static final int SPECIFIC_QUEUE_NOT_BOUND_WITH_RK = 6;

    public static ExchangeBoundHandler getInstance()
    {
        return _instance;
    }

    private ExchangeBoundHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, ExchangeBoundBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHostImpl virtualHost = session.getVirtualHost();
        MethodRegistry methodRegistry = session.getMethodRegistry();

        final AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        channel.sync();


        AMQShortString exchangeName = body.getExchange();
        AMQShortString queueName = body.getQueue();
        AMQShortString routingKey = body.getRoutingKey();
        ExchangeBoundOkBody response;

        if(isDefaultExchange(exchangeName))
        {
            if(routingKey == null)
            {
                if(queueName == null)
                {
                    response = methodRegistry.createExchangeBoundOkBody(virtualHost.getQueues().isEmpty() ? NO_BINDINGS : OK, null);
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                                                                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        response = methodRegistry.createExchangeBoundOkBody(OK, null);
                    }
                }
            }
            else
            {
                if(queueName == null)
                {
                    response = methodRegistry.createExchangeBoundOkBody(virtualHost.getQueue(routingKey.toString()) == null ? NO_QUEUE_BOUND_WITH_RK : OK, null);
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                                                                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        response = methodRegistry.createExchangeBoundOkBody(queueName.equals(routingKey) ? OK : SPECIFIC_QUEUE_NOT_BOUND_WITH_RK, null);
                    }
                }
            }
        }
        else
        {
            ExchangeImpl exchange = virtualHost.getExchange(exchangeName.toString());
            if (exchange == null)
            {


                response = methodRegistry.createExchangeBoundOkBody(EXCHANGE_NOT_FOUND,
                                                                    AMQShortString.validValueOf("Exchange '" + exchangeName + "' not found"));
            }
            else if (routingKey == null)
            {
                if (queueName == null)
                {
                    if (exchange.hasBindings())
                    {
                        response = methodRegistry.createExchangeBoundOkBody(OK, null);
                    }
                    else
                    {

                        response = methodRegistry.createExchangeBoundOkBody(NO_BINDINGS,	// replyCode
                            null);	// replyText
                    }
                }
                else
                {

                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        if (exchange.isBound(queue))
                        {

                            response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                                null);	// replyText
                        }
                        else
                        {

                            response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_BOUND,	// replyCode
                                AMQShortString.validValueOf("Queue '" + queueName + "' not bound to exchange '" + exchangeName + "'"));	// replyText
                        }
                    }
                }
            }
            else if (queueName != null)
            {
                AMQQueue queue = virtualHost.getQueue(queueName.toString());
                if (queue == null)
                {

                    response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                        AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                }
                else
                {
                    String bindingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().asString();
                    if (exchange.isBound(bindingKey, queue))
                    {

                        response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                            null);	// replyText
                    }
                    else
                    {

                        String message = "Queue '" + queueName + "' not bound with routing key '" +
                                            body.getRoutingKey() + "' to exchange '" + exchangeName + "'";

                        response = methodRegistry.createExchangeBoundOkBody(SPECIFIC_QUEUE_NOT_BOUND_WITH_RK,	// replyCode
                            AMQShortString.validValueOf(message));	// replyText
                    }
                }
            }
            else
            {
                if (exchange.isBound(body.getRoutingKey() == null ? "" : body.getRoutingKey().asString()))
                {

                    response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                        null);	// replyText
                }
                else
                {

                    response = methodRegistry.createExchangeBoundOkBody(NO_QUEUE_BOUND_WITH_RK,	// replyCode
                        AMQShortString.validValueOf("No queue bound with routing key '" + body.getRoutingKey() +
                        "' to exchange '" + exchangeName + "'"));	// replyText
                }
            }
        }
        session.writeFrame(response.generateFrame(channelId));
    }

    protected boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || exchangeName.equals(AMQShortString.EMPTY_STRING);
    }

}
