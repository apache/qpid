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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.framing.QueueUnbindOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import org.apache.log4j.Logger;

public class QueueUnbindHandler implements StateAwareMethodListener<QueueUnbindBody>
{
    private static final Logger _log = Logger.getLogger(QueueUnbindHandler.class);

    private static final QueueUnbindHandler _instance = new QueueUnbindHandler();

    public static QueueUnbindHandler getInstance()
    {
        return _instance;
    }

    private QueueUnbindHandler() {}

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<QueueUnbindBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        final QueueUnbindBody body = evt.getMethod();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        

        final AMQQueue queue = queueRegistry.getQueue(body.queue);
        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(), "Queue " + body.queue + " does not exist.");
        }
        final Exchange exch = exchangeRegistry.getExchange(body.exchange);
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(), "Exchange " + body.exchange + " does not exist.");
        }

        queue.unbind(body.routingKey, exch);//TODO: this should take the args as well 
        if (_log.isInfoEnabled())
        {
            _log.info("Unbinding queue " + queue + " from exchange " + exch + " with routing key " + body.routingKey);
        }
        // Be aware of possible changes to parameter order as versions change.
        final AMQMethodBody response = QueueUnbindOkBody.createMethodBody(
            session.getProtocolMajorVersion(), // AMQP major version
            session.getProtocolMinorVersion()); // AMQP minor version
        session.writeResponse(evt, response);
    }
}
