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
package org.apache.qpid.server.virtualhost;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class ExchangeRecoverer extends AbstractDurableConfiguredObjectRecoverer<ExchangeImpl>
{
    private final ExchangeRegistry _exchangeRegistry;
    private final ExchangeFactory _exchangeFactory;

    public ExchangeRecoverer(final ExchangeRegistry exchangeRegistry, final ExchangeFactory exchangeFactory)
    {
        _exchangeRegistry = exchangeRegistry;
        _exchangeFactory = exchangeFactory;
    }

    @Override
    public String getType()
    {
        return org.apache.qpid.server.model.Exchange.class.getSimpleName();
    }

    @Override
    public UnresolvedObject<ExchangeImpl> createUnresolvedObject(final UUID id,
                                                             final String type,
                                                             final Map<String, Object> attributes)
    {
        return new UnresolvedExchange(id, attributes);
    }

    private class UnresolvedExchange implements UnresolvedObject<ExchangeImpl>
    {
        private ExchangeImpl _exchange;

        public UnresolvedExchange(final UUID id,
                                  final Map<String, Object> attributeMap)
        {
            String exchangeName = (String) attributeMap.get(org.apache.qpid.server.model.Exchange.NAME);
            try
            {
                _exchange = _exchangeRegistry.getExchange(id);
                if(_exchange == null)
                {
                    _exchange = _exchangeRegistry.getExchange(exchangeName);
                }
                if (_exchange == null)
                {
                    Map<String,Object> attributesWithId = new HashMap<String,Object>(attributeMap);
                    attributesWithId.put(org.apache.qpid.server.model.Exchange.ID,id);
                    attributesWithId.put(org.apache.qpid.server.model.Exchange.DURABLE,true);
                    _exchange = _exchangeFactory.restoreExchange(attributesWithId);
                    _exchangeRegistry.registerExchange(_exchange);
                }
            }
            catch (AMQUnknownExchangeType e)
            {
                throw new ServerScopedRuntimeException("Unknown exchange type found when attempting to restore " +
                                                       "exchanges, check classpath", e);
            }
            catch (UnknownExchangeException e)
            {
                throw new ServerScopedRuntimeException("Unknown alternate exchange type found when attempting to restore " +
                                                       "exchanges: ", e);
            }
        }

        @Override
        public UnresolvedDependency[] getUnresolvedDependencies()
        {
            return new UnresolvedDependency[0];
        }

        @Override
        public ExchangeImpl resolve()
        {
            return _exchange;
        }
    }
}
