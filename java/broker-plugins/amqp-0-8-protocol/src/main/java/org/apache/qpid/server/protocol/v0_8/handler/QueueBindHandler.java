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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.exchange.TopicExchange;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.security.QpidSecurityException;
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
        AMQChannel channel = protocolConnection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        final AMQQueue queue;
        final AMQShortString routingKey;

        final AMQShortString queueName = body.getQueue();

        if (queueName == null)
        {

            queue = channel.getDefaultQueue();

            if (queue == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "No default queue defined on channel and queue was null");
            }

            if (body.getRoutingKey() == null)
            {
                routingKey = AMQShortString.valueOf(queue.getName());
            }
            else
            {
                routingKey = body.getRoutingKey().intern();
            }
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
            routingKey = body.getRoutingKey() == null ? AMQShortString.EMPTY_STRING : body.getRoutingKey().intern();
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + queueName + " does not exist.");
        }
        final String exchangeName = body.getExchange() == null ? null : body.getExchange().toString();
        final Exchange exch = virtualHost.getExchange(exchangeName);
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Exchange " + exchangeName + " does not exist.");
        }


        try
        {

            Map<String,Object> arguments = FieldTable.convertToMap(body.getArguments());
            String bindingKey = String.valueOf(routingKey);

            if (!exch.isBound(bindingKey, arguments, queue))
            {

                if(!exch.addBinding(bindingKey, queue, arguments) && TopicExchange.TYPE.equals(exch.getType()))
                {
                    Binding oldBinding = exch.getBinding(bindingKey, queue, arguments);

                    Map<String, Object> oldArgs = oldBinding.getArguments();
                    if((oldArgs == null && !arguments.isEmpty()) || (oldArgs != null && !oldArgs.equals(arguments)))
                    {
                        exch.replaceBinding(oldBinding.getId(), bindingKey, queue, arguments);
                    }
                }
            }
        }
        catch (QpidSecurityException e)
        {
            throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
        }

        if (_log.isInfoEnabled())
        {
            _log.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + routingKey);
        }
        if (!body.getNowait())
        {
            channel.sync();
            MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createQueueBindOkBody();
            protocolConnection.writeFrame(responseBody.generateFrame(channelId));

        }
    }
}
