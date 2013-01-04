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
import static org.apache.qpid.server.util.MapValueConverter.getStringAttribute;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
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
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfigurationChangeListener;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerAdapter extends AbstractAdapter implements Broker, ConfigurationChangeListener
{
    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final RootMessageLogger _rootMessageLogger;
    private String _name;
    private StatisticsAdapter _statistics;

    private final Map<String, VirtualHost> _vhostAdapters = new HashMap<String, VirtualHost>();
    private final Map<Integer, Port> _portAdapters = new HashMap<Integer, Port>();
    private final Map<String, AuthenticationProvider> _authenticationProviders = new HashMap<String, AuthenticationProvider>();
    private final Map<String, GroupProvider> _groupProviders = new HashMap<String, GroupProvider>();
    private final Map<UUID, ConfiguredObject> _plugins = new HashMap<UUID, ConfiguredObject>();
    private final Map<UUID, KeyStore> _keyStores = new HashMap<UUID, KeyStore>();
    private final Map<UUID, TrustStore> _trustStores = new HashMap<UUID, TrustStore>();

    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private AuthenticationProvider _defaultAuthenticationProvider;

    private final PortFactory _portFactory;
    private final SecurityManager _securityManager;

    //TODO: delete these fields, instead, add the attributes into attribute map
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
    private String _defaultVirtualHost;
    private String _aclFile;
    private int _sessionCountLimit;
    private int _heartBeatDelay;
    private int _statisticsReportingPeriod;
    private boolean _statisticsReportingResetEnabled;

    public BrokerAdapter(UUID id, Map<String, Object> attributes, StatisticsGatherer statisticsGatherer, VirtualHostRegistry virtualHostRegistry,
            LogRecorder logRecorder, RootMessageLogger rootMessageLogger, AuthenticationProviderFactory authenticationProviderFactory,
            PortFactory portFactory)
    {
        super(id);
        _name = "Broker";
        _statisticsGatherer = statisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _rootMessageLogger = rootMessageLogger;
        _name = "Broker";
        _statistics = new StatisticsAdapter(statisticsGatherer);
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
        _defaultVirtualHost = getStringAttribute(DEFAULT_VIRTUAL_HOST, attributes, null);
        _aclFile = getStringAttribute(ACL_FILE, attributes, null);
        _securityManager = new SecurityManager(_aclFile);

        _sessionCountLimit = getIntegerAttribute(SESSION_COUNT_LIMIT, attributes, 256);
        _heartBeatDelay = getIntegerAttribute(HEART_BEAT_DELAY, attributes, BrokerProperties.DEFAULT_HEART_BEAT_DELAY);
        _statisticsReportingPeriod =  getIntegerAttribute(STATISTICS_REPORTING_PERIOD, attributes, BrokerProperties.DEFAULT_STATISTICS_REPORTING_PERIOD);
        _statisticsReportingResetEnabled = getBooleanAttribute(STATISTICS_REPORTING_RESET_ENABLED, attributes, false);
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
        final VirtualHostAdapter virtualHostAdapter = new VirtualHostAdapter(UUID.randomUUID(), attributes, this,
                _statisticsGatherer);

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
        else if(clazz == KeyStore.class)
        {
            return (Collection<C>) getKeyStores();
        }
        else if(clazz == TrustStore.class)
        {
            return (Collection<C>) getTrustStores();
        }
        else if(clazz == Plugin.class)
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

    public void addKeyStore(KeyStore keyStore)
    {
        synchronized (_keyStores)
        {
            if(_keyStores.containsKey(keyStore.getId()))
            {
                throw new IllegalConfigurationException("Cannot add KeyStore because one with id " + keyStore.getId() + " already exists");
            }
            _keyStores.put(keyStore.getId(), keyStore);
        }
        keyStore.addChangeListener(this);
    }

    private boolean deleteKeyStore(KeyStore object)
    {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    public void addTrustStore(TrustStore trustStore)
    {
        synchronized (_trustStores)
        {
            if(_trustStores.containsKey(trustStore.getId()))
            {
                throw new IllegalConfigurationException("Cannot add TrustStore because one with id " + trustStore.getId() + " already exists");
            }
            _trustStores.put(trustStore.getId(), trustStore);
        }
        trustStore.addChangeListener(this);
    }

    private boolean deleteTrustStore(TrustStore object)
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
            return _defaultAuthenticationProvider == null ? null : _defaultAuthenticationProvider.getName();
        }
        else if (DEFAULT_VIRTUAL_HOST.equals(name))
        {
            return _defaultVirtualHost;
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
        else if (ACL_FILE.equals(name))
        {
            return _aclFile;
        }
        else if (SESSION_COUNT_LIMIT.equals(name))
        {
            return _sessionCountLimit;
        }
        else if (HEART_BEAT_DELAY.equals(name))
        {
            return _heartBeatDelay;
        }
        else if (STATISTICS_REPORTING_PERIOD.equals(name))
        {
            return _statisticsReportingPeriod;
        }
        else if (STATISTICS_REPORTING_RESET_ENABLED.equals(name))
        {
            return _statisticsReportingResetEnabled;
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

            CurrentActor.set(new BrokerActor(getRootMessageLogger()));
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
            else if(object instanceof KeyStore)
            {
                childDeleted = deleteKeyStore((KeyStore)object);
            }
            else if(object instanceof TrustStore)
            {
                childDeleted = deleteTrustStore((TrustStore)object);
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
        else if(object instanceof KeyStore)
        {
            addKeyStore((KeyStore)object);
        }
        else if(object instanceof TrustStore)
        {
            addTrustStore((TrustStore)object);
        }
        else if(object instanceof Plugin)
        {
            addPlugin(object);
        }
        else
        {
            throw new IllegalArgumentException("Attempted to recover unexpected type of configured object: " + object.getClass().getName());
        }
    }

    @Override
    public RootMessageLogger getRootMessageLogger()
    {
        return _rootMessageLogger;
    }

    @Override
    public SecurityManager getSecurityManager()
    {
        return _securityManager;
    }

    @Override
    public LogRecorder getLogRecorder()
    {
        return _logRecorder;
    }

    @Override
    public VirtualHost findVirtualHostByName(String name)
    {
        return _vhostAdapters.get(name);
    }

    @Override
    public SubjectCreator getSubjectCreator(SocketAddress localAddress)
    {
        InetSocketAddress inetSocketAddress = (InetSocketAddress)localAddress;
        AuthenticationProvider provider = _defaultAuthenticationProvider;
        Collection<Port> ports = getPorts();
        for (Port p : ports)
        {
            if (inetSocketAddress.getPort() == p.getPort())
            {
                provider = p.getAuthenticationProvider();
                break;
            }
        }
        return provider.getSubjectCreator();
    }

    @Override
    public Collection<KeyStore> getKeyStores()
    {
        synchronized(_trustStores)
        {
            return Collections.unmodifiableCollection(_keyStores.values());
        }
    }

    @Override
    public Collection<TrustStore> getTrustStores()
    {
        synchronized(_trustStores)
        {
            return Collections.unmodifiableCollection(_trustStores.values());
        }
    }

    @Override
    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

}
