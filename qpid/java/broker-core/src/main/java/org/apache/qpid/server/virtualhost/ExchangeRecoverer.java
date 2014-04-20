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

import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class ExchangeRecoverer extends AbstractDurableConfiguredObjectRecoverer<ExchangeImpl>
{
    private final VirtualHostImpl<?,?,?> _vhost;
    private final ConfiguredObjectFactory _objectFactory;

    public ExchangeRecoverer(final VirtualHostImpl vhost)
    {
        _vhost = vhost;
        Broker<?> broker = _vhost.getParent(Broker.class);
        _objectFactory = broker.getObjectFactory();
    }

    @Override
    public String getType()
    {
        return org.apache.qpid.server.model.Exchange.class.getSimpleName();
    }

    @Override
    public UnresolvedObject<ExchangeImpl> createUnresolvedObject(final ConfiguredObjectRecord record)
    {
        return new UnresolvedExchange(record);
    }

    private class UnresolvedExchange implements UnresolvedObject<ExchangeImpl>
    {
        private ExchangeImpl<?> _exchange;

        public UnresolvedExchange(ConfiguredObjectRecord record)
        {
            Map<String,Object> attributeMap = record.getAttributes();
            String exchangeName = (String) attributeMap.get(org.apache.qpid.server.model.Exchange.NAME);
            try
            {
                _exchange = _vhost.getExchange(record.getId());
                if(_exchange == null)
                {
                    _exchange = _vhost.getExchange(exchangeName);
                }
                if (_exchange == null)
                {
                    Map<String,Object> attributesWithId = new HashMap<String,Object>(attributeMap);
                    attributesWithId.put(org.apache.qpid.server.model.Exchange.ID,record.getId());
                    attributesWithId.put(org.apache.qpid.server.model.Exchange.DURABLE,true);

                    UnresolvedConfiguredObject<? extends Exchange> unresolvedConfiguredObject =
                            _objectFactory.recover(record, _vhost);
                    _exchange = (ExchangeImpl<?>) unresolvedConfiguredObject.resolve();

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
            _exchange.open();
            return _exchange;
        }
    }
}
