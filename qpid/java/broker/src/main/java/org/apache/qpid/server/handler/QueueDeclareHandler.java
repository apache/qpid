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
import org.apache.qpid.AMQChannelException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.framing.*;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

public class QueueDeclareHandler implements StateAwareMethodListener<QueueDeclareBody>
{
    private static final Logger _log = Logger.getLogger(QueueDeclareHandler.class);

    private static final QueueDeclareHandler _instance = new QueueDeclareHandler();

    public static QueueDeclareHandler getInstance()
    {
        return _instance;
    }

    @Configured(path = "queue.auto_register", defaultValue = "false")
    public boolean autoRegister;

    private final AtomicInteger _counter = new AtomicInteger();

    private final MessageStore _store;

    protected QueueDeclareHandler()
    {
        Configurator.configure(this);
        _store = ApplicationRegistry.getInstance().getMessageStore();
    }

    public void methodReceived(AMQStateManager stateManager, QueueRegistry queueRegistry,
                               ExchangeRegistry exchangeRegistry, AMQProtocolSession protocolSession,
                               AMQMethodEvent<QueueDeclareBody> evt) throws AMQException
    {
        QueueDeclareBody body = evt.getMethod();

        // if we aren't given a queue name, we create one which we return to the client
        if (body.queue == null)
        {
            body.queue = createName();
        }

        AMQQueue queue = null;
        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        synchronized (queueRegistry)
        {

            if (((queue = queueRegistry.getQueue(body.queue)) == null) )
            {
                if(body.passive)
                {
                    String msg = "Queue: " + body.queue + " not found.";
                    throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(),msg );

                }
                else
                {
                    queue = createQueue(body, queueRegistry, protocolSession);
                    if (queue.isDurable() && !queue.isAutoDelete())
                    {
                        _store.createQueue(queue);
                    }
                    queueRegistry.registerQueue(queue);
                    if (autoRegister)
                    {
                        Exchange defaultExchange = exchangeRegistry.getExchange(ExchangeDefaults.DIRECT_EXCHANGE_NAME);
                        defaultExchange.registerQueue(body.queue, queue, null);
                        queue.bind(body.queue, defaultExchange);
                        _log.info("Queue " + body.queue + " bound to default exchange");
                    }
                }
            }
            else if(queue.getOwner() != null && !protocolSession.getContextKey().equals(queue.getOwner()))
            {
                // todo - constant
                throw body.getChannelException(405, "Cannot declare queue, as exclusive queue with same name declared on another connection");        

            }
            //set this as the default queue on the channel:
            protocolSession.getChannel(evt.getChannelId()).setDefaultQueue(queue);
        }

        if (!body.nowait)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQFrame response = QueueDeclareOkBody.createAMQFrame(evt.getChannelId(),
                (byte)8, (byte)0,	// AMQP version (major, minor)
                queue.getConsumerCount(), // consumerCount
                queue.getMessageCount(), // messageCount
                body.queue); // queue
            _log.info("Queue " + body.queue + " declared successfully");
            protocolSession.writeFrame(response);
        }
    }

    protected AMQShortString createName()
    {
        return new AMQShortString("tmp_" + pad(_counter.incrementAndGet()));
    }

    protected static String pad(int value)
    {
        return MessageFormat.format("{0,number,0000000000000}", value);
    }

    protected AMQQueue createQueue(QueueDeclareBody body, QueueRegistry registry, AMQProtocolSession session)
            throws AMQException
    {
        AMQShortString owner = body.exclusive ? session.getContextKey() : null;
        return new AMQQueue(body.queue, body.durable, owner, body.autoDelete, registry);
    }
}
