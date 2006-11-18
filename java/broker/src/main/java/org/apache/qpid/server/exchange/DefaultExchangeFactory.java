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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;

import java.util.HashMap;
import java.util.Map;

public class DefaultExchangeFactory implements ExchangeFactory
{
    private static final Logger _logger = Logger.getLogger(DefaultExchangeFactory.class);

    private Map<String, Class<? extends Exchange>> _exchangeClassMap = new HashMap<String, Class<? extends Exchange>>();

    public DefaultExchangeFactory()
    {
        _exchangeClassMap.put("direct", org.apache.qpid.server.exchange.DestNameExchange.class);
        _exchangeClassMap.put("topic", org.apache.qpid.server.exchange.DestWildExchange.class);
        _exchangeClassMap.put("headers", org.apache.qpid.server.exchange.HeadersExchange.class);
    }

    public Exchange createExchange(String exchange, String type, boolean durable, boolean autoDelete,
                                   int ticket)
            throws AMQException
    {
        Class<? extends Exchange> exchClass = _exchangeClassMap.get(type);
        if (exchClass == null)
        {
            throw new AMQException(_logger, "Unknown exchange type: " + type);
        }
        try
        {
            Exchange e = exchClass.newInstance();
            e.initialise(exchange, durable, ticket, autoDelete);
            return e;
        }
        catch (InstantiationException e)
        {
            throw new AMQException(_logger, "Unable to create exchange: " + e, e);
        }
        catch (IllegalAccessException e)
        {
            throw new AMQException(_logger, "Unable to create exchange: " + e, e);
        }
    }
}
