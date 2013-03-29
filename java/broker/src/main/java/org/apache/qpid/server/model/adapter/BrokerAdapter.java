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
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.AccessControlException;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.KeyManagerFactory;
import java.security.cert.Certificate;

import org.apache.log4j.Logger;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
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
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.auth.manager.Base64MD5PasswordFileAuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.PlainPasswordFileAuthenticationManagerFactory;
import org.apache.qpid.server.security.group.FileGroupManager;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.MessageStoreCreator;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.transport.network.security.ssl.SSLUtil;

public class BrokerAdapter extends AbstractAdapter implements Broker, ConfigurationChangeListener
{

    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, Long.class);
        put(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, Long.class);
        put(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, Long.class);
        put(QUEUE_ALERT_REPEAT_GAP, Long.class);
        put(QUEUE_FLOW_CONTROL_SIZE_BYTES, Long.class);
        put(QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES, Long.class);
        put(VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD, Long.class);

        put(QUEUE_DEAD_LETTER_QUEUE_ENABLED, Boolean.class);
        put(STATISTICS_REPORTING_RESET_ENABLED, Boolean.class);

        put(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, Integer.class);
        put(CONNECTION_SESSION_COUNT_LIMIT, Integer.class);
        put(CONNECTION_HEART_BEAT_DELAY, Integer.class);
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
        put(PEER_STORE_PATH, String.class);
        put(PEER_STORE_PASSWORD, String.class);
        put(GROUP_FILE, String.class);
        put(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN, Long.class);
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
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE = 0l;
    public static final long DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_WARN = 0l;
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE = 0l;
    public static final long DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_WARN = 0l;
    private static final String DEFAULT_KEY_STORE_NAME = "defaultKeyStore";
    private static final String DEFAULT_TRUST_STORE_NAME = "defaultTrustStore";
    private static final String DEFAULT_GROUP_PROFIDER_NAME = "defaultGroupProvider";
    private static final String DEFAULT_PEER_STORE_NAME = "defaultPeerStore";

    private static final String DUMMY_PASSWORD_MASK = "********";

    @SuppressWarnings("serial")
    private static final Map<String, Object> DEFAULTS = Collections.unmodifiableMap(new HashMap<String, Object>(){{
        put(Broker.STATISTICS_REPORTING_PERIOD, DEFAULT_STATISTICS_REPORTING_PERIOD);
        put(Broker.STATISTICS_REPORTING_RESET_ENABLED, DEFAULT_STATISTICS_REPORTING_RESET_ENABLED);
        put(Broker.QUEUE_ALERT_REPEAT_GAP, DEFAULT_ALERT_REPEAT_GAP);
        put(Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, DEFAULT_ALERT_THRESHOLD_MESSAGE_AGE);
        put(Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, DEFAULT_ALERT_THRESHOLD_MESSAGE_COUNT);
        put(Broker.QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, DEFAULT_ALERT_THRESHOLD_MESSAGE_SIZE);
        put(Broker.QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, DEFAULT_ALERT_THRESHOLD_QUEUE_DEPTH);
        put(Broker.QUEUE_DEAD_LETTER_QUEUE_ENABLED, DEFAULT_DEAD_LETTER_QUEUE_ENABLED);
        put(Broker.QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, DEFAULT_MAXIMUM_DELIVERY_ATTEMPTS);
        put(Broker.QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES, DEFAULT_FLOW_CONTROL_RESUME_SIZE_BYTES);
        put(Broker.QUEUE_FLOW_CONTROL_SIZE_BYTES, DEFAULT_FLOW_CONTROL_SIZE_BYTES);
        put(Broker.VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD, DEFAULT_HOUSEKEEPING_CHECK_PERIOD);
        put(Broker.CONNECTION_HEART_BEAT_DELAY, DEFAULT_HEART_BEAT_DELAY);
        put(Broker.CONNECTION_SESSION_COUNT_LIMIT, DEFAULT_SESSION_COUNT_LIMIT);
        put(Broker.NAME, DEFAULT_NAME);
        put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
        put(Broker.VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, DEFAULT_STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
        put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
        put(Broker.VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN, DEFAULT_STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
    }});

    private String[] POSITIVE_NUMERIC_ATTRIBUTES = { QUEUE_ALERT_THRESHOLD_MESSAGE_AGE, QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES,
            QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE, QUEUE_ALERT_REPEAT_GAP, QUEUE_FLOW_CONTROL_SIZE_BYTES,
            QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES, QUEUE_MAXIMUM_DELIVERY_ATTEMPTS, VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD, CONNECTION_SESSION_COUNT_LIMIT,
            CONNECTION_HEART_BEAT_DELAY, STATISTICS_REPORTING_PERIOD, VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE,
            VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE,
            VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN};


    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;
    private final RootMessageLogger _rootMessageLogger;
    private StatisticsAdapter _statistics;

    private final Map<String, VirtualHost> _vhostAdapters = new HashMap<String, VirtualHost>();
    private final Map<Integer, Port> _portAdapters = new HashMap<Integer, Port>();
    private final Map<UUID, AuthenticationProvider> _authenticationProviders = new HashMap<UUID, AuthenticationProvider>();
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

    private final Collection<String> _supportedStoreTypes;
    private final ConfigurationEntryStore _brokerStore;

    public BrokerAdapter(UUID id, Map<String, Object> attributes, StatisticsGatherer statisticsGatherer, VirtualHostRegistry virtualHostRegistry,
            LogRecorder logRecorder, RootMessageLogger rootMessageLogger, AuthenticationProviderFactory authenticationProviderFactory,
            PortFactory portFactory, TaskExecutor taskExecutor, ConfigurationEntryStore brokerStore)
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
        addChangeListener(_securityManager);
        _defaultKeyStoreId = UUIDGenerator.generateBrokerChildUUID(KeyStore.class.getSimpleName(), DEFAULT_KEY_STORE_NAME);
        _defaultTrustStoreId = UUIDGenerator.generateBrokerChildUUID(TrustStore.class.getSimpleName(), DEFAULT_TRUST_STORE_NAME);
        createBrokerChildrenFromAttributes();
        _supportedStoreTypes = new MessageStoreCreator().getStoreTypes();
        _brokerStore = brokerStore;
    }

    /*
     * A temporary method to create broker children that can be only configured via broker attributes
     */
    private void createBrokerChildrenFromAttributes()
    {
        createGroupProvider();
        createKeyStore();
        createTrustStore();
        createPeerStore();
    }

    private void createGroupProvider()
    {
        String groupFile = (String) getAttribute(GROUP_FILE);
        if (groupFile != null)
        {
            GroupManager groupManager = new FileGroupManager(groupFile);
            UUID groupProviderId = UUIDGenerator.generateBrokerChildUUID(GroupProvider.class.getSimpleName(),
                    DEFAULT_GROUP_PROFIDER_NAME);
            GroupProviderAdapter groupProviderAdapter = new GroupProviderAdapter(groupProviderId, groupManager, this);
            _groupProviders.put(DEFAULT_GROUP_PROFIDER_NAME, groupProviderAdapter);
        }
        else
        {
            _groupProviders.remove(DEFAULT_GROUP_PROFIDER_NAME);
        }
    }

    private void createKeyStore()
    {
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
            KeyStoreAdapter keyStoreAdapter = new KeyStoreAdapter(_defaultKeyStoreId, this, keyStoreAttributes);
            _keyStores.put(keyStoreAdapter.getId(), keyStoreAdapter);
        }
        else
        {
            _keyStores.remove(_defaultKeyStoreId);
        }
    }

    private void createTrustStore()
    {
        Map<String, Object> actualAttributes = getActualAttributes();
        String trustStorePath = (String) getAttribute(TRUST_STORE_PATH);
        if (trustStorePath != null)
        {
            Map<String, Object> trsustStoreAttributes = new HashMap<String, Object>();
            trsustStoreAttributes.put(TrustStore.NAME, DEFAULT_TRUST_STORE_NAME);
            trsustStoreAttributes.put(TrustStore.PATH, trustStorePath);
            trsustStoreAttributes.put(TrustStore.PEERS_ONLY, Boolean.FALSE);
            trsustStoreAttributes.put(TrustStore.PASSWORD, (String) actualAttributes.get(TRUST_STORE_PASSWORD));
            trsustStoreAttributes.put(TrustStore.TYPE, java.security.KeyStore.getDefaultType());
            trsustStoreAttributes.put(TrustStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
            TrustStoreAdapter trustStore = new TrustStoreAdapter(_defaultTrustStoreId, this, trsustStoreAttributes);
            _trustStores.put(trustStore.getId(), trustStore);
        }
        else
        {
            _trustStores.remove(_defaultTrustStoreId);
        }
    }

    private void createPeerStore()
    {
        Map<String, Object> actualAttributes = getActualAttributes();
        String peerStorePath = (String) getAttribute(PEER_STORE_PATH);
        UUID peerStoreId = UUIDGenerator.generateBrokerChildUUID(TrustStore.class.getSimpleName(), DEFAULT_PEER_STORE_NAME);
        if (peerStorePath != null)
        {
            Map<String, Object> peerStoreAttributes = new HashMap<String, Object>();
            peerStoreAttributes.put(TrustStore.NAME, peerStoreId.toString());
            peerStoreAttributes.put(TrustStore.PATH, peerStorePath);
            peerStoreAttributes.put(TrustStore.PEERS_ONLY, Boolean.TRUE);
            peerStoreAttributes.put(TrustStore.PASSWORD, (String) actualAttributes.get(PEER_STORE_PASSWORD));
            peerStoreAttributes.put(TrustStore.TYPE, java.security.KeyStore.getDefaultType());
            peerStoreAttributes.put(TrustStore.KEY_MANAGER_FACTORY_ALGORITHM, KeyManagerFactory.getDefaultAlgorithm());
            TrustStoreAdapter trustStore = new TrustStoreAdapter(peerStoreId, this, peerStoreAttributes);
            _trustStores.put(trustStore.getId(), trustStore);
        }
        else
        {
            _trustStores.remove(peerStoreId);
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

    public AuthenticationProvider findAuthenticationProviderByName(String authenticationProviderName)
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
            _vhostAdapters.remove(vhost.getName());
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
        port.setDesiredState(State.INITIALISING, State.ACTIVE);
        return port;
    }

    private AuthenticationProvider createAuthenticationProvider(Map<String, Object> attributes)
    {
        AuthenticationProvider authenticationProvider = null;
        synchronized (_authenticationProviders)
        {
            authenticationProvider = _authenticationProviderFactory.create(UUID.randomUUID(), this, attributes);
            addAuthenticationProvider(authenticationProvider);
        }
        authenticationProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
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
            if (_authenticationProviders.containsKey(authenticationProvider.getId()))
            {
                throw new IllegalConfigurationException("Cannot add AuthenticationProvider because one with id " + authenticationProvider.getId() + " already exists");
            }
            for (AuthenticationProvider provider : _authenticationProviders.values())
            {
                if (provider.getName().equals(name))
                {
                    throw new IllegalConfigurationException("Cannot add AuthenticationProvider because one with name " + name + " already exists");
                }
            }
            _authenticationProviders.put(authenticationProvider.getId(), authenticationProvider);
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
            return _supportedStoreTypes;
        }
        else if(SUPPORTED_AUTHENTICATION_PROVIDERS.equals(name))
        {
            return _authenticationProviderFactory.getSupportedAuthenticationProviders();
        }
        else if (KEY_STORE_PASSWORD.equals(name) || TRUST_STORE_PASSWORD.equals(name) || PEER_STORE_PASSWORD.equals(name))
        {
            if (getActualAttributes().get(name) != null)
            {
                return DUMMY_PASSWORD_MASK;
            }
            return null;
        }
        else if (MANAGEMENT_VERSION.equals(name))
        {
            return Model.MANAGEMENT_API_MAJOR_VERSION + "." + Model.MANAGEMENT_API_MINOR_VERSION;
        }
        else if (STORE_VERSION.equals(name))
        {
            return _brokerStore.getVersion();
        }
        else if (STORE_TYPE.equals(name))
        {
            return _brokerStore.getType();
        }
        else if (STORE_PATH.equals(name))
        {
            return _brokerStore.getStoreLocation();
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
            removedAuthenticationProvider = _authenticationProviders.remove(authenticationProvider.getId());
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
        synchronized(_keyStores)
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

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        //TODO: Add ACL check
        //TODO: Add management mode check
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateAttributes(convertedAttributes);

        boolean keyStoreChanged = false;
        boolean trustStoreChanged = false;
        boolean peerStoreChanged = false;
        Collection<String> names = AVAILABLE_ATTRIBUTES;
        for (String name : names)
        {
            if (convertedAttributes.containsKey(name))
            {
                Object desired = convertedAttributes.get(name);
                Object expected = getAttribute(name);
                if (changeAttribute(name, expected, desired))
                {
                    if (GROUP_FILE.equals(name))
                    {
                        createGroupProvider();
                    }
                    else if (DEFAULT_AUTHENTICATION_PROVIDER.equals(name))
                    {
                        if (!_defaultAuthenticationProvider.getName().equals(desired))
                        {
                            _defaultAuthenticationProvider = findAuthenticationProviderByName((String)desired);
                        }
                    }
                    else if (KEY_STORE_PATH.equals(name) || KEY_STORE_PASSWORD.equals(name) || KEY_STORE_CERT_ALIAS.equals(name))
                    {
                        keyStoreChanged = true;
                    }
                    else if (TRUST_STORE_PATH.equals(name) || TRUST_STORE_PASSWORD.equals(name))
                    {
                        trustStoreChanged = true;
                    }
                    else if (PEER_STORE_PATH.equals(name) || PEER_STORE_PASSWORD.equals(name))
                    {
                        peerStoreChanged = true;
                    }
                    attributeSet(name, expected, desired);
                }
            }
        }

        // the calls below are not thread safe but they should be fine in a management mode
        // as there will be no user connected
        // The new keystore/trustore/peerstore will be only used with new ports
        // At the moment we cannot restart ports with new keystore/trustore/peerstore

        if (keyStoreChanged)
        {
            createKeyStore();
        }
        if (trustStoreChanged)
        {
            createTrustStore();
        }
        if (peerStoreChanged)
        {
            createPeerStore();
        }
    }

    private void validateAttributes(Map<String, Object> convertedAttributes)
    {
        String aclFile = (String) convertedAttributes.get(ACL_FILE);
        if (aclFile != null)
        {
            // create a security manager to validate the ACL specified in file
            new SecurityManager(aclFile);
        }
        String groupFile = (String) convertedAttributes.get(GROUP_FILE);
        if (groupFile != null)
        {
            // create a group manager to validate the groups specified in file
            new FileGroupManager(groupFile);
        }
        validateKeyStoreAttributes(convertedAttributes, "key store", KEY_STORE_PATH, KEY_STORE_PASSWORD, KEY_STORE_CERT_ALIAS);
        validateKeyStoreAttributes(convertedAttributes, "trust store", TRUST_STORE_PATH, TRUST_STORE_PASSWORD, null);
        validateKeyStoreAttributes(convertedAttributes, "peer store", PEER_STORE_PATH, PEER_STORE_PASSWORD, null);
        String defaultAuthenticationProvider = (String) convertedAttributes.get(DEFAULT_AUTHENTICATION_PROVIDER);
        if (defaultAuthenticationProvider != null)
        {
            AuthenticationProvider provider = findAuthenticationProviderByName(defaultAuthenticationProvider);
            if (provider == null)
            {
                throw new IllegalConfigurationException("Authentication provider with name " + defaultAuthenticationProvider
                        + " canot be set as a default as it does not exist");
            }
        }
        String defaultVirtualHost = (String) convertedAttributes.get(DEFAULT_VIRTUAL_HOST);
        if (defaultVirtualHost != null)
        {
            VirtualHost foundHost = findVirtualHostByName(defaultVirtualHost);
            if (foundHost == null)
            {
                throw new IllegalConfigurationException("Virtual host with name " + defaultVirtualHost
                        + " cannot be set as a default as it does not exist");
            }
        }
        Long queueFlowControlSize = (Long) convertedAttributes.get(QUEUE_FLOW_CONTROL_SIZE_BYTES);
        if (queueFlowControlSize != null && queueFlowControlSize > 0)
        {
            Long queueFlowControlResumeSize = (Long) convertedAttributes.get(QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
            if (queueFlowControlResumeSize == null)
            {
                throw new IllegalConfigurationException("Flow control resume size attribute is not specified with flow control size attribute");
            }
            if (queueFlowControlResumeSize >= queueFlowControlSize)
            {
                throw new IllegalConfigurationException("Flow control resume size should be less then flow control size");
            }
        }
        for (String attributeName : POSITIVE_NUMERIC_ATTRIBUTES)
        {
            Number value = (Number) convertedAttributes.get(attributeName);
            if (value != null && value.longValue() < 0)
            {
                throw new IllegalConfigurationException("Only positive integer value can be specified for the attribute "
                        + attributeName);
            }
        }
    }

    private void validateKeyStoreAttributes(Map<String, Object> convertedAttributes, String type, String pathAttribute,
            String passwordAttribute, String aliasAttribute)
    {
        String keyStoreFile = (String) convertedAttributes.get(pathAttribute);
        if (keyStoreFile != null)
        {
            String password = (String) convertedAttributes.get(passwordAttribute);
            if (password == null)
            {
                password = (String) getActualAttributes().get(passwordAttribute);
            }
            java.security.KeyStore keyStore = null;
            try
            {
                keyStore = SSLUtil.getInitializedKeyStore(keyStoreFile, password, java.security.KeyStore.getDefaultType());
            }
            catch (Exception e)
            {
                throw new IllegalConfigurationException("Cannot instantiate " + type + " at " + keyStoreFile, e);
            }
            if (aliasAttribute != null)
            {
                String alias = (String) convertedAttributes.get(aliasAttribute);
                if (alias != null)
                {
                    Certificate cert = null;
                    try
                    {
                        cert = keyStore.getCertificate(alias);
                    }
                    catch (KeyStoreException e)
                    {
                        // key store should be initialized above
                        throw new RuntimeException("Key store has not been initialized", e);
                    }
                    if (cert == null)
                    {
                        throw new IllegalConfigurationException("Cannot find a certificate with alias " + alias + "in " + type
                                + " : " + keyStoreFile);
                    }
                }
            }
        }
    }
}
