/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ExchangeBoundBody;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;

/**
 * @author Apache Software Foundation
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

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<ExchangeBoundBody> evt) throws AMQException
    {
        ExchangeBoundBody body = evt.getMethod();

        String exchangeName = body.exchange;
        String queueName = body.queue;
        String routingKey = body.routingKey;
        if (exchangeName == null)
        {
            throw new AMQException("Exchange exchange must not be null");
        }
        Exchange exchange = exchangeRegistry.getExchange(exchangeName);
        AMQFrame response;
        if (exchange == null)
        {
            response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), EXCHANGE_NOT_FOUND,
                                                             "Exchange " + exchangeName + " not found");
        }
        else if (routingKey == null)
        {
            if (queueName == null)
            {
                if (exchange.hasBindings())
                {
                    response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), OK, null);
                }
                else
                {
                    response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), NO_BINDINGS, null);
                }
            }
            else
            {
                AMQQueue queue = queueRegistry.getQueue(queueName);
                if (queue == null)
                {
                    response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), QUEUE_NOT_FOUND,
                                                                      "Queue " + queueName + " not found");
                }
                else
                {
                    if (exchange.isBound(queue))
                    {
                        response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), OK, null);
                    }
                    else
                    {
                        response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), QUEUE_NOT_BOUND,
                                                                      "Queue " + queueName + " not bound to exchange " +
                                                                      exchangeName);
                    }
                }
            }
        }
        else if (queueName != null)
        {
            AMQQueue queue = queueRegistry.getQueue(queueName);
            if (queue == null)
            {
                response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), QUEUE_NOT_FOUND,
                                                                  "Queue " + queueName + " not found");
            }
            else
            {
                if (exchange.isBound(body.routingKey, queue))
                {
                    response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), OK,
                                                                     null);
                }
                else
                {
                    response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(),
                                                                     SPECIFIC_QUEUE_NOT_BOUND_WITH_RK,
                                                                     "Queue " + queueName +
                                                                     " not bound with routing key " +
                                                                     body.routingKey + " to exchange " +
                                                                     exchangeName);
                }
            }
        }
        else
        {
            if (exchange.isBound(body.routingKey))
            {
                response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(), OK,
                                                                 null);
            }
            else
            {
                response = ExchangeBoundOkBody.createAMQFrame(evt.getChannelId(),
                                                                 NO_QUEUE_BOUND_WITH_RK,
                                                                 "No queue bound with routing key " +
                                                                 body.routingKey + " to exchange " +
                                                                 exchangeName);
            }
        }
        protocolSession.writeFrame(response);
    }
}
