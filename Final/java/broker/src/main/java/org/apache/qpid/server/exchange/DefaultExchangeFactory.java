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
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnknownExchangeType;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DefaultExchangeFactory implements ExchangeFactory
{
    private static final Logger _logger = Logger.getLogger(DefaultExchangeFactory.class);

    private Map<AMQShortString, Class<? extends Exchange>> _exchangeClassMap = new HashMap<AMQShortString, Class<? extends Exchange>>();
    private final VirtualHost _host;

    public DefaultExchangeFactory(VirtualHost host)
    {
        _host = host;
        _exchangeClassMap.put(ExchangeDefaults.DIRECT_EXCHANGE_CLASS, org.apache.qpid.server.exchange.DestNameExchange.class);
        _exchangeClassMap.put(ExchangeDefaults.TOPIC_EXCHANGE_CLASS, org.apache.qpid.server.exchange.DestWildExchange.class);
        _exchangeClassMap.put(ExchangeDefaults.HEADERS_EXCHANGE_CLASS, org.apache.qpid.server.exchange.HeadersExchange.class);
        _exchangeClassMap.put(ExchangeDefaults.FANOUT_EXCHANGE_CLASS, org.apache.qpid.server.exchange.FanoutExchange.class);

    }

    public Exchange createExchange(AMQShortString exchange, AMQShortString type, boolean durable, boolean autoDelete,
                                   int ticket)
            throws AMQException
    {
        Class<? extends Exchange> exchClass = _exchangeClassMap.get(type);
        if (exchClass == null)
        {

            throw new AMQUnknownExchangeType("Unknown exchange type: " + type);
        }
        try
        {
            Exchange e = exchClass.newInstance();
            e.initialise(_host, exchange, durable, ticket, autoDelete);
            return e;
        }
        catch (InstantiationException e)
        {
            throw new AMQException("Unable to create exchange: " + e, e);
        }
        catch (IllegalAccessException e)
        {
            throw new AMQException("Unable to create exchange: " + e, e);
        }
    }
}
