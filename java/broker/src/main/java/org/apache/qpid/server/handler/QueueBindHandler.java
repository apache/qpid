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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;

public class QueueBindHandler implements StateAwareMethodListener<QueueBindBody>
{
    private static final Logger _log = Logger.getLogger(QueueBindHandler.class);

    private static final QueueBindHandler _instance = new QueueBindHandler();

    public static QueueBindHandler getInstance()
    {
        return _instance;
    }

    private QueueBindHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueBindBody body, int channelId) throws AMQException
    {
        AMQProtocolSession protocolConnection = stateManager.getProtocolSession();
        VirtualHost virtualHost = protocolConnection.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        final AMQQueue queue;
        final AMQShortString routingKey;

        if (body.getQueue() == null)
        {
            AMQChannel channel = protocolConnection.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId);
            }

            queue = channel.getDefaultQueue();

            if (queue == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "No default queue defined on channel and queue was null");
            }

            if (body.getRoutingKey() == null)
            {
                routingKey = queue.getNameShortString();
            }
            else
            {
                routingKey = body.getRoutingKey().intern();
            }
        }
        else
        {
            queue = queueRegistry.getQueue(body.getQueue());
            routingKey = body.getRoutingKey() == null ? AMQShortString.EMPTY_STRING : body.getRoutingKey().intern();
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.");
        }
        final Exchange exch = exchangeRegistry.getExchange(body.getExchange());
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Exchange " + body.getExchange() + " does not exist.");
        }


        try
        {
            if (queue.isExclusive() && !queue.isDurable())
            {
                AMQSessionModel session = queue.getExclusiveOwningSession();
                if (session == null || session.getConnectionModel() != protocolConnection)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue " + queue.getNameShortString() + " is exclusive, but not created on this Connection.");
                }
            }

            if (!exch.isBound(routingKey, body.getArguments(), queue))
            {
                String bindingKey = String.valueOf(routingKey);
                Map<String,Object> arguments = FieldTable.convertToMap(body.getArguments());

                if(!virtualHost.getBindingFactory().addBinding(bindingKey, queue, exch, arguments))
                {
                    Binding oldBinding = virtualHost.getBindingFactory().getBinding(bindingKey, queue, exch, arguments);

                    Map<String, Object> oldArgs = oldBinding.getArguments();
                    if((oldArgs == null && !arguments.isEmpty()) || (oldArgs != null && !oldArgs.equals(arguments)))
                    {
                        virtualHost.getBindingFactory().replaceBinding(bindingKey, queue, exch, arguments);    
                    }
                }
            }
        }
        catch (AMQException e)
        {
            throw body.getChannelException(AMQConstant.CHANNEL_ERROR, e.toString());
        }

        if (_log.isInfoEnabled())
        {
            _log.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + routingKey);
        }
        if (!body.getNowait())
        {
            MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createQueueBindOkBody();
            protocolConnection.writeFrame(responseBody.generateFrame(channelId));

        }
    }
}
