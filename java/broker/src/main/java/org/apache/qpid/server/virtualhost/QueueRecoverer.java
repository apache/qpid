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

import java.util.Map;
import java.util.UUID;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.store.AbstractDurableConfiguredObjectRecoverer;
import org.apache.qpid.server.store.UnresolvedDependency;
import org.apache.qpid.server.store.UnresolvedObject;

public class QueueRecoverer extends AbstractDurableConfiguredObjectRecoverer<AMQQueue>
{
    private static final Logger _logger = Logger.getLogger(QueueRecoverer.class);
    private final VirtualHost _virtualHost;
    private final ExchangeRegistry _exchangeRegistry;

    public QueueRecoverer(final VirtualHost virtualHost, final ExchangeRegistry exchangeRegistry)
    {
        _virtualHost = virtualHost;
        _exchangeRegistry = exchangeRegistry;
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
        private AMQQueue _queue;

        public UnresolvedQueue(final UUID id,
                               final String type,
                               final Map<String, Object> attributeMap)
        {
            String queueName = (String) attributeMap.get(Queue.NAME);
            String owner = (String) attributeMap.get(Queue.OWNER);
            boolean exclusive = (Boolean) attributeMap.get(Queue.EXCLUSIVE);
            UUID alternateExchangeId = attributeMap.get(Queue.ALTERNATE_EXCHANGE) == null ? null : UUID.fromString((String)attributeMap.get(Queue.ALTERNATE_EXCHANGE));
            @SuppressWarnings("unchecked")
            Map<String, Object> queueArgumentsMap = (Map<String, Object>) attributeMap.get(Queue.ARGUMENTS);
            try
            {
                _queue = _virtualHost.getQueueRegistry().getQueue(id);
                if(_queue == null)
                {
                    _queue = _virtualHost.getQueueRegistry().getQueue(queueName);
                }

                if (_queue == null)
                {
                    _queue = AMQQueueFactory.createAMQQueueImpl(id, queueName, true, owner, false, exclusive, _virtualHost,
                                                           queueArgumentsMap);
                    _virtualHost.getQueueRegistry().registerQueue(_queue);

                    if (alternateExchangeId != null)
                    {
                        Exchange altExchange = _exchangeRegistry.getExchange(alternateExchangeId);
                        if (altExchange == null)
                        {
                            _logger.error("Unknown exchange id " + alternateExchangeId + ", cannot set alternate exchange on queue with id " + id);
                            return;
                        }
                        _queue.setAlternateExchange(altExchange);
                    }
                }
            }
            catch (AMQException e)
            {
                throw new RuntimeException("Error recovering queue uuid " + id + " name " + queueName, e);
            }
        }

        @Override
        public UnresolvedDependency[] getUnresolvedDependencies()
        {
            return new UnresolvedDependency[0];
        }

        @Override
        public AMQQueue resolve()
        {
            return _queue;
        }
    }
}
