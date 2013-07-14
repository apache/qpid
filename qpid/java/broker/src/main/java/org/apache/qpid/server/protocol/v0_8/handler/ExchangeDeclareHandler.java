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

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
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

    public void methodReceived(AMQStateManager stateManager, ExchangeDeclareBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        final AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        final AMQShortString exchangeName = body.getExchange();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request to declare exchange of type " + body.getType() + " with name " + exchangeName);
        }

        Exchange exchange;

        if (body.getPassive())
        {
            exchange = virtualHost.getExchange(exchangeName == null ? null : exchangeName.toString());
            if(exchange == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "Unknown exchange: " + exchangeName);
            }
            else if (!exchange.getTypeShortString().equals(body.getType()) && !(body.getType() == null || body.getType().length() ==0))
            {

                throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: " +
                                  exchangeName + " of type " + exchange.getTypeShortString()
                                  + " to " + body.getType() +".",body.getClazz(), body.getMethod(),body.getMajor(),body.getMinor(),null);
            }

        }
        else
        {
            try
            {
                exchange = virtualHost.createExchange(null,
                                                      exchangeName == null ? null : exchangeName.intern().toString(),
                                                      body.getType() == null ? null : body.getType().intern().toString(),
                                                      body.getDurable(),
                                                      body.getAutoDelete(),
                        null);

            }
            catch(ReservedExchangeNameException e)
            {
                throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                          "Attempt to declare exchange: " + exchangeName +
                                          " which begins with reserved prefix.");

            }
            catch(ExchangeExistsException e)
            {
                exchange = e.getExistingExchange();
                if(!exchange.getTypeShortString().equals(body.getType()))
                {
                    throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                                                              + exchangeName + " of type "
                                                                              + exchange.getTypeShortString()
                                                                              + " to " + body.getType() +".",
                                                     body.getClazz(), body.getMethod(),
                                                     body.getMajor(), body.getMinor(),null);
                }
            }
            catch(AMQUnknownExchangeType e)
            {
                throw body.getConnectionException(AMQConstant.COMMAND_INVALID, "Unknown exchange: " + exchangeName,e);
            }
        }


        if(!body.getNowait())
        {
            MethodRegistry methodRegistry = session.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
            channel.sync();
            session.writeFrame(responseBody.generateFrame(channelId));
        }
    }
}
