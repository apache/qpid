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

package org.apache.qpid.server.model.port;

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
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.server.model.*;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ParameterizedTypeImpl;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;

abstract public class AbstractPort<X extends AbstractPort<X>> extends AbstractConfiguredObject<X> implements Port<X>
{
    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(NAME, String.class);
        put(PROTOCOLS, new ParameterizedTypeImpl(Set.class, Protocol.class));
        put(TRANSPORTS, new ParameterizedTypeImpl(Set.class, Transport.class));
        put(TRUST_STORES, new ParameterizedTypeImpl(Set.class, String.class));
        put(KEY_STORE, String.class);
        put(PORT, Integer.class);
        put(TCP_NO_DELAY, Boolean.class);
        put(RECEIVE_BUFFER_SIZE, Integer.class);
        put(SEND_BUFFER_SIZE, Integer.class);
        put(NEED_CLIENT_AUTH, Boolean.class);
        put(WANT_CLIENT_AUTH, Boolean.class);
        put(BINDING_ADDRESS, String.class);
        put(STATE, State.class);
        put(AUTHENTICATION_PROVIDER, String.class);
    }});

    public static final Transport DEFAULT_TRANSPORT = Transport.TCP;

    private final Broker<?> _broker;
    private AtomicReference<State> _state;

    @ManagedAttributeField
    private int _port;

    @ManagedAttributeField
    private String _bindingAddress;

    @ManagedAttributeField
    private KeyStore<?> _keyStore;

    @ManagedAttributeField
    private Collection<TrustStore> _trustStores;

    @ManagedAttributeField
    private Set<Transport> _transports;

    @ManagedAttributeField
    private Set<Protocol> _protocols;

    public AbstractPort(UUID id,
                        Broker<?> broker,
                        Map<String, Object> attributes,
                        TaskExecutor taskExecutor)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, broker),
              combineIdWithAttributes(id,attributes),
              taskExecutor);
        _broker = broker;

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
    }

    @Override
    public void validate()
    {
        super.validate();

        boolean useTLSTransport = getTransports().contains(Transport.SSL) || getTransports().contains(Transport.WSS);

        if(useTLSTransport && getKeyStore() == null)
        {
            throw new IllegalConfigurationException("Can't create a port which uses a secure transport but has no KeyStore");
        }
    }

    @Override
    public String getBindingAddress()
    {
        return _bindingAddress;
    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public Set<Transport> getTransports()
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
    public Set<Protocol> getProtocols()
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
        for(VirtualHost<?,?,?> vh : _broker.getVirtualHosts())
        {
            for(VirtualHostAlias<?> alias : vh.getAliases())
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
    public Set<Protocol> getAvailableProtocols()
    {
        Set<Protocol> protocols = getProtocols();
        if(protocols == null || protocols.isEmpty())
        {
            protocols = getDefaultProtocols();
        }
        return protocols;
    }

    protected abstract Set<Protocol> getDefaultProtocols();

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();
    }

    @Override
    public State getState()
    {
        return _state.get();
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
    public Object getAttribute(String name)
    {
        if(ID.equals(name))
        {
            return getId();
        }
        else if(STATE.equals(name))
        {
            return getState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(getClass());
    }

    @Override
    public boolean setState(State currentState, State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if (state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED  || state == State.ERRORED)
            {
                if( _state.compareAndSet(state, State.DELETED))
                {
                    onStop();
                    deleted();
                    return true;
                }
            }
            else
            {
                throw new IllegalStateException("Cannot delete port in " + state + " state");
            }
        }
        else if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    onActivate();
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    throw e;
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot activate port in " + state + " state");
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                onStop();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop port in " + state + " state");
            }
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
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> converted = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);

        Map<String, Object> merged = generateEffectiveAttributes(converted);

        String newName = (String) merged.get(NAME);
        if(!getName().equals(newName))
        {
            throw new IllegalConfigurationException("Changing the port name is not allowed");
        }

        if(converted.containsKey(PORT))
        {
            Integer newPort = (Integer) merged.get(PORT);
            if (getPort() != newPort)
            {
                for (Port p : _broker.getPorts())
                {
                    if (p.getPort() == newPort)
                    {
                        throw new IllegalConfigurationException("Port number "
                                                                + newPort
                                                                + " is already in use by port "
                                                                + p.getName());
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        Collection<Transport> transports = (Collection<Transport>)merged.get(TRANSPORTS);
        @SuppressWarnings("unchecked")
        Collection<Protocol> protocols = (Collection<Protocol>)merged.get(PROTOCOLS);
        Boolean needClientCertificate = (Boolean)merged.get(NEED_CLIENT_AUTH);
        Boolean wantClientCertificate = (Boolean)merged.get(WANT_CLIENT_AUTH);
        boolean requiresCertificate = (needClientCertificate != null && needClientCertificate.booleanValue())
                || (wantClientCertificate != null && wantClientCertificate.booleanValue());

        String keyStoreName = (String) merged.get(KEY_STORE);
        if(keyStoreName != null)
        {
            if (_broker.findKeyStoreByName(keyStoreName) == null)
            {
                throw new IllegalConfigurationException("Can't find key store with name '" + keyStoreName + "' for port " + getName());
            }
        }

        Collection<String> trustStoreNames = (Collection<String>) merged.get(TRUST_STORES);
        boolean hasTrustStore = trustStoreNames != null && !trustStoreNames.isEmpty();
        if(hasTrustStore)
        {
            for (String trustStoreName : trustStoreNames)
            {
                if (_broker.findTrustStoreByName(trustStoreName) == null)
                {
                    throw new IllegalConfigurationException("Cannot find trust store with name '" + trustStoreName + "'");
                }
            }
        }

        boolean usesSsl = transports != null && transports.contains(Transport.SSL);
        if (usesSsl)
        {
            if (keyStoreName == null)
            {
                throw new IllegalConfigurationException("Can't create port which requires SSL but has no key store configured.");
            }

            if (!hasTrustStore && requiresCertificate)
            {
                throw new IllegalConfigurationException("Can't create port which requests SSL client certificates but has no trust store configured.");
            }
        }
        else
        {
            if (requiresCertificate)
            {
                throw new IllegalConfigurationException("Can't create port which requests SSL client certificates but doesn't use SSL transport.");
            }
        }

        if (protocols != null && protocols.contains(Protocol.RMI) && usesSsl)
        {
            throw new IllegalConfigurationException("Can't create RMI Registry port which requires SSL.");
        }

        String authenticationProviderName = (String)merged.get(AUTHENTICATION_PROVIDER);
        if (authenticationProviderName != null)
        {
            Collection<AuthenticationProvider<?>> providers = _broker.getAuthenticationProviders();
            AuthenticationProvider<?> provider = null;
            for (AuthenticationProvider<?> p : providers)
            {
                if (p.getName().equals(authenticationProviderName))
                {
                    provider = p;
                    break;
                }
            }

            if (provider == null)
            {
                throw new IllegalConfigurationException("Cannot find authentication provider with name '"
                        + authenticationProviderName + "'");
            }
        }
        else
        {
            if (protocols != null && !protocols.contains(Protocol.RMI))
            {
                throw new IllegalConfigurationException("An authentication provider must be specified");
            }
        }

        super.changeAttributes(attributes);
    }

    @Override
    protected void authoriseSetDesiredState(State currentState, State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of port is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of port attributes is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of port attributes is denied");
        }
    }

    @Override
    public KeyStore getKeyStore()
    {
        return _keyStore;
    }

    @Override
    public Collection<TrustStore> getTrustStores()
    {
        return _trustStores;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", port=" + getPort() + "]";
    }


    protected void validateOnlyOneInstance()
    {
        Broker<?> broker = getParent(Broker.class);
        if(!broker.isManagementMode())
        {
            //ManagementMode needs this relaxed to allow its overriding management ports to be inserted.

            //Enforce only a single port of each management protocol, as the plugins will only use one.
            Collection<Port<?>> existingPorts = broker.getPorts();
            existingPorts.remove(this);

            for (Port<?> existingPort : existingPorts)
            {
                Collection<Protocol> portProtocols = existingPort.getAvailableProtocols();
                if (portProtocols != null)
                {
                    final ArrayList<Protocol> intersection = new ArrayList(portProtocols);
                    intersection.retainAll(getAvailableProtocols());
                    if(!intersection.isEmpty())
                    {
                        throw new IllegalConfigurationException("Port for protocols " + intersection + " already exists. Only one management port per protocol can be created.");
                    }
                }
            }
        }
    }
}
