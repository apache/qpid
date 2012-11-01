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

import java.net.InetSocketAddress;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

public class PortAdapter extends AbstractAdapter implements Port
{

    private final String _name;
    private final Broker _broker;
    private final Set<Protocol> _protocols;
    private final Set<Transport> _transports;
    private final InetSocketAddress _bindingSocketAddress;
    private final boolean _tcpNoDelay;
    private final int _receiveBufferSize;
    private final int _sendBufferSize;
    private final boolean _needClientAuth;
    private final boolean _wantClientAuth;
    private final String _authenticationManager;
    private AuthenticationProvider _authenticationProvider;

    /*
     * TODO register PortAceptor as a listener. For supporting multiple
     * protocols on the same port we need to introduce a special entity like
     * PortAceptor which will be responsible for port binding/unbinding
     */
    public PortAdapter(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id);
        _broker = broker;

        addParent(Broker.class, broker);

        String bindingAddress = MapValueConverter.getStringAttribute(BINDING_ADDRESS, attributes, null);
        int portNumber = MapValueConverter.getIntegerAttribute(PORT, attributes, null);

        final Set<Protocol> protocolSet = MapValueConverter.getSetAttribute(PROTOCOLS, attributes);
        final Set<Transport> transportSet = MapValueConverter.getSetAttribute(TRANSPORTS, attributes);

        _bindingSocketAddress = determineBindingAddress(bindingAddress, portNumber);
        _name = MapValueConverter.getStringAttribute(NAME, attributes, _bindingSocketAddress.getHostName() + ":" + portNumber);
        _protocols = Collections.unmodifiableSet(new TreeSet<Protocol>(protocolSet));
        _transports = Collections.unmodifiableSet(new TreeSet<Transport>(transportSet));
        _tcpNoDelay = MapValueConverter.getBooleanAttribute(TCP_NO_DELAY, attributes);
        _receiveBufferSize = MapValueConverter.getIntegerAttribute(RECEIVE_BUFFER_SIZE, attributes);
        _sendBufferSize = MapValueConverter.getIntegerAttribute(SEND_BUFFER_SIZE, attributes);
        _needClientAuth = MapValueConverter.getBooleanAttribute(NEED_CLIENT_AUTH, attributes);
        _wantClientAuth = MapValueConverter.getBooleanAttribute(WANT_CLIENT_AUTH, attributes);
        _authenticationManager = MapValueConverter.getStringAttribute(AUTHENTICATION_MANAGER, attributes, null);
    }

    @Override
    public String getBindingAddress()
    {
        return _bindingSocketAddress.getAddress().getHostAddress();
    }

    @Override
    public int getPort()
    {
        return _bindingSocketAddress.getPort();
    }

    @Override
    public Collection<Transport> getTransports()
    {
        return _transports;
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

    @Override
    public Collection<Protocol> getProtocols()
    {
        return _protocols;
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
        return _name;
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }

    @Override
    public State getActualState()
    {
        return State.ACTIVE;
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
        else if(NAME.equals(name))
        {
            return getName();
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
        else if(BINDING_ADDRESS.equals(name))
        {
            return getBindingAddress();
        }
        else if(PORT.equals(name))
        {
            return getPort();
        }
        else if(PROTOCOLS.equals(name))
        {
            return getProtocols();
        }
        else if(TRANSPORTS.equals(name))
        {
            return getTransports();
        }
        else if(TCP_NO_DELAY.equals(name))
        {
            return isTcpNoDelay();
        }
        else if(SEND_BUFFER_SIZE.equals(name))
        {
            return getSendBufferSize();
        }
        else if(RECEIVE_BUFFER_SIZE.equals(name))
        {
            return getReceiveBufferSize();
        }
        else if(NEED_CLIENT_AUTH.equals(name))
        {
            return isNeedClientAuth();
        }
        else if(WANT_CLIENT_AUTH.equals(name))
        {
            return isWantClientAuth();
        }
        else if(AUTHENTICATION_MANAGER.equals(name))
        {
            return getAuthenticationManager();
        }
        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return super.setAttribute(name, expected, desired);
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

    private InetSocketAddress determineBindingAddress(String bindingAddress, int portNumber)
    {
        return bindingAddress == null ? new InetSocketAddress(portNumber) : new InetSocketAddress(bindingAddress, portNumber);
    }

    @Override
    public boolean isTcpNoDelay()
    {
        return _tcpNoDelay;
    }

    @Override
    public int getReceiveBufferSize()
    {
        return _receiveBufferSize;
    }

    @Override
    public int getSendBufferSize()
    {
        return _sendBufferSize;
    }

    @Override
    public boolean isNeedClientAuth()
    {
        return _needClientAuth;
    }

    @Override
    public boolean isWantClientAuth()
    {
        return _wantClientAuth;
    }

    @Override
    public String getAuthenticationManager()
    {
        return _authenticationManager;
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

}
