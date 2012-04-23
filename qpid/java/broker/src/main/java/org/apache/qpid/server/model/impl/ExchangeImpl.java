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
package org.apache.qpid.server.model.impl;

import org.apache.qpid.server.model.Binding;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Publisher;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class ExchangeImpl extends AbstractConfiguredObject implements Exchange
{

    private final VirtualHostImpl _virtualHost;

    ExchangeImpl(final UUID id,
                 final String name,
                 final State state,
                 final boolean durable,
                 final LifetimePolicy lifetimePolicy,
                 final long timeToLive,
                 final String exchangeType,
                 final Map<String, Object> attributes,
                 final VirtualHostImpl parent)
    {
        super(id, name, state, durable, lifetimePolicy, timeToLive, fixAttributes(attributes, exchangeType),
              (Map) Collections.singletonMap(VirtualHost.class, parent));
        _virtualHost = parent;

    }

    private static Map<String, Object> fixAttributes(final Map<String, Object> attributes, final String exchangeType)
    {
        Map<String,Object> fixedAttributes = new HashMap<String, Object>(attributes);
        fixedAttributes.put(TYPE, exchangeType);
        return fixedAttributes;
    }

    @Override
    protected Object getLock()
    {
        return _virtualHost.getLock();
    }

    public String getExchangeType()
    {
        return (String) getAttribute(TYPE);
    }

    public Collection<Binding> getBindings()
    {
        return null;  //TODO
    }

    public Collection<Publisher> getPublishers()
    {
        return null;  //TODO
    }

    public Binding createBinding(String bindingKey, Queue queue, Map<String, Object> bindingArguments,
                                 Map<String, Object> attributes)
    {
        return null;  // TODO - Implement
    }

    public void delete()
    {
        // TODO - Implement
    }

    public State getActualState()
    {
        State vhostState = _virtualHost.getActualState();
        return vhostState == State.ACTIVE ? getDesiredState() : vhostState;
    }

    public Statistics getStatistics()
    {
        return null; // TODO
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException(); //TODO
    }
}
