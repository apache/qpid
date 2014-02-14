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

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueFactory;
import org.apache.qpid.server.security.QpidSecurityException;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class QueueRecoverer extends AbstractDurableConfiguredObjectRecoverer<AMQQueue>
{
    private static final Logger _logger = Logger.getLogger(QueueRecoverer.class);
    private final VirtualHost _virtualHost;
    private final ExchangeRegistry _exchangeRegistry;
    private final QueueFactory _queueFactory;

    public QueueRecoverer(final VirtualHost virtualHost,
                          final ExchangeRegistry exchangeRegistry,
                          final QueueFactory queueFactory)
    {
        _virtualHost = virtualHost;
        _exchangeRegistry = exchangeRegistry;
        _queueFactory = queueFactory;
    }

    @Override
    public String getType()
    {
        return Queue.class.getSimpleName();
    }

    @Override
    public UnresolvedObject<AMQQueue> createUnresolvedObject(final UUID id,
                                                             final String type,
                                                             final Map<String, Object> attributes)
    {
        return new UnresolvedQueue(id, type, attributes);
    }

    private class UnresolvedQueue implements UnresolvedObject<AMQQueue>
    {
        private final Map<String, Object> _attributes;
        private final UUID _alternateExchangeId;
        private final UUID _id;
        private AMQQueue _queue;
        private List<UnresolvedDependency> _dependencies = new ArrayList<UnresolvedDependency>();
        private Exchange _alternateExchange;

        public UnresolvedQueue(final UUID id,
                               final String type,
                               final Map<String, Object> attributes)
        {
            _attributes = attributes;
            _alternateExchangeId = _attributes.get(Queue.ALTERNATE_EXCHANGE) == null ? null : UUID.fromString((String) _attributes
                    .get(Queue.ALTERNATE_EXCHANGE));
            _id = id;
            if (_alternateExchangeId != null)
            {
                _alternateExchange = _exchangeRegistry.getExchange(_alternateExchangeId);
                if(_alternateExchange == null)
                {
                    _dependencies.add(new AlternateExchangeDependency());
                }
            }
        }

        @Override
        public UnresolvedDependency[] getUnresolvedDependencies()
        {
            return _dependencies.toArray(new UnresolvedDependency[_dependencies.size()]);
        }

        @Override
        public AMQQueue resolve()
        {
            String queueName = (String) _attributes.get(Queue.NAME);
            String owner = (String) _attributes.get(Queue.OWNER);
            boolean exclusive = (Boolean) _attributes.get(Queue.EXCLUSIVE);

            Map<String, Object> queueArgumentsMap = new LinkedHashMap<String, Object>(_attributes);
            queueArgumentsMap.remove(Queue.NAME);
            queueArgumentsMap.remove(Queue.OWNER);
            queueArgumentsMap.remove(Queue.EXCLUSIVE);

            try
            {
                _queue = _virtualHost.getQueue(_id);
                if(_queue == null)
                {
                    _queue = _virtualHost.getQueue(queueName);
                }

                if (_queue == null)
                {
                    _queue = _queueFactory.restoreQueue(_id, queueName, owner, false, exclusive,
                            false, queueArgumentsMap);
                }
            }
            catch (QpidSecurityException e)
            {
                throw new ServerScopedRuntimeException("Security Exception thrown when recovering. The recovery " +
                                                       "thread should not be bound by permissions, this is likely " +
                                                       "a programming error.",e);
            }
            return _queue;
        }

        private class AlternateExchangeDependency implements UnresolvedDependency
        {
            @Override
            public UUID getId()
            {
                return _alternateExchangeId;
            }

            @Override
            public String getType()
            {
                return "Exchange";
            }

            @Override
            public void resolve(final Object dependency)
            {
                _alternateExchange = (Exchange) dependency;
                _dependencies.remove(this);
            }
        }
    }
}
