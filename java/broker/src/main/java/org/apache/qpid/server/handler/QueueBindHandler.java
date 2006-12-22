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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueBindOkBody;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

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

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<QueueBindBody> evt) throws AMQException
    {
        final QueueBindBody body = evt.getMethod();
        final AMQQueue queue;
        if (body.queue == null)
        {
            queue = protocolSession.getChannel(evt.getChannelId()).getDefaultQueue();
            if (queue == null)
            {
                throw new AMQException("No default queue defined on channel and queue was null");
            }
            if (body.routingKey == null)
            {
                body.routingKey = queue.getName();
            }
        }
        else
        {
            queue = queueRegistry.getQueue(body.queue);
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(), "Queue " + body.queue + " does not exist.");
        }
        final Exchange exch = exchangeRegistry.getExchange(body.exchange);
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(), "Exchange " + body.exchange + " does not exist.");
        }
        exch.registerQueue(body.routingKey, queue, body.arguments);
        queue.bind(body.routingKey, exch);
        if (_log.isInfoEnabled())
        {
            _log.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + body.routingKey);
        }
        if (!body.nowait)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            final AMQFrame response = QueueBindOkBody.createAMQFrame(evt.getChannelId(), (byte)8, (byte)0);
            protocolSession.writeFrame(response);
        }
    }
}
