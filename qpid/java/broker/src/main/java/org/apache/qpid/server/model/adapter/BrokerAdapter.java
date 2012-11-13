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

import static org.apache.qpid.server.util.MapValueConverter.getLongAttribute;
import static org.apache.qpid.server.util.MapValueConverter.getIntegerAttribute;
import static org.apache.qpid.server.util.MapValueConverter.getBooleanAttribute;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerAdapter extends AbstractAdapter implements Broker, ConfigurationChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    private IApplicationRegistry _applicationRegistry;
    private String _name;
    private StatisticsAdapter _statistics;

    private final Map<String, VirtualHost> _vhostAdapters = new HashMap<String, VirtualHost>();
    private final Map<Integer, Port> _portAdapters = new HashMap<Integer, Port>();
    private final Map<String, AuthenticationProvider> _authenticationProviders = new HashMap<String, AuthenticationProvider>();
    private final Map<String, GroupProvider> _groupProviders = new HashMap<String, GroupProvider>();
    private final Map<UUID, ConfiguredObject> _plugins = new HashMap<UUID, ConfiguredObject>();

    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private AuthenticationProvider _defaultAuthenticationProvider;

    private final PortFactory _portFactory;

    private long _maximumMessageAge;
    private long _maximumMessageCount;
    private long _maximumQueueDepth;
    private long _maximumMessageSize;
    private long _minimumAlertRepeatGap;
    private long _flowResumeCapacity;
    private long _flowCapacity;
    private int _maximumDeliveryCount;
    private boolean _deadLetterQueueEnabled;
    private long _housekeepingCheckPeriod;


    public BrokerAdapter(UUID id, Map<String, Object> attributes, IApplicationRegistry instance,
            AuthenticationProviderFactory authenticationProviderFactory, PortFactory portFactory)
    {
        super(id);
        _name = "Broker";
        _applicationRegistry = instance;
        _name = "Broker";
        _statistics = new StatisticsAdapter(instance);
        _authenticationProviderFactory = authenticationProviderFactory;
        _portFactory = portFactory;

        _maximumMessageAge = getLongAttribute(ALERT_THRESHOLD_MESSAGE_AGE, attributes, Long.getLong(BrokerProperties.PROPERTY_MAXIMUM_MESSAGE_AGE, 0));
        _maximumMessageCount = getLongAttribute(ALERT_THRESHOLD_MESSAGE_COUNT, attributes, Long.getLong(BrokerProperties.PROPERTY_MAXIMUM_MESSAGE_COUNT, 0));
        _maximumQueueDepth = getLongAttribute(ALERT_THRESHOLD_QUEUE_DEPTH, attributes, Long.getLong(BrokerProperties.PROPERTY_MAXIMUM_QUEUE_DEPTH, 0));
        _maximumMessageSize = getLongAttribute(ALERT_THRESHOLD_MESSAGE_SIZE, attributes, Long.getLong(BrokerProperties.PROPERTY_MAXIMUM_MESSAGE_SIZE, 0));
        _minimumAlertRepeatGap = getLongAttribute(ALERT_REPEAT_GAP, attributes, Long.getLong(BrokerProperties.PROPERTY_MINIMUM_ALERT_REPEAT_GAP, BrokerProperties.DEFAULT_MINIMUM_ALERT_REPEAT_GAP));
        _flowCapacity = getLongAttribute(FLOW_CONTROL_SIZE_BYTES, attributes, Long.getLong(BrokerProperties.PROPERTY_FLOW_CAPACITY, 0));
        _flowResumeCapacity = getLongAttribute(FLOW_CONTROL_RESUME_SIZE_BYTES, attributes, Long.getLong(BrokerProperties.PROPERTY_FLOW_RESUME_CAPACITY, _flowCapacity));
        _maximumDeliveryCount = getIntegerAttribute(MAXIMUM_DELIVERY_ATTEMPTS, attributes, 0);
        _deadLetterQueueEnabled = getBooleanAttribute(DEAD_LETTER_QUEUE_ENABLED, attributes, false);
        _housekeepingCheckPeriod = getLongAttribute(HOUSEKEEPING_CHECK_PERIOD, attributes, Long.getLong(BrokerProperties.PROPERTY_HOUSE_KEEPING_CHECK_PERIOD, BrokerProperties.DEFAULT_HOUSEKEEPING_PERIOD));
    }

    public Collection<VirtualHost> getVirtualHosts()
    {
        synchronized(_vhostAdapters)
        {
            return new ArrayList<VirtualHost>(_vhostAdapters.values());
        }

    }

    public Collection<Port> getPorts()
    {
        synchronized (_portAdapters)
        {
            final ArrayList<Port> ports = new ArrayList<Port>(_portAdapters.values());
            return ports;
        }
    }

    public Collection<AuthenticationProvider> getAuthenticationProviders()
    {
        synchronized (_authenticationProviders)
        {
            return new ArrayList<AuthenticationProvider>(_authenticationProviders.values());
        }
    }

    public AuthenticationProvider getAuthenticationProviderByName(String authenticationProviderName)
    {
        Collection<AuthenticationProvider> providers = getAuthenticationProviders();
        for (AuthenticationProvider authenticationProvider : providers)
        {
            if (authenticationProvider.getName().equals(authenticationProviderName))
            {
                return authenticationProvider;
            }
        }
        return null;
    }

    @Override
    public AuthenticationProvider getDefaultAuthenticationProvider()
    {
        return _defaultAuthenticationProvider;
    }

    public void setDefaultAuthenticationProvider(AuthenticationProvider provider)
    {
        _defaultAuthenticationProvider = provider;
    }

    @Override
    public Collection<GroupProvider> getGroupProviders()
    {
        synchronized (_groupProviders)
        {
            final ArrayList<GroupProvider> groupManagers =
                    new ArrayList<GroupProvider>(_groupProviders.values());
            return groupManagers;
        }
    }

    public VirtualHost createVirtualHost(final String name,
                                         final State initialState,
                                         final boolean durable,
                                         final LifetimePolicy lifetime,
                                         final long ttl,
                                         final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        return null;  //TODO
    }

    private VirtualHost createVirtualHost(final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        VirtualHostRegistry virtualHostRegistry = _applicationRegistry.getVirtualHostRegistry();
        final VirtualHostAdapter virtualHostAdapter = new VirtualHostAdapter(UUID.randomUUID(), this,
                attributes, virtualHostRegistry, (StatisticsGatherer)_applicationRegistry,
                _applicationRegistry.getSecurityManager(), null);

        synchronized (_vhostAdapters)
        {
            _vhostAdapters.put(virtualHostAdapter.getName(), virtualHostAdapter);
        }

        virtualHostAdapter.setState(State.INITIALISING, State.ACTIVE);
        childAdded(virtualHostAdapter);

        return virtualHostAdapter;
    }

    private boolean deleteVirtualHost(final VirtualHost vhost)
        throws AccessControlException, IllegalStateException
    {
        //TODO implement deleteVirtualHost
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String getName()
    {
        return _name;
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }


    public State getActualState()
    {
        return null;  //TODO
    }


    public boolean isDurable()
    {
        return true;
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public long getTimeToLive()
    {
        return 0;
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == VirtualHost.class)
        {
            return (Collection<C>) getVirtualHosts();
        }
        else if(clazz == Port.class)
        {
            return (Collection<C>) getPorts();
        }
        else if(clazz == AuthenticationProvider.class)
        {
            return (Collection<C>) getAuthenticationProviders();
        }
        else if(clazz == GroupProvider.class)
        {
            return (Collection<C>) getGroupProviders();
        }
        else if(clazz == ConfiguredObject.class)
        {
            return (Collection<C>) getPlugins();
        }

        return Collections.emptySet();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == VirtualHost.class)
        {
            return (C) createVirtualHost(attributes);
        }
        else if(childClass == Port.class)
        {
            return (C) createPort(attributes);
        }
        else if(childClass == AuthenticationProvider.class)
        {
            return (C) createAuthenticationProvider(attributes);
        }
        else
        {
            throw new IllegalArgumentException("Cannot create child of class " + childClass.getSimpleName());
        }
    }

    public void addPort(Port port)
    {
        synchronized (_portAdapters)
        {
            int portNumber = port.getPort();
            if(_portAdapters.containsKey(portNumber))
            {
                throw new IllegalArgumentException("Cannot add port " + port + " because port number " + portNumber + " already configured");
            }
            _portAdapters.put(portNumber, port);
        }
        port.addChangeListener(this);
    }

    private Port createPort(Map<String, Object> attributes)
    {
        Port port = _portFactory.createPort(UUID.randomUUID(), this, attributes);
        addPort(port);
        childAdded(port);
        return port;
    }

    private AuthenticationProvider createAuthenticationProvider(Map<String, Object> attributes)
    {
        // it's cheap to create the groupPrincipalAccessor on the fly
        GroupPrincipalAccessor groupPrincipalAccessor = new GroupPrincipalAccessor(_groupProviders.values());

        AuthenticationProvider authenticationProvider = _authenticationProviderFactory.create(UUID.randomUUID(), this, attributes, groupPrincipalAccessor);
        addAuthenticationProvider(authenticationProvider);
        childAdded(authenticationProvider);
        return authenticationProvider;
    }

    /**
     * @throws IllegalConfigurationException if an AuthenticationProvider with the same name already exists
     */
    public void addAuthenticationProvider(AuthenticationProvider authenticationProvider)
    {
        String name = authenticationProvider.getName();
        synchronized (_authenticationProviders)
        {
            if(_authenticationProviders.containsKey(name))
            {
                throw new IllegalConfigurationException("Cannot add AuthenticationProvider because one with name " + name + " already exists");
            }
            _authenticationProviders.put(name, authenticationProvider);
        }
        authenticationProvider.addChangeListener(this);
    }

    public void addGroupProvider(GroupProvider groupProvider)
    {
        synchronized (_groupProviders)
        {
            String name = groupProvider.getName();
            if(_groupProviders.containsKey(name))
            {
                throw new IllegalConfigurationException("Cannot add GroupProvider because one with name " + name + " already exists");
            }
            _groupProviders.put(name, groupProvider);
        }
        groupProvider.addChangeListener(this);
    }

    private boolean deleteGroupProvider(GroupProvider object)
    {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
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
            return State.ACTIVE;
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return LifetimePolicy.PERMANENT;
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            // TODO
        }
        else if(CREATED.equals(name))
        {
            // TODO
        }
        else if(UPDATED.equals(name))
        {
            // TODO
        }
        else if(BUILD_VERSION.equals(name))
        {
            return QpidProperties.getBuildVersion();
        }
        else if(BYTES_RETAINED.equals(name))
        {
            // TODO
        }
        else if(OPERATING_SYSTEM.equals(name))
        {
            return System.getProperty("os.name") + " "
                   + System.getProperty("os.version") + " "
                   + System.getProperty("os.arch");
        }
        else if(PLATFORM.equals(name))
        {
            return System.getProperty("java.vendor") + " "
                   + System.getProperty("java.runtime.version", System.getProperty("java.version"));
        }
        else if(PROCESS_PID.equals(name))
        {
            // TODO
        }
        else if(PRODUCT_VERSION.equals(name))
        {
            return QpidProperties.getReleaseVersion();
        }
        else if(SUPPORTED_STORE_TYPES.equals(name))
        {
            // TODO
        }
        else if (DEFAULT_AUTHENTICATION_PROVIDER.equals(name))
        {
            return getDefaultAuthenticationProvider();
        }
        else if (ALERT_THRESHOLD_MESSAGE_AGE.equals(name))
        {
            return _maximumMessageAge;
        }
        else if (ALERT_THRESHOLD_MESSAGE_COUNT.equals(name))
        {
            return _maximumMessageCount;
        }
        else if (ALERT_THRESHOLD_QUEUE_DEPTH.equals(name))
        {
            return _maximumQueueDepth;
        }
        else if (ALERT_THRESHOLD_MESSAGE_SIZE.equals(name))
        {
            return _maximumMessageSize;
        }
        else if (ALERT_REPEAT_GAP.equals(name))
        {
            return _minimumAlertRepeatGap;
        }
        else if (FLOW_CONTROL_SIZE_BYTES.equals(name))
        {
            return _flowCapacity;
        }
        else if (FLOW_CONTROL_RESUME_SIZE_BYTES.equals(name))
        {
            return _flowResumeCapacity;
        }
        else if (MAXIMUM_DELIVERY_ATTEMPTS.equals(name))
        {
            return _maximumDeliveryCount;
        }
        else if (DEAD_LETTER_QUEUE_ENABLED.equals(name))
        {
            return _deadLetterQueueEnabled;
        }
        else if (HOUSEKEEPING_CHECK_PERIOD.equals(name))
        {
            return _housekeepingCheckPeriod;
        }
        return super.getAttribute(name);    //TODO - Implement.
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return super.setAttribute(name, expected, desired);    //TODO - Implement.
    }

    private boolean deletePort(Port portAdapter)
    {
        Port removedPort = null;
        synchronized (_portAdapters)
        {
            removedPort = _portAdapters.remove(portAdapter.getPort());
        }
        return removedPort != null;
    }

    private boolean deleteAuthenticationProvider(AuthenticationProvider authenticationProvider)
    {
        AuthenticationProvider removedAuthenticationProvider = null;
        synchronized (_authenticationProviders)
        {
            removedAuthenticationProvider = _authenticationProviders.remove(authenticationProvider.getName());
        }
        return removedAuthenticationProvider != null;
    }

    public void addVirtualHost(VirtualHost virtualHost)
    {
        synchronized (_vhostAdapters)
        {
            String name = virtualHost.getName();
            if (_vhostAdapters.containsKey(name))
            {
                throw new IllegalConfigurationException("Virtual host with name " + name + " is already specified!");
            }
            _vhostAdapters.put(name, virtualHost);
        }
        virtualHost.addChangeListener(this);
    }

    @Override
    public boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            changeState(_groupProviders, currentState, State.ACTIVE, false);
            changeState(_authenticationProviders, currentState, State.ACTIVE, false);

            CurrentActor.set(new BrokerActor(_applicationRegistry.getRootMessageLogger()));
            try
            {
                changeState(_vhostAdapters, currentState, State.ACTIVE, false);
            }
            finally
            {
                CurrentActor.remove();
            }

            changeState(_portAdapters, currentState,State.ACTIVE, false);
            changeState(_plugins, currentState,State.ACTIVE, false);
            return true;
        }
        else if (desiredState == State.STOPPED)
        {
            changeState(_plugins, currentState,State.STOPPED, true);
            changeState(_portAdapters, currentState, State.STOPPED, true);
            changeState(_vhostAdapters,currentState, State.STOPPED, true);
            changeState(_authenticationProviders, currentState, State.STOPPED, true);
            changeState(_groupProviders, currentState, State.STOPPED, true);
            return true;
        }
        return false;
    }

    private void changeState(Map<?, ? extends ConfiguredObject> configuredObjectMap, State currentState, State desiredState, boolean swallowException)
    {
        synchronized(configuredObjectMap)
        {
            Collection<? extends ConfiguredObject> adapters = configuredObjectMap.values();
            for (ConfiguredObject configuredObject : adapters)
            {
                try
                {
                    configuredObject.setDesiredState(currentState, desiredState);
                }
                catch(RuntimeException e)
                {
                    if (swallowException)
                    {
                        LOGGER.error("Failed to stop " + configuredObject, e);
                    }
                    else
                    {
                        throw e;
                    }
                }
            }
        }
    }

    @Override
    public void stateChanged(ConfiguredObject object, State oldState, State newState)
    {
        if(newState == State.DELETED)
        {
            boolean childDeleted = false;
            if(object instanceof AuthenticationProvider)
            {
                childDeleted = deleteAuthenticationProvider((AuthenticationProvider)object);
            }
            else if(object instanceof Port)
            {
                childDeleted = deletePort((Port)object);
            }
            else if(object instanceof VirtualHost)
            {
                childDeleted = deleteVirtualHost((VirtualHost)object);
            }
            else if(object instanceof GroupProvider)
            {
                childDeleted = deleteGroupProvider((GroupProvider)object);
            }
            if(childDeleted)
            {
                childRemoved(object);
            }
        }
    }

    @Override
    public void childAdded(ConfiguredObject object, ConfiguredObject child)
    {
        // no-op
    }

    @Override
    public void childRemoved(ConfiguredObject object, ConfiguredObject child)
    {
        // no-op
    }

    public void addPlugin(ConfiguredObject plugin)
    {
        synchronized(_plugins)
        {
            if (_plugins.containsKey(plugin.getId()))
            {
                throw new IllegalConfigurationException("Plugin with id '" + plugin.getId() + "' is already registered!");
            }
            _plugins.put(plugin.getId(), plugin);
        }
        plugin.addChangeListener(this);
    }


    private Collection<ConfiguredObject> getPlugins()
    {
        synchronized(_plugins)
        {
            return Collections.unmodifiableCollection(_plugins.values());
        }
    }

    public void recoverChild(ConfiguredObject object)
    {
        if(object instanceof AuthenticationProvider)
        {
            addAuthenticationProvider((AuthenticationProvider)object);
        }
        else if(object instanceof Port)
        {
            addPort((Port)object);
        }
        else if(object instanceof VirtualHost)
        {
            addVirtualHost((VirtualHost)object);
        }
        else if(object instanceof GroupProvider)
        {
            addGroupProvider((GroupProvider)object);
        }
        else
        {
            addPlugin(object);
        }
    }

}
