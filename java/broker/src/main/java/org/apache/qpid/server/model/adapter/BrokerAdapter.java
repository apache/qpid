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
import java.net.SocketAddress;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;

import org.apache.log4j.Logger;
import org.apache.qpid.common.QpidProperties;
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
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.group.FileGroupManager;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerAdapter extends AbstractAdapter implements Broker, ConfigurationChangeListener
{

    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    @SuppressWarnings("serial")
    public static final Map<String, Class<?>> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Class<?>>(){{
        put(ALERT_THRESHOLD_MESSAGE_AGE, Long.class);
        put(ALERT_THRESHOLD_MESSAGE_COUNT, Long.class);
        put(ALERT_THRESHOLD_QUEUE_DEPTH, Long.class);
        put(ALERT_THRESHOLD_MESSAGE_SIZE, Long.class);
        put(ALERT_REPEAT_GAP, Long.class);
        put(FLOW_CONTROL_SIZE_BYTES, Long.class);
        put(FLOW_CONTROL_RESUME_SIZE_BYTES, Long.class);
        put(HOUSEKEEPING_CHECK_PERIOD, Long.class);

        put(DEAD_LETTER_QUEUE_ENABLED, Boolean.class);
        put(STATISTICS_REPORTING_RESET_ENABLED, Boolean.class);

        put(MAXIMUM_DELIVERY_ATTEMPTS, Integer.class);
        put(SESSION_COUNT_LIMIT, Integer.class);
        put(HEART_BEAT_DELAY, Integer.class);
        put(STATISTICS_REPORTING_PERIOD, Integer.class);

        put(ACL_FILE, String.class);
        put(NAME, String.class);
        put(DEFAULT_VIRTUAL_HOST, String.class);
        put(DEFAULT_AUTHENTICATION_PROVIDER, String.class);

        put(KEY_STORE_PATH, String.class);
        put(KEY_STORE_PASSWORD, String.class);
        put(KEY_STORE_CERT_ALIAS, String.class);
        put(TRUST_STORE_PATH, String.class);
        put(TRUST_STORE_PASSWORD, String.class);
        put(GROUP_FILE, String.class);
    }});

    public static final int DEFAULT_STATISTICS_REPORTING_PERIOD = 0;
    public static final boolean DEFAULT_STATISTICS_REPORTING_RESET_ENABLED = false;
    public static final long DEFAULT_ALERT_REPEAT_GAP = 30000l;
    public static final long DEFAULT_ALERT_THRESHOLD_MESSAGE_AGE = 0l;
    public static final long DEFAULT_ALERT_THRESHOLD_MESSAGE_COUNT = 0l;
    public static final long DEFAULT_ALERT_THRESHOLD_MESSAGE_SIZE = 0l;
    public static final long DEFAULT_ALERT_THRESHOLD_QUEUE_DEPTH = 0l;
    public static final boolean DEFAULT_DEAD_LETTER_QUEUE_ENABLED = false;
    public static final int DEFAULT_MAXIMUM_DELIVERY_ATTEMPTS = 0;
    public static final long DEFAULT_FLOW_CONTROL_RESUME_SIZE_BYTES = 0l;
    public static final long DEFAULT_FLOW_CONTROL_SIZE_BYTES = 0l;
    public static final long DEFAULT_HOUSEKEEPING_CHECK_PERIOD = 30000l;
    public static final int DEFAULT_HEART_BEAT_DELAY = 0;
    public static final int DEFAULT_SESSION_COUNT_LIMIT = 256;
    public static final String DEFAULT_NAME = "QpidBroker";
    private static final String DEFAULT_KEY_STORE_NAME = "defaultKeyStore";
    private static final String DEFAULT_TRUST_STORE_NAME = "defaultTrustStore";
    private static final String DEFAULT_GROUP_PROFIDER_NAME = "defaultGroupProvider";

    private static final String DUMMY_PASSWORD_MASK = "********";

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(Broker.STATISTICS_REPORTING_PERIOD, DEFAULT_STATISTICS_REPORTING_PERIOD);
        put(Broker.STATISTICS_REPORTING_RESET_ENABLED, DEFAULT_STATISTICS_REPORTING_RESET_ENABLED);
        put(Broker.ALERT_REPEAT_GAP, DEFAULT_ALERT_REPEAT_GAP);
        put(Broker.ALERT_THRESHOLD_MESSAGE_AGE, DEFAULT_ALERT_THRESHOLD_MESSAGE_AGE);
        put(Broker.ALERT_THRESHOLD_MESSAGE_COUNT, DEFAULT_ALERT_THRESHOLD_MESSAGE_COUNT);
        put(Broker.ALERT_THRESHOLD_MESSAGE_SIZE, DEFAULT_ALERT_THRESHOLD_MESSAGE_SIZE);
        put(Broker.ALERT_THRESHOLD_QUEUE_DEPTH, DEFAULT_ALERT_THRESHOLD_QUEUE_DEPTH);
        put(Broker.DEAD_LETTER_QUEUE_ENABLED, DEFAULT_DEAD_LETTER_QUEUE_ENABLED);
        put(Broker.MAXIMUM_DELIVERY_ATTEMPTS, DEFAULT_MAXIMUM_DELIVERY_ATTEMPTS);
        put(Broker.FLOW_CONTROL_RESUME_SIZE_BYTES, DEFAULT_FLOW_CONTROL_RESUME_SIZE_BYTES);
        put(Broker.FLOW_CONTROL_SIZE_BYTES, DEFAULT_FLOW_CONTROL_SIZE_BYTES);
        put(Broker.HOUSEKEEPING_CHECK_PERIOD, DEFAULT_HOUSEKEEPING_CHECK_PERIOD);
        put(Broker.HEART_BEAT_DELAY, DEFAULT_HEART_BEAT_DELAY);
        put(Broker.SESSION_COUNT_LIMIT, DEFAULT_SESSION_COUNT_LIMIT);
        put(Broker.NAME, DEFAULT_NAME);
    }});




    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final RootMessageLogger _rootMessageLogger;
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
    private final UUID _defaultKeyStoreId;
    private final UUID _defaultTrustStoreId;

    public BrokerAdapter(UUID id, Map<String, Object> attributes, StatisticsGatherer statisticsGatherer, VirtualHostRegistry virtualHostRegistry,
            LogRecorder logRecorder, RootMessageLogger rootMessageLogger, AuthenticationProviderFactory authenticationProviderFactory,
            PortFactory portFactory, TaskExecutor taskExecutor)
    {
        super(id, DEFAULTS,  MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        _statisticsGatherer = statisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _rootMessageLogger = rootMessageLogger;
        _statistics = new StatisticsAdapter(statisticsGatherer);
        _authenticationProviderFactory = authenticationProviderFactory;
        _portFactory = portFactory;
        _securityManager = new SecurityManager((String)getAttribute(ACL_FILE));

        _defaultKeyStoreId = UUIDGenerator.generateBrokerChildUUID(KeyStore.class.getSimpleName(), DEFAULT_KEY_STORE_NAME);
        _defaultTrustStoreId = UUIDGenerator.generateBrokerChildUUID(TrustStore.class.getSimpleName(), DEFAULT_TRUST_STORE_NAME);
        createBrokerChildrenFromAttributes();
    }

    /*
     * A temporary method to create broker children that can be only configured via broker attributes
     */
    private void createBrokerChildrenFromAttributes()
    {
        String groupFile = (String) getAttribute(GROUP_FILE);
        if (groupFile != null)
        {
            GroupManager groupManager = new FileGroupManager(groupFile);
            UUID groupProviderId = UUIDGenerator.generateBrokerChildUUID(GroupProvider.class.getSimpleName(),
                    DEFAULT_GROUP_PROFIDER_NAME);
            GroupProviderAdapter groupProviderAdapter = new GroupProviderAdapter(groupProviderId, groupManager, this);
            addGroupProvider(groupProviderAdapter);
        }
        Map<String, Object> actualAttributes = getActualAttributes();
        String keyStorePath = (String) getAttribute(KEY_STORE_PATH);
        if (keyStorePath != null)
        {
            Map<String, Object> keyStoreAttributes = new HashMap<String, Object>();
            keyStoreAttributes.put(KeyStore.NAME, DEFAULT_KEY_STORE_NAME);
            keyStoreAttributes.put(KeyStore.PATH, keyStorePath);
            keyStoreAttributes.put(KeyStore.PASSWORD, (String) actualAttributes.get(KEY_STORE_PASSWORD));
            keyStoreAttributes.put(KeyStore.TYPE, java.security.KeyStore.getDefaultType());
            keyStoreAttributes.put(KeyStore.CERTIFICATE_ALIAS, getAttribute(KEY_STORE_CERT_ALIAS));
            keyStoreAttributes.put(KeyStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
            KeyStoreAdapter KeyStoreAdapter = new KeyStoreAdapter(_defaultKeyStoreId, this, keyStoreAttributes);
            addKeyStore(KeyStoreAdapter);
        }
        String trustStorePath = (String) getAttribute(TRUST_STORE_PATH);
        if (trustStorePath != null)
        {
            Map<String, Object> trsustStoreAttributes = new HashMap<String, Object>();
            trsustStoreAttributes.put(TrustStore.NAME, DEFAULT_TRUST_STORE_NAME);
            trsustStoreAttributes.put(TrustStore.PATH, trustStorePath);
            trsustStoreAttributes.put(TrustStore.PASSWORD, (String) actualAttributes.get(TRUST_STORE_PASSWORD));
            trsustStoreAttributes.put(TrustStore.TYPE, java.security.KeyStore.getDefaultType());
            trsustStoreAttributes.put(TrustStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
            TrustStoreAdapter trustStore = new TrustStoreAdapter(_defaultTrustStoreId, this, trsustStoreAttributes);
            addTrustStore(trustStore);
        }
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
                _statisticsGatherer, getTaskExecutor());
        addVirtualHost(virtualHostAdapter);
        virtualHostAdapter.setDesiredState(State.INITIALISING, State.ACTIVE);
        return virtualHostAdapter;
    }

    private boolean deleteVirtualHost(final VirtualHost vhost) throws AccessControlException, IllegalStateException
    {
        synchronized (_vhostAdapters)
        {
            _vhostAdapters.remove(vhost);
        }
        vhost.removeChangeListener(this);
        return true;
    }

    public String getName()
    {
        return (String)getAttribute(NAME);
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

    //TODO: ACL
    @SuppressWarnings("unchecked")
    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
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

    private void addPort(Port port)
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
        return port;
    }

    private AuthenticationProvider createAuthenticationProvider(Map<String, Object> attributes)
    {
        // it's cheap to create the groupPrincipalAccessor on the fly
        GroupPrincipalAccessor groupPrincipalAccessor = new GroupPrincipalAccessor(_groupProviders.values());

        AuthenticationProvider authenticationProvider = _authenticationProviderFactory.create(UUID.randomUUID(), this, attributes, groupPrincipalAccessor);
        addAuthenticationProvider(authenticationProvider);
        return authenticationProvider;
    }

    /**
     * @throws IllegalConfigurationException if an AuthenticationProvider with the same name already exists
     */
    private void addAuthenticationProvider(AuthenticationProvider authenticationProvider)
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

    private void addGroupProvider(GroupProvider groupProvider)
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

    private void addKeyStore(KeyStore keyStore)
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

    private void addTrustStore(TrustStore trustStore)
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
        else if (KEY_STORE_PASSWORD.equals(name))
        {
            return DUMMY_PASSWORD_MASK;
        }
        else if (TRUST_STORE_PASSWORD.equals(name))
        {
            return DUMMY_PASSWORD_MASK;
        }
        return super.getAttribute(name);
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

    private void addVirtualHost(VirtualHost virtualHost)
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
                if (State.ACTIVE.equals(desiredState) && State.QUIESCED.equals(configuredObject.getActualState()))
                {
                    if (LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug(configuredObject + " cannot be activated as it is " +State.QUIESCED);
                    }
                    continue;
                }
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

    @Override
    public void attributeSet(ConfiguredObject object, String attributeName, Object oldAttributeValue, Object newAttributeValue)
    {
        // no-op
    }

    private void addPlugin(ConfiguredObject plugin)
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

    @Override
    public KeyStore getDefaultKeyStore()
    {
        return _keyStores.get(_defaultKeyStoreId);
    }

    @Override
    public TrustStore getDefaultTrustStore()
    {
        return _trustStores.get(_defaultTrustStoreId);
    }

    @Override
    public TaskExecutor getTaskExecutor()
    {
        return super.getTaskExecutor();
    }
}
