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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedConfiguredObject;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;

public class QueueRecoverer extends AbstractDurableConfiguredObjectRecoverer<AMQQueue>
{
    private static final Logger _logger = Logger.getLogger(QueueRecoverer.class);
    private final VirtualHostImpl<?,?,?> _virtualHost;
    private final ConfiguredObjectFactory _objectFactory;

    public QueueRecoverer(final VirtualHostImpl virtualHost)
    {
        _virtualHost = virtualHost;
        Broker<?> broker = _virtualHost.getParent(Broker.class);
        _objectFactory = broker.getObjectFactory();
    }

    @Override
    public String getType()
    {
        return Queue.class.getSimpleName();
    }

    @Override
    public UnresolvedObject<AMQQueue> createUnresolvedObject(final ConfiguredObjectRecord record)
    {
        return new UnresolvedQueue(record);
    }

    private class UnresolvedQueue implements UnresolvedObject<AMQQueue>
    {

      //  private final UUID _alternateExchangeId;
        private final ConfiguredObjectRecord _record;
        private AMQQueue _queue;
        private List<UnresolvedDependency> _dependencies = new ArrayList<UnresolvedDependency>();
        private ExchangeImpl _alternateExchange;
        private UUID _alternateExchangeId;
        private String _alternateExchangeName;

        public UnresolvedQueue(ConfiguredObjectRecord record)
        {
            _record = record;
            Object altExchObj = record.getAttributes().get(Queue.ALTERNATE_EXCHANGE);
            if(altExchObj instanceof UUID)
            {
                _alternateExchangeId = (UUID) altExchObj;
                _dependencies.add(new AlternateExchangeDependency());
            }
            else if (altExchObj instanceof String)
            {
                try
                {
                    _alternateExchangeId = UUID.fromString((String)altExchObj);
                }
                catch (IllegalArgumentException e)
                {
                    _alternateExchangeName = (String) altExchObj;
                }
                _dependencies.add(new AlternateExchangeDependency());
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
            String queueName = (String) _record.getAttributes().get(Queue.NAME);

            _queue = _virtualHost.getQueue(_record.getId());
            if(_queue == null)
            {
                _queue = _virtualHost.getQueue(queueName);
            }

            if (_queue == null)
            {
                Map<String,Object> attributesWithId = new HashMap<String,Object>(_record.getAttributes());
                attributesWithId.put(Queue.ID,_record.getId());
                attributesWithId.put(Queue.DURABLE,true);

                ConfiguredObjectTypeFactory<? extends Queue> configuredObjectTypeFactory =
                        _objectFactory.getConfiguredObjectTypeFactory(Queue.class, attributesWithId);
                UnresolvedConfiguredObject<? extends Queue> unresolvedConfiguredObject =
                        configuredObjectTypeFactory.recover(_record, _virtualHost);
                _queue = (AMQQueue<?>) unresolvedConfiguredObject.resolve();
            }
            _queue.open();
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
            public String getName()
            {
                return _alternateExchangeName;
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
