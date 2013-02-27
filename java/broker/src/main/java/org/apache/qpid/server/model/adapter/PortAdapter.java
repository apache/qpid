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

package org.apache.qpid.server.model.adapter;

import java.lang.reflect.Type;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;
import org.apache.qpid.server.configuration.updater.TaskExecutor;

public class PortAdapter extends AbstractAdapter implements Port
{
    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(PROTOCOLS, new ParameterizedTypeImpl(Set.class, Protocol.class));
        put(TRANSPORTS, new ParameterizedTypeImpl(Set.class, Transport.class));
        put(PORT, Integer.class);
        put(TCP_NO_DELAY, Boolean.class);
        put(RECEIVE_BUFFER_SIZE, Integer.class);
        put(SEND_BUFFER_SIZE, Integer.class);
        put(NEED_CLIENT_AUTH, Boolean.class);
        put(WANT_CLIENT_AUTH, Boolean.class);
        put(BINDING_ADDRESS, String.class);
        put(STATE, State.class);
        put(AUTHENTICATION_MANAGER, String.class);
    }});

    private final Broker _broker;
    private AuthenticationProvider _authenticationProvider;

    /*
     * TODO register PortAceptor as a listener. For supporting multiple
     * protocols on the same port we need to introduce a special entity like
     * PortAceptor which will be responsible for port binding/unbinding
     */
    public PortAdapter(UUID id, Broker broker, Map<String, Object> attributes, Map<String, Object> defaults, TaskExecutor taskExecutor)
    {
        super(id, defaults, MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        _broker = broker;
        addParent(Broker.class, broker);
    }

    @Override
    public String getBindingAddress()
    {
        return (String)getAttribute(BINDING_ADDRESS);
    }

    @Override
    public int getPort()
    {
        return (Integer)getAttribute(PORT);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Transport> getTransports()
    {
        return (Collection<Transport>)getAttribute(TRANSPORTS);
    }

    @Override
    public void addTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Transport removeTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Protocol> getProtocols()
    {
        return (Collection<Protocol>)getAttribute(PROTOCOLS);
    }

    @Override
    public void addProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Protocol removeProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostBindings()
    {
        List<VirtualHostAlias> aliases = new ArrayList<VirtualHostAlias>();
        for(VirtualHost vh : _broker.getVirtualHosts())
        {
            for(VirtualHostAlias alias : vh.getAliases())
            {
                if(alias.getPort().equals(this))
                {
                    aliases.add(alias);
                }
            }
        }
        return Collections.unmodifiableCollection(aliases);
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return null;
    }

    @Override
    public String getName()
    {
        return (String)getAttribute(NAME);
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }

    @Override
    public State getActualState()
    {
        State state = (State)super.getAttribute(STATE);
        if (state == null)
        {
            return State.ACTIVE;
        }
        return state;
    }

    @Override
    public boolean isDurable()
    {
        return false;
    }

    @Override
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public long getTimeToLive()
    {
        return 0;
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAttribute(String name)
    {
        if(ID.equals(name))
        {
            return getId();
        }
        else if(STATE.equals(name))
        {
            return getActualState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            return getTimeToLive();
        }
        else if(CREATED.equals(name))
        {

        }
        else if(UPDATED.equals(name))
        {

        }
        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    @Override
    public boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.DELETED)
        {
            return true;
        }
        else if (desiredState == State.ACTIVE)
        {
            onActivate();
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            onStop();
            return true;
        }
        return false;
    }

    protected void onActivate()
    {
        // no-op: expected to be overridden by subclass
    }

    protected void onStop()
    {
        // no-op: expected to be overridden by subclass
    }

    @Override
    public AuthenticationProvider getAuthenticationProvider()
    {
        return _authenticationProvider;
    }

    public void setAuthenticationProvider(AuthenticationProvider authenticationProvider)
    {
        _authenticationProvider = authenticationProvider;
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        super.changeAttributes(MapValueConverter.convert(attributes, ATTRIBUTE_TYPES));
    }
}
