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

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;

public class BrokerImpl extends AbstractConfiguredObject implements Broker
{
    private final Collection<VirtualHost> _virtualHosts = new ArrayList<VirtualHost>();
    private final Collection<Port> _ports = new ArrayList<Port>();
    private final Collection<AuthenticationProvider> _authenticationProviders = new ArrayList<AuthenticationProvider>();

    public BrokerImpl(final UUID id,
                      final String name,
                      final State state,
                      final boolean durable,
                      final LifetimePolicy lifetimePolicy,
                      final long timeToLive,
                      final Map<String, Object> attributes)
    {
        super(id, name, state, durable, lifetimePolicy, timeToLive, attributes, Collections.EMPTY_MAP);
    }

    @Override
    protected Object getLock()
    {
        return this;
    }

    public Collection<VirtualHost> getVirtualHosts()
    {
        synchronized (getLock())
        {
            return new ArrayList<VirtualHost>(_virtualHosts);
        }
    }

    public Collection<Port> getPorts()
    {
        synchronized (getLock())
        {
            return new ArrayList<Port>(_ports);
        }
    }

    public Collection<AuthenticationProvider> getAuthenticationProviders()
    {
        synchronized (getLock())
        {
            return new ArrayList<AuthenticationProvider>(_authenticationProviders);
        }
    }

    public State getActualState()
    {
        return getDesiredState();
    }

    public Statistics getStatistics()
    {
        // TODO
        return null;
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    public VirtualHost createVirtualHost(String name, State initialState,boolean durable,
                                         LifetimePolicy lifetime, long ttl, Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        // TODO - check name is valid and not reserved
        // TODO - check permissions

        synchronized (getLock())
        {
            for(VirtualHost virtualHost : _virtualHosts)
            {
                if(virtualHost.getName().equals(name))
                {
                    throw new IllegalArgumentException("A virtual host with the name '"+name+"' already exists");
                }
            }
            VirtualHostImpl vhost = new VirtualHostImpl(UUID.randomUUID(),
                                                        name,
                                                        initialState,
                                                        durable,
                                                        lifetime,
                                                        ttl,
                                                        attributes,
                                                        this);
            _virtualHosts.add(vhost);

            // TODO - create a mapping for each port with "default" authentication provider and alias of the vhost name?

            childAdded(VirtualHost.class, vhost);
            return vhost;
        }
    }

    public void deleteVirtualHost(VirtualHost virtualHost)
    {
        synchronized (getLock())
        {
            boolean found = _virtualHosts.remove(virtualHost);
            if (!found)
            {
                throw new IllegalArgumentException("A virtual host with the name '" + virtualHost.getName() + "' does not exist");
            }
            childRemoved(VirtualHost.class, virtualHost);
        }
    }

}
