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
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueFactory;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;

public class QueueRecoverer extends AbstractDurableConfiguredObjectRecoverer<AMQQueue>
{
    private static final Logger _logger = Logger.getLogger(QueueRecoverer.class);
    private final VirtualHostImpl _virtualHost;
    private final ExchangeRegistry _exchangeRegistry;
    private final QueueFactory _queueFactory;

    public QueueRecoverer(final VirtualHostImpl virtualHost,
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
    public UnresolvedObject<AMQQueue> createUnresolvedObject(final ConfiguredObjectRecord record)
    {
        return new UnresolvedQueue(record.getId(), record.getAttributes());
    }

    private class UnresolvedQueue implements UnresolvedObject<AMQQueue>
    {
        private final Map<String, Object> _attributes;
        private final UUID _alternateExchangeId;
        private final UUID _id;
        private AMQQueue _queue;
        private List<UnresolvedDependency> _dependencies = new ArrayList<UnresolvedDependency>();
        private ExchangeImpl _alternateExchange;

        public UnresolvedQueue(final UUID id,
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

            _queue = _virtualHost.getQueue(_id);
            if(_queue == null)
            {
                _queue = _virtualHost.getQueue(queueName);
            }

            if (_queue == null)
            {
                Map<String, Object> attributes = new LinkedHashMap<String, Object>(_attributes);
                attributes.put(Queue.ID, _id);
                attributes.put(Queue.DURABLE, true);
                _queue = _queueFactory.restoreQueue(attributes);
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
                _alternateExchange = (ExchangeImpl) dependency;
                _dependencies.remove(this);
            }
        }
    }
}
