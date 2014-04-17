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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;

public class BindingRecoverer extends AbstractDurableConfiguredObjectRecoverer<BindingImpl>
{
    private static final Logger _logger = Logger.getLogger(BindingRecoverer.class);

    private final VirtualHostImpl _virtualHost;

    public BindingRecoverer(final VirtualHostImpl virtualHost)
    {
        _virtualHost = virtualHost;
    }

    @Override
    public UnresolvedObject<BindingImpl> createUnresolvedObject(ConfiguredObjectRecord record)
    {
        return new UnresolvedBinding(record);
    }

    @Override
    public String getType()
    {
        return org.apache.qpid.server.model.Binding.class.getSimpleName();
    }

    private class UnresolvedBinding implements UnresolvedObject<BindingImpl>
    {
        private final Map<String, Object> _bindingArgumentsMap;
        private final String _bindingName;
        private final UUID _queueId;
        private final UUID _exchangeId;
        private final UUID _bindingId;

        private List<UnresolvedDependency> _unresolvedDependencies =
                new ArrayList<UnresolvedDependency>();

        private ExchangeImpl _exchange;
        private AMQQueue _queue;

        public UnresolvedBinding(final ConfiguredObjectRecord record)
        {
            _bindingId = record.getId();
            _exchangeId = record.getParents().get(Exchange.class.getSimpleName()).getId();
            _queueId = record.getParents().get(Queue.class.getSimpleName()).getId();
            _exchange = _virtualHost.getExchange(_exchangeId);
            if(_exchange == null)
            {
                _unresolvedDependencies.add(new ExchangeDependency());
            }
            _queue = _virtualHost.getQueue(_queueId);
            if(_queue == null)
            {
                _unresolvedDependencies.add(new QueueDependency());
            }

            _bindingName = (String) record.getAttributes().get(org.apache.qpid.server.model.Binding.NAME);
            _bindingArgumentsMap = (Map<String, Object>) record.getAttributes().get(org.apache.qpid.server.model.Binding.ARGUMENTS);
        }

        @Override
        public UnresolvedDependency[] getUnresolvedDependencies()
        {
            return _unresolvedDependencies.toArray(new UnresolvedDependency[_unresolvedDependencies.size()]);
        }

        @Override
        public BindingImpl resolve()
        {
            if(!_exchange.hasBinding(_bindingName, _queue))
            {
                _logger.info("Restoring binding: (Exchange: " + _exchange.getName() + ", Queue: " + _queue.getName()
                             + ", Routing Key: " + _bindingName + ", Arguments: " + _bindingArgumentsMap + ")");

                _exchange.restoreBinding(_bindingId, _bindingName, _queue, _bindingArgumentsMap);
            }
            return (_exchange).getBinding(_bindingName, _queue);
        }

        private class QueueDependency implements UnresolvedDependency<AMQQueue>
        {

            @Override
            public UUID getId()
            {
                return _queueId;
            }

            @Override
            public String getType()
            {
                return Queue.class.getSimpleName();
            }

            @Override
            public void resolve(final AMQQueue dependency)
            {
                _queue = dependency;
                _unresolvedDependencies.remove(this);
            }

        }

        private class ExchangeDependency implements UnresolvedDependency<ExchangeImpl>
        {

            @Override
            public UUID getId()
            {
                return _exchangeId;
            }

            @Override
            public String getType()
            {
                return org.apache.qpid.server.model.Exchange.class.getSimpleName();
            }

            @Override
            public void resolve(final ExchangeImpl dependency)
            {
                _exchange = dependency;
                _unresolvedDependencies.remove(this);
            }
        }
    }
}
