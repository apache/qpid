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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.framing.amqp_0_9.MethodRegistry_0_9;
import org.apache.qpid.framing.amqp_0_91.MethodRegistry_0_91;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

import java.security.AccessControlException;

public class QueueUnbindHandler implements StateAwareMethodListener<QueueUnbindBody>
{
    private static final Logger _log = Logger.getLogger(QueueUnbindHandler.class);

    private static final QueueUnbindHandler _instance = new QueueUnbindHandler();

    public static QueueUnbindHandler getInstance()
    {
        return _instance;
    }

    private QueueUnbindHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, QueueUnbindBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHostImpl virtualHost = session.getVirtualHost();

        final AMQQueue queue;
        final AMQShortString routingKey;


        AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        if (body.getQueue() == null)
        {

            queue = channel.getDefaultQueue();

            if (queue == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "No default queue defined on channel and queue was null");
            }

            routingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().intern(false);

        }
        else
        {
            queue = virtualHost.getQueue(body.getQueue().toString());
            routingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().intern(false);
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.");
        }

        if(isDefaultExchange(body.getExchange()))
        {
            throw body.getConnectionException(AMQConstant.NOT_ALLOWED, "Cannot unbind the queue " + queue.getName() + " from the default exchange");
        }

        final ExchangeImpl exch = virtualHost.getExchange(body.getExchange() == null ? null : body.getExchange().toString());
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Exchange " + body.getExchange() + " does not exist.");
        }

        if(!exch.hasBinding(String.valueOf(routingKey), queue))
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND,"No such binding");
        }
        else
        {
            try
            {
                exch.deleteBinding(String.valueOf(routingKey), queue);
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
            }
        }


        if (_log.isInfoEnabled())
        {
            _log.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + routingKey);
        }

        final MethodRegistry registry = session.getMethodRegistry();
        final AMQMethodBody responseBody;
        if (registry instanceof MethodRegistry_0_9)
        {
            responseBody = ((MethodRegistry_0_9)registry).createQueueUnbindOkBody();
        }
        else if (registry instanceof MethodRegistry_0_91)
        {
            responseBody = ((MethodRegistry_0_91)registry).createQueueUnbindOkBody();
        }
        else
        {
            // 0-8 does not support QueueUnbind
            throw new AMQException(AMQConstant.COMMAND_INVALID, "QueueUnbind not present in AMQP version: " + session.getProtocolVersion(), null);
        }
        channel.sync();
        session.writeFrame(responseBody.generateFrame(channelId));
    }

    protected boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || exchangeName.equals(AMQShortString.EMPTY_STRING);
    }

}
