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
package org.apache.qpid.server.exchange;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.commons.configuration.Configuration;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DefaultExchangeFactory implements ExchangeFactory
{
    private static final Logger _logger = Logger.getLogger(DefaultExchangeFactory.class);

    private Map<AMQShortString, ExchangeType<? extends Exchange>> _exchangeClassMap = new HashMap<AMQShortString, ExchangeType<? extends Exchange>>();
    private final VirtualHost _host;

    public DefaultExchangeFactory(VirtualHost host)
    {
        _host = host;
        registerExchangeType(DestNameExchange.TYPE);
        registerExchangeType(DestWildExchange.TYPE);
        registerExchangeType(HeadersExchange.TYPE);
        registerExchangeType(FanoutExchange.TYPE);

    }

    public void registerExchangeType(ExchangeType<? extends Exchange> type)
    {
        _exchangeClassMap.put(type.getName(), type);
    }

    public Exchange createExchange(AMQShortString exchange, AMQShortString type, boolean durable, boolean autoDelete,
                                   int ticket)
            throws AMQException
    {
        ExchangeType<? extends Exchange> exchType = _exchangeClassMap.get(type);
        if (exchType == null)
        {

            throw new AMQUnknownExchangeType("Unknown exchange type: " + type, null);
        }
        Exchange e = exchType.newInstance(_host, exchange, durable, autoDelete);
        return e;
    }

    public void initialise(Configuration hostConfig)
    {
        for(Object className : hostConfig.getList("custom-exchanges.class-name"))
        {
            try
            {
                Class<? extends ExchangeType> exchangeTypeClass = (Class<? extends ExchangeType>) Class.forName(String.valueOf(className));
                ExchangeType type = exchangeTypeClass.newInstance();
                registerExchangeType(type);

            }
            catch (ClassNotFoundException e)
            {
                _logger.error("No such custom exchange class found: \""+String.valueOf(className)+"\"");
            }
            catch (ClassCastException classCastEx)
            {
                _logger.error("No custom exchange class: \""+String.valueOf(className)+"\" cannot be registered as it does not extend class \""+ExchangeType.class+"\"");
            }
            catch (IllegalAccessException e)
            {
                _logger.error("Cannot create custom exchange class: \""+String.valueOf(className)+"\"",e);
            }
            catch (InstantiationException e)
            {
                _logger.error("Cannot create custom exchange class: \""+String.valueOf(className)+"\"",e);
            }
        }

    }
}
