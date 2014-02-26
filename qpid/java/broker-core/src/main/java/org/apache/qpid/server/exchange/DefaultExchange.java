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
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.plugin.ExchangeType;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.store.StorableMessageMetaData;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class DefaultExchange implements ExchangeImpl<DirectExchange>
{

    private final QueueRegistry _queueRegistry;
    private UUID _id;
    private VirtualHost _virtualHost;
    private static final Logger _logger = Logger.getLogger(DefaultExchange.class);

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
    public ExchangeType<DirectExchange> getExchangeType()
    {
        return DirectExchange.TYPE;
    }


    @Override
    public boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        throw new AccessControlException("Cannot add bindings to the default exchange");
    }

    @Override
    public boolean deleteBinding(final String bindingKey, final AMQQueue queue)
    {
        throw new AccessControlException("Cannot delete bindings from the default exchange");
    }

    @Override
    public boolean hasBinding(final String bindingKey, final AMQQueue queue)
    {
        return false;
    }

    @Override
    public boolean replaceBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments)
    {
        throw new AccessControlException("Cannot replace bindings on the default exchange");
    }

    @Override
    public void restoreBinding(UUID id, String bindingKey, AMQQueue queue, Map<String, Object> argumentMap)
    {
        _logger.warn("Bindings to the default exchange should not be stored in the configuration store");
    }

    @Override
    public String getTypeName()
    {
        return getExchangeType().getType();
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
        return !_virtualHost.getQueues().isEmpty();
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
    public ExchangeImpl getAlternateExchange()
    {
        return null;
    }

    @Override
    public void setAlternateExchange(ExchangeImpl exchange)
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
    public void addBindingListener(BindingListener listener)
    {

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
                          final Action<? super MessageInstance> postEnqueueAction)
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

    private static final StateChangeListener<BindingImpl, State> STATE_CHANGE_LISTENER =
            new StateChangeListener<BindingImpl, State>()
            {
                @Override
                public void stateChanged(final BindingImpl object, final State oldState, final State newState)
                {
                    if(newState == State.DELETED)
                    {
                        throw new AccessControlException("Cannot remove bindings to the default exchange");
                    }
                }
            };
}
