/*
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
 */
package org.apache.qpid.server.exchange;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.ExchangeMessages;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DefaultExchange implements Exchange<DirectExchange>
{

    private final QueueRegistry _queueRegistry;
    private UUID _id;
    private VirtualHost _virtualHost;
    private static final Logger _logger = Logger.getLogger(DefaultExchange.class);
    private final AtomicBoolean _closed = new AtomicBoolean();

    private Map<ExchangeReferrer,Object> _referrers = new ConcurrentHashMap<ExchangeReferrer,Object>();

    public DefaultExchange(VirtualHost virtualHost, QueueRegistry queueRegistry, UUID id)
    {
        _virtualHost =  virtualHost;
        _queueRegistry = queueRegistry;
        _id = id;
    }

    @Override
    public String getName()
    {
        return ExchangeDefaults.DEFAULT_EXCHANGE_NAME;
    }

    @Override
    public ExchangeType<DirectExchange> getType()
    {
        return DirectExchange.TYPE;
    }

    @Override
    public long getBindingCount()
    {
        return _virtualHost.getQueues().size();
    }

    @Override
    public long getByteDrops()
    {
        return 0;
    }

    @Override
    public long getByteReceives()
    {
        return 0;
    }

    @Override
    public long getMsgDrops()
    {
        return 0;
    }

    @Override
    public long getMsgReceives()
    {
        return 0;
    }

    @Override
    public boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        throw new AccessControlException("Cannot add bindings to the default exchange");
    }

    @Override
    public boolean replaceBinding(UUID id, String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        throw new AccessControlException("Cannot replace bindings on the default exchange");
    }

    @Override
    public void restoreBinding(UUID id, String bindingKey, AMQQueue queue, Map<String, Object> argumentMap)
    {
        _logger.warn("Bindings to the default exchange should not be stored in the configuration store");
    }

    @Override
    public void removeBinding(Binding b)
    {
        throw new AccessControlException("Cannot remove bindings to the default exchange");
    }

    @Override
    public Binding removeBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        throw new AccessControlException("Cannot remove bindings to the default exchange");
    }

    @Override
    public Binding getBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        if(_virtualHost.getQueue(bindingKey) == queue && (arguments == null || arguments.isEmpty()))
        {
            return convertToBinding(queue);
        }
        else
        {
            return null;
        }

    }

    private Binding convertToBinding(AMQQueue queue)
    {
        String queueName = queue.getName();

        UUID exchangeId = UUIDGenerator.generateBindingUUID(ExchangeDefaults.DEFAULT_EXCHANGE_NAME,
                                                            queueName,
                                                            queueName,
                                                            _virtualHost.getName());

        return new Binding(exchangeId, queueName, queue, this, Collections.EMPTY_MAP);
    }

    @Override
    public String getTypeName()
    {
        return getType().getType();
    }

    @Override
    public boolean isDurable()
    {
        return false;
    }

    @Override
    public boolean isAutoDelete()
    {
        return false;
    }

    @Override
    public void close()
    {
        throw new AccessControlException("Cannot close the default exchange");
    }

    @Override
    public boolean isBound(AMQQueue queue)
    {
        return _virtualHost.getQueue(queue.getName()) == queue;
    }

    @Override
    public boolean hasBindings()
    {
        return getBindingCount() != 0;
    }

    @Override
    public boolean isBound(String bindingKey, AMQQueue queue)
    {
        return isBound(queue) && queue.getName().equals(bindingKey);
    }

    @Override
    public boolean isBound(String bindingKey, Map<String, Object> arguments, AMQQueue queue)
    {
        return isBound(bindingKey, queue) && (arguments == null || arguments.isEmpty());
    }

    @Override
    public boolean isBound(Map<String, Object> arguments, AMQQueue queue)
    {
        return (arguments == null || arguments.isEmpty()) && isBound(queue);
    }

    @Override
    public boolean isBound(String bindingKey, Map<String, Object> arguments)
    {
        return (arguments == null || arguments.isEmpty()) && isBound(bindingKey);
    }

    @Override
    public boolean isBound(Map<String, Object> arguments)
    {
        return (arguments == null || arguments.isEmpty()) && hasBindings();
    }

    @Override
    public boolean isBound(String bindingKey)
    {
        return _virtualHost.getQueue(bindingKey) != null;
    }

    @Override
    public Exchange getAlternateExchange()
    {
        return null;
    }

    @Override
    public void setAlternateExchange(Exchange exchange)
    {
        _logger.warn("Cannot set the alternate exchange for the default exchange");
    }

    @Override
    public void removeReference(ExchangeReferrer exchange)
    {
        _referrers.remove(exchange);
    }

    @Override
    public void addReference(ExchangeReferrer exchange)
    {
        _referrers.put(exchange, Boolean.TRUE);
    }

    @Override
    public boolean hasReferrers()
    {
        return !_referrers.isEmpty();
    }

    @Override
    public Collection<Binding> getBindings()
    {
        List<Binding> bindings = new ArrayList<Binding>();
        for(AMQQueue q : _virtualHost.getQueues())
        {
            bindings.add(convertToBinding(q));
        }
        return bindings;
    }

    @Override
    public void addBindingListener(BindingListener listener)
    {
        _queueRegistry.addRegistryChangeListener(convertListener(listener));
    }

    private QueueRegistry.RegistryChangeListener convertListener(final BindingListener listener)
    {
        return new QueueRegistry.RegistryChangeListener()
        {
            @Override
            public void queueRegistered(AMQQueue queue)
            {
                listener.bindingAdded(DefaultExchange.this, convertToBinding(queue));
            }

            @Override
            public void queueUnregistered(AMQQueue queue)
            {
                listener.bindingRemoved(DefaultExchange.this, convertToBinding(queue));
            }
        };
    }

    @Override
    public void removeBindingListener(BindingListener listener)
    {
        // TODO
    }

    @Override
    public UUID getId()
    {
        return _id;
    }

    public final  <M extends ServerMessage<? extends StorableMessageMetaData>> int send(final M message,
                          final InstanceProperties instanceProperties,
                          final ServerTransaction txn,
                          final Action<? super MessageInstance<?, ? extends Consumer>> postEnqueueAction)
    {
        final AMQQueue q = _virtualHost.getQueue(message.getRoutingKey());
        if(q == null)
        {
            return 0;
        }
        else
        {
            txn.enqueue(q,message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        q.enqueue(message, postEnqueueAction);
                    }
                    finally
                    {
                        _reference.release();
                    }
                }

                public void onRollback()
                {
                    _reference.release();
                }
            });
            return 1;
        }
    }

}
