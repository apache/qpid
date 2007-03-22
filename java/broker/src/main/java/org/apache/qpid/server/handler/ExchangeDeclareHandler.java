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
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.framing.AMQFrame;
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

public class ExchangeDeclareHandler implements StateAwareMethodListener<ExchangeDeclareBody>
{
    private static final Logger _logger = Logger.getLogger(ExchangeDeclareHandler.class);

    private static final ExchangeDeclareHandler _instance = new ExchangeDeclareHandler();

    public static ExchangeDeclareHandler getInstance()
    {
        return _instance;
    }



    private ExchangeDeclareHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<ExchangeDeclareBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
        ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();
        
        final ExchangeDeclareBody body = evt.getMethod();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request to declare exchange of type " + body.getType() + " with name " + body.getExchange());
        }
        synchronized(exchangeRegistry)
        {
            Exchange exchange = exchangeRegistry.getExchange(body.getExchange());



            if (exchange == null)
            {
                if(body.getPassive() && ((body.getType() == null) || body.getType().length() ==0))
                {
                    throw body.getChannelException(AMQConstant.NOT_FOUND, "Unknown exchange: " + body.getExchange());
                }
                else
                {
                    try
                    {

                    exchange = exchangeFactory.createExchange(body.getExchange(), body.getType(), body.getDurable(),
                                                              body.getPassive(), body.getTicket());
                    exchangeRegistry.registerExchange(exchange);
                    }
                    catch(AMQUnknownExchangeType e)
                    {
                        throw body.getConnectionException(AMQConstant.COMMAND_INVALID, "Unknown exchange: " + body.getExchange(),e);
                    }
                }
            }
            else if (!exchange.getType().equals(body.getType()))
            {

                throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: " + body.getExchange() + " of type " + exchange.getType() + " to " + body.getType() +".",body.getClazz(), body.getMethod(),body.getMajor(),body.getMinor());
            }

        }
        if(!body.getNowait())
        {
            // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQFrame response = ExchangeDeclareOkBody.createAMQFrame(evt.getChannelId(), (byte)8, (byte)0);
            session.writeFrame(response);
        }
    }
}
