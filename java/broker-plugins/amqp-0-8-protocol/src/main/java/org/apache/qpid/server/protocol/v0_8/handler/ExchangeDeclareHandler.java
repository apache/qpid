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

import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.NoFactoryForTypeException;
import org.apache.qpid.server.model.UnknownConfiguredObjectException;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

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
        VirtualHostImpl virtualHost = session.getVirtualHost();
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

        ExchangeImpl exchange;

        if(isDefaultExchange(exchangeName))
        {
            if(!new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_CLASS).equals(body.getType()))
            {
                throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare default exchange: "
                                                                          + " of type "
                                                                          + ExchangeDefaults.DIRECT_EXCHANGE_CLASS
                                                                          + " to " + body.getType() +".",
                                                 body.getClazz(), body.getMethod(),
                                                 body.getMajor(), body.getMinor(),null);
            }
        }
        else
        {
            if (body.getPassive())
            {
                exchange = virtualHost.getExchange(exchangeName.toString());
                if(exchange == null)
                {
                    throw body.getChannelException(AMQConstant.NOT_FOUND, "Unknown exchange: " + exchangeName);
                }
                else if (!(body.getType() == null || body.getType().length() ==0) && !exchange.getType().equals(body.getType().asString()))
                {

                    throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: " +
                                      exchangeName + " of type " + exchange.getType()
                                      + " to " + body.getType() +".",body.getClazz(), body.getMethod(),body.getMajor(),body.getMinor(),null);
                }

            }
            else
            {
                try
                {
                    String name = exchangeName == null ? null : exchangeName.intern().toString();
                    String type = body.getType() == null ? null : body.getType().intern().toString();

                    Map<String,Object> attributes = new HashMap<String, Object>();
                    if(body.getArguments() != null)
                    {
                        attributes.putAll(FieldTable.convertToMap(body.getArguments()));
                    }
                    attributes.put(org.apache.qpid.server.model.Exchange.ID, null);
                    attributes.put(org.apache.qpid.server.model.Exchange.NAME,name);
                    attributes.put(org.apache.qpid.server.model.Exchange.TYPE,type);
                    attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, body.getDurable());
                    attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                                   body.getAutoDelete() ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
                    if(!attributes.containsKey(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE))
                    {
                        attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
                    }
                    exchange = virtualHost.createExchange(attributes);

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
                    if(!new AMQShortString(exchange.getType()).equals(body.getType()))
                    {
                        throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                                                                  + exchangeName + " of type "
                                                                                  + exchange.getType()
                                                                                  + " to " + body.getType() +".",
                                                         body.getClazz(), body.getMethod(),
                                                         body.getMajor(), body.getMinor(),null);
                    }
                }
                catch(NoFactoryForTypeException e)
                {
                    throw body.getConnectionException(AMQConstant.COMMAND_INVALID, "Unknown exchange: " + exchangeName,e);
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
                }
                catch (UnknownConfiguredObjectException e)
                {
                    // note - since 0-8/9/9-1 can't set the alt. exchange this exception should never occur
                    throw body.getConnectionException(AMQConstant.NOT_FOUND, "Unknown alternate exchange",e);
                }
                catch (IllegalArgumentException e)
                {
                    throw body.getConnectionException(AMQConstant.COMMAND_INVALID, "Error creating exchange",e);
                }
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

    protected boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || exchangeName.equals(AMQShortString.EMPTY_STRING);
    }
}
