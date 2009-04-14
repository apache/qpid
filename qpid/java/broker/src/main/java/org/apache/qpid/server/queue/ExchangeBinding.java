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
package org.apache.qpid.server.queue;

import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;

public class ExchangeBinding
{
    private final Exchange _exchange;
    private final AMQShortString _routingKey;
    private final FieldTable _arguments;

    private static final FieldTable EMPTY_ARGUMENTS = new FieldTable();    

    ExchangeBinding(AMQShortString routingKey, Exchange exchange)
    {
        this(routingKey, exchange, EMPTY_ARGUMENTS);
    }

    ExchangeBinding(AMQShortString routingKey, Exchange exchange, FieldTable arguments)
    {
        _routingKey = routingKey == null ? AMQShortString.EMPTY_STRING : routingKey;
        _exchange = exchange;
        _arguments = arguments == null ? EMPTY_ARGUMENTS : arguments;
    }

    void unbind(AMQQueue queue) throws AMQException
    {
        _exchange.deregisterQueue(_routingKey, queue, _arguments);
    }

    public Exchange getExchange()
    {
        return _exchange;
    }

    public AMQShortString getRoutingKey()
    {
        return _routingKey;
    }

    public FieldTable getArguments()
    {
        return _arguments;
    }

    public int hashCode()
    {
        return (_exchange == null ? 0 : _exchange.hashCode())
               + (_routingKey == null ? 0 : _routingKey.hashCode());
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof ExchangeBinding))
        {
            return false;
        }
        ExchangeBinding eb = (ExchangeBinding) o;
        return _exchange.equals(eb._exchange)
               && _routingKey.equals(eb._routingKey);
    }
}