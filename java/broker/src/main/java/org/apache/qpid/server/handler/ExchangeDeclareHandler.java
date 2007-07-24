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

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import org.apache.log4j.Logger;

public class ExchangeDeclareHandler implements StateAwareMethodListener<ExchangeDeclareBody>
{
    private static final Logger _logger = Logger.getLogger(ExchangeDeclareHandler.class);

    private static final ExchangeDeclareHandler _instance = new ExchangeDeclareHandler();

    public static ExchangeDeclareHandler getInstance()
    {
        return _instance;
    }

    private ExchangeDeclareHandler() {}

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ExchangeDeclareBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        final ExchangeDeclareBody body = evt.getMethod();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();
        
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request to declare exchange of type " + body.type + " with name " + body.exchange);
        }
        synchronized(exchangeRegistry)
        {
            Exchange exchange = exchangeRegistry.getExchange(body.exchange);



            if (exchange == null)
            {
                if(body.passive && ((body.type == null) || body.type.length() ==0))
                {
                    throw body.getChannelException(AMQConstant.NOT_FOUND.getCode(), "Unknown exchange: " + body.exchange);                    
                }
                else
                {
                    try
                    {
                        exchange = exchangeFactory.createExchange(exchangeRegistry,body.exchange, body.type, body.durable,
                                                                  body.passive, body.ticket);
                        exchangeRegistry.registerExchange(exchange);
                    }
                    catch(AMQUnknownExchangeType e)
                    {
                        throw body.getConnectionException(AMQConstant.COMMAND_INVALID.getCode(), "Unknown exchange type: " + body.type, e);
                    }
                }
            }
            else if (!exchange.getType().equals(body.type))
            {

                throw new AMQConnectionException(AMQConstant.NOT_ALLOWED.getCode(), "Attempt to redeclare exchange: " + body.exchange +
                    " of type " + exchange.getType() + " to " + body.type +".",body.getClazz(), body.getMethod(),body.getMajor(),body.getMinor());        
            }
        }
        if(!body.nowait)
        {
            // Be aware of possible changes to parameter order as versions change.
            AMQMethodBody response = ExchangeDeclareOkBody.createMethodBody(
                session.getProtocolMajorVersion(), // AMQP major version
                session.getProtocolMinorVersion()); // AMQP minor version
            session.writeResponse(evt, response);
        }
    }
}
