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

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.AMQChannel;
import org.apache.commons.configuration.Configuration;

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


    protected QueueDeclareHandler()
    {
        Configurator.configure(this);
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<QueueDeclareBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore store = virtualHost.getMessageStore();

        QueueDeclareBody body = evt.getMethod();

        // if we aren't given a queue name, we create one which we return to the client
        if (body.queue == null)
        {
            body.queue = createName();
        }

        AMQQueue queue;
        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        synchronized (queueRegistry)
        {



            if (((queue = queueRegistry.getQueue(body.queue)) == null))
            {
                if(body.queue != null)
                {
                    body.queue = body.queue.intern();
                }

                if (body.passive)
                {
                    String msg = "Queue: " + body.queue + " not found on VirtualHost(" + virtualHost + ").";
                    throw body.getChannelException(AMQConstant.NOT_FOUND, msg);
                }
                else
                {
                    queue = createQueue(body, virtualHost, session);
                    if (queue.isDurable() && !queue.isAutoDelete())
                    {
                        store.createQueue(queue);
                    }
                    queueRegistry.registerQueue(queue);
                    if (autoRegister)
                    {
                        Exchange defaultExchange = exchangeRegistry.getDefaultExchange();

                        queue.bind(body.queue, null, defaultExchange);
                        _log.info("Queue " + body.queue + " bound to default exchange(" + defaultExchange.getName() + ")");
                    }
                }
            }
            else if (queue.getOwner() != null && !session.getContextKey().equals(queue.getOwner()))
            {
                throw body.getChannelException(AMQConstant.ALREADY_EXISTS, "Cannot declare queue('" + body.queue + "'),"
                                                                           + " as exclusive queue with same name "
                                                                           + "declared on another client ID('"
                                                                           + queue.getOwner() + "')");
            }

            AMQChannel channel = session.getChannel(evt.getChannelId());

            if (channel == null)
            {
                throw body.getChannelNotFoundException(evt.getChannelId());
            }

            //set this as the default queue on the channel:
            channel.setDefaultQueue(queue);
        }

        if (!body.nowait)
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQFrame response = QueueDeclareOkBody.createAMQFrame(evt.getChannelId(),
                                                                  (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                  queue.getConsumerCount(), // consumerCount
                                                                  queue.getMessageCount(), // messageCount
                                                                  body.queue); // queue
            _log.info("Queue " + body.queue + " declared successfully");
            session.writeFrame(response);
        }
    }

    protected AMQShortString createName()
    {
        return new AMQShortString("tmp_" + UUID.randomUUID());
    }

    protected AMQQueue createQueue(QueueDeclareBody body, VirtualHost virtualHost, final AMQProtocolSession session)
            throws AMQException
    {
        final QueueRegistry registry = virtualHost.getQueueRegistry();
        AMQShortString owner = body.exclusive ? session.getContextKey() : null;
        final AMQQueue queue = new AMQQueue(body.queue, body.durable, owner, body.autoDelete, virtualHost);
        final AMQShortString queueName = queue.getName();

        if (body.exclusive && !body.durable)
        {
            final AMQProtocolSession.Task deleteQueueTask =
                    new AMQProtocolSession.Task()
                    {
                        public void doTask(AMQProtocolSession session) throws AMQException
                        {
                            if (registry.getQueue(queueName) == queue)
                            {
                                queue.delete();
                            }
                        }
                    };

            session.addSessionCloseTask(deleteQueueTask);

            queue.addQueueDeleteTask(new AMQQueue.Task()
            {
                public void doTask(AMQQueue queue)
                {
                    session.removeSessionCloseTask(deleteQueueTask);
                }
            });
        }// if exclusive and not durable

        Configuration virtualHostDefaultQueueConfiguration = VirtualHostConfiguration.getDefaultQueueConfiguration(queue);
        if (virtualHostDefaultQueueConfiguration != null)
        {
            Configurator.configure(queue, virtualHostDefaultQueueConfiguration);
        }

        return queue;
    }
}
