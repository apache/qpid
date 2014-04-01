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
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerConfigurationStoreCreator;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.plugin.PreferencesProviderFactory;
import org.apache.qpid.server.plugin.VirtualHostFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleAuthenticationManager;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.util.SystemUtils;

import javax.security.auth.Subject;

public class BrokerAdapter<X extends Broker<X>> extends AbstractConfiguredObject<X> implements Broker<X>, ConfigurationChangeListener
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
        put(CONNECTION_CLOSE_WHEN_NO_ROUTE, Boolean.class);
        put(STATISTICS_REPORTING_PERIOD, Integer.class);

        put(NAME, String.class);
        put(DEFAULT_VIRTUAL_HOST, String.class);

        put(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE, Long.class);
        put(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN, Long.class);
        put(MODEL_VERSION, String.class);
        put(STORE_VERSION, String.class);
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
    public static final boolean DEFAULT_CONNECTION_CLOSE_WHEN_NO_ROUTE = true;

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
        put(Broker.CONNECTION_CLOSE_WHEN_NO_ROUTE, DEFAULT_CONNECTION_CLOSE_WHEN_NO_ROUTE);
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


    private EventLogger _eventLogger;
    private final StatisticsGatherer _statisticsGatherer;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;

    private final Map<String, VirtualHost<?>> _vhostAdapters = new HashMap<String, VirtualHost<?>>();
    private final Map<UUID, Port<?>> _portAdapters = new HashMap<UUID, Port<?>>();
    private final Map<Port, Integer> _stillInUsePortNumbers = new HashMap<Port, Integer>();
    private final Map<UUID, AuthenticationProvider<?>> _authenticationProviders = new HashMap<UUID, AuthenticationProvider<?>>();
    private final Map<String, GroupProvider<?>> _groupProviders = new HashMap<String, GroupProvider<?>>();
    private final Map<UUID, ConfiguredObject<?>> _plugins = new HashMap<UUID, ConfiguredObject<?>>();
    private final Map<String, KeyStore<?>> _keyStores = new HashMap<String, KeyStore<?>>();
    private final Map<String, TrustStore<?>> _trustStores = new HashMap<String, TrustStore<?>>();
    private final Map<UUID, AccessControlProvider<?>> _accessControlProviders = new HashMap<UUID, AccessControlProvider<?>>();

    private final GroupProviderFactory _groupProviderFactory;
    private final AuthenticationProviderFactory _authenticationProviderFactory;
    private final AccessControlProviderFactory _accessControlProviderFactory;
    private final PortFactory _portFactory;
    private final SecurityManager _securityManager;

    private final Collection<String> _supportedVirtualHostStoreTypes;
    private Collection<String> _supportedBrokerStoreTypes;
    private final ConfigurationEntryStore _brokerStore;

    private AuthenticationProvider<?> _managementAuthenticationProvider;
    private BrokerOptions _brokerOptions;

    public BrokerAdapter(UUID id,
                         Map<String, Object> attributes,
                         StatisticsGatherer statisticsGatherer,
                         VirtualHostRegistry virtualHostRegistry,
                         LogRecorder logRecorder,
                         AuthenticationProviderFactory authenticationProviderFactory,
                         GroupProviderFactory groupProviderFactory,
                         AccessControlProviderFactory accessControlProviderFactory,
                         PortFactory portFactory,
                         TaskExecutor taskExecutor,
                         ConfigurationEntryStore brokerStore,
                         BrokerOptions brokerOptions)
    {
        super(id, DEFAULTS,  MapValueConverter.convert(attributes, ATTRIBUTE_TYPES), taskExecutor);
        _statisticsGatherer = statisticsGatherer;
        _virtualHostRegistry = virtualHostRegistry;
        _logRecorder = logRecorder;
        _eventLogger = virtualHostRegistry.getEventLogger();
        _authenticationProviderFactory = authenticationProviderFactory;
        _groupProviderFactory = groupProviderFactory;
        _accessControlProviderFactory = accessControlProviderFactory;
        _portFactory = portFactory;
        _brokerOptions = brokerOptions;
        _securityManager = new SecurityManager(this, _brokerOptions.isManagementMode());
        _supportedVirtualHostStoreTypes = MessageStoreFactory.FACTORY_LOADER.getSupportedTypes();
        _supportedBrokerStoreTypes = new BrokerConfigurationStoreCreator().getStoreTypes();
        _brokerStore = brokerStore;
        if (_brokerOptions.isManagementMode())
        {
            Map<String,Object> authManagerAttrs = new HashMap<String, Object>();
            authManagerAttrs.put(NAME,"MANAGEMENT_MODE_AUTHENTICATION");
            authManagerAttrs.put(ID, UUID.randomUUID());
            SimpleAuthenticationManager authManager = new SimpleAuthenticationManager(this, Collections.<String,Object>emptyMap(), authManagerAttrs);
            authManager.addUser(BrokerOptions.MANAGEMENT_MODE_USER_NAME, _brokerOptions.getManagementModePassword());
            _managementAuthenticationProvider = authManager;
        }
    }

    @Override
    public String getBuildVersion()
    {
        return (String) getAttribute(BUILD_VERSION);
    }

    @Override
    public String getOperatingSystem()
    {
        return (String) getAttribute(OPERATING_SYSTEM);
    }

    @Override
    public String getPlatform()
    {
        return (String) getAttribute(PLATFORM);
    }

    @Override
    public String getProcessPid()
    {
        return (String) getAttribute(PROCESS_PID);
    }

    @Override
    public String getProductVersion()
    {
        return (String) getAttribute(PRODUCT_VERSION);
    }

    @Override
    public Collection<String> getSupportedBrokerStoreTypes()
    {
        return _supportedBrokerStoreTypes;
    }

    @Override
    public Collection<String> getSupportedVirtualHostStoreTypes()
    {
        return _supportedVirtualHostStoreTypes;
    }

    @Override
    public Collection<String> getSupportedAuthenticationProviders()
    {
        return _authenticationProviderFactory.getSupportedAuthenticationProviders();
    }

    @Override
    public Collection<String> getSupportedPreferencesProviderTypes()
    {
        return PreferencesProviderFactory.FACTORY_LOADER.getSupportedTypes();
    }

    @Override
    public String getDefaultVirtualHost()
    {
        return (String) getAttribute(DEFAULT_VIRTUAL_HOST);
    }

    @Override
    public int getQueue_alertThresholdMessageAge()
    {
        return (Integer) getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_AGE);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthMessages()
    {
        return (Long) getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES);
    }

    @Override
    public long getQueue_alertThresholdQueueDepthBytes()
    {
        return (Long) getAttribute(QUEUE_ALERT_THRESHOLD_QUEUE_DEPTH_BYTES);
    }

    @Override
    public long getQueue_alertThresholdMessageSize()
    {
        return (Long) getAttribute(QUEUE_ALERT_THRESHOLD_MESSAGE_SIZE);
    }

    @Override
    public long getQueue_alertRepeatGap()
    {
        return (Long) getAttribute(QUEUE_ALERT_REPEAT_GAP);
    }

    @Override
    public long getQueue_flowControlSizeBytes()
    {
        return (Long) getAttribute(QUEUE_FLOW_CONTROL_SIZE_BYTES);
    }

    @Override
    public long getQueue_flowResumeSizeBytes()
    {
        return (Long) getAttribute(QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
    }

    @Override
    public int getQueue_maximumDeliveryAttempts()
    {
        return (Integer) getAttribute(QUEUE_MAXIMUM_DELIVERY_ATTEMPTS);
    }

    @Override
    public boolean isQueue_deadLetterQueueEnabled()
    {
        return (Boolean) getAttribute(QUEUE_DEAD_LETTER_QUEUE_ENABLED);
    }

    @Override
    public long getVirtualhost_housekeepingCheckPeriod()
    {
        return (Long) getAttribute(VIRTUALHOST_HOUSEKEEPING_CHECK_PERIOD);
    }

    @Override
    public int getConnection_sessionCountLimit()
    {
        return (Integer) getAttribute(CONNECTION_SESSION_COUNT_LIMIT);
    }

    @Override
    public int getConnection_heartBeatDelay()
    {
        return (Integer) getAttribute(CONNECTION_HEART_BEAT_DELAY);
    }

    @Override
    public boolean getConnection_closeWhenNoRoute()
    {
        return (Boolean) getAttribute(CONNECTION_CLOSE_WHEN_NO_ROUTE);
    }

    @Override
    public int getStatisticsReportingPeriod()
    {
        return (Integer) getAttribute(STATISTICS_REPORTING_PERIOD);
    }

    @Override
    public boolean getStatisticsReportingResetEnabled()
    {
        return (Boolean) getAttribute(STATISTICS_REPORTING_RESET_ENABLED);
    }

    @Override
    public String getStoreType()
    {
        return _brokerStore.getType();
    }

    @Override
    public int getStoreVersion()
    {
        return _brokerStore.getVersion();
    }

    @Override
    public String getStorePath()
    {
        return _brokerStore.getStoreLocation();
    }

    @Override
    public String getModelVersion()
    {
        return Model.MODEL_VERSION;
    }

    @Override
    public long getVirtualhost_storeTransactionIdleTimeoutClose()
    {
        return (Long) getAttribute(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_CLOSE);
    }

    @Override
    public long getVirtualhost_storeTransactionIdleTimeoutWarn()
    {
        return (Long) getAttribute(VIRTUALHOST_STORE_TRANSACTION_IDLE_TIMEOUT_WARN);
    }

    @Override
    public long getVirtualhost_storeTransactionOpenTimeoutClose()
    {
        return (Long) getAttribute(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_CLOSE);
    }

    @Override
    public long getVirtualhost_storeTransactionOpenTimeoutWarn()
    {
        return (Long) getAttribute(VIRTUALHOST_STORE_TRANSACTION_OPEN_TIMEOUT_WARN);
    }

    public Collection<VirtualHost<?>> getVirtualHosts()
    {
        synchronized(_vhostAdapters)
        {
            return new ArrayList<VirtualHost<?>>(_vhostAdapters.values());
        }
    }

    public Collection<Port<?>> getPorts()
    {
        synchronized (_portAdapters)
        {
            return new ArrayList<Port<?>>(_portAdapters.values());
        }
    }

    public Collection<AuthenticationProvider<?>> getAuthenticationProviders()
    {
        synchronized (_authenticationProviders)
        {
            return new ArrayList<AuthenticationProvider<?>>(_authenticationProviders.values());
        }
    }

    public AuthenticationProvider<?> findAuthenticationProviderByName(String authenticationProviderName)
    {
        if (isManagementMode())
        {
            return _managementAuthenticationProvider;
        }
        Collection<AuthenticationProvider<?>> providers = getAuthenticationProviders();
        for (AuthenticationProvider<?> authenticationProvider : providers)
        {
            if (authenticationProvider.getName().equals(authenticationProviderName))
            {
                return authenticationProvider;
            }
        }
        return null;
    }

    public KeyStore<?> findKeyStoreByName(String keyStoreName)
    {
        synchronized(_keyStores)
        {
            return _keyStores.get(keyStoreName);
        }
    }

    public TrustStore<?> findTrustStoreByName(String trustStoreName)
    {
        synchronized(_trustStores)
        {
            return _trustStores.get(trustStoreName);
        }
    }

    @Override
    public Collection<GroupProvider<?>> getGroupProviders()
    {
        synchronized (_groupProviders)
        {
            return new ArrayList<GroupProvider<?>>(_groupProviders.values());
        }
    }

    private VirtualHost createVirtualHost(final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        final VirtualHostAdapter virtualHostAdapter = new VirtualHostAdapter(UUID.randomUUID(), attributes, this,
                _statisticsGatherer, getTaskExecutor());
        addVirtualHost(virtualHostAdapter);

        // permission has already been granted to create the virtual host
        // disable further access check on other operations, e.g. create exchange
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
                            {
                                @Override
                                public Object run()
                                {
                                    virtualHostAdapter.setDesiredState(State.INITIALISING, State.ACTIVE);
                                    return null;
                                }
                            });
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

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }


    public State getState()
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


    @Override
    public long getBytesIn()
    {
        return _statisticsGatherer.getDataReceiptStatistics().getTotal();
    }

    @Override
    public long getBytesOut()
    {
        return _statisticsGatherer.getDataDeliveryStatistics().getTotal();
    }

    @Override
    public long getMessagesIn()
    {
        return _statisticsGatherer.getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getMessagesOut()
    {
        return _statisticsGatherer.getMessageDeliveryStatistics().getTotal();
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
        else if(clazz == AccessControlProvider.class)
        {
            return (Collection<C>) getAccessControlProviders();
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
        else if(childClass == AccessControlProvider.class)
        {
            return (C) createAccessControlProvider(attributes);
        }
        else if(childClass == AuthenticationProvider.class)
        {
            return (C) createAuthenticationProvider(attributes);
        }
        else if(childClass == KeyStore.class)
        {
            return (C) createKeyStore(attributes);
        }
        else if(childClass == TrustStore.class)
        {
            return (C) createTrustStore(attributes);
        }
        else if(childClass == GroupProvider.class)
        {
            return (C) createGroupProvider(attributes);
        }
        else
        {
            throw new IllegalArgumentException("Cannot create child of class " + childClass.getSimpleName());
        }
    }

    /**
     * Called when adding a new port via the management interface
     */
    private Port createPort(Map<String, Object> attributes)
    {
        Port<?> port = _portFactory.createPort(UUID.randomUUID(), this, attributes);
        addPort(port);

        //1. AMQP ports are disabled during ManagementMode.
        //2. The management plugins can currently only start ports at broker startup and
        //   not when they are newly created via the management interfaces.
        //3. When active ports are deleted, or their port numbers updated, the broker must be
        //   restarted for it to take effect so we can't reuse port numbers until it is.
        boolean quiesce = isManagementMode() || !(port instanceof AmqpPortAdapter) || isPreviouslyUsedPortNumber(port);

        port.setDesiredState(State.INITIALISING, quiesce ? State.QUIESCED : State.ACTIVE);

        return port;
    }

    private void addPort(Port<?> port)
    {
        synchronized (_portAdapters)
        {
            int portNumber = port.getPort();
            String portName = port.getName();
            UUID portId = port.getId();

            for(Port<?> p : _portAdapters.values())
            {
                if(portNumber == p.getPort())
                {
                    throw new IllegalConfigurationException("Can't add port " + portName + " because port number " + portNumber + " is already configured for port " + p.getName());
                }

                if(portName.equals(p.getName()))
                {
                    throw new IllegalConfigurationException("Can't add Port because one with name " + portName + " already exists");
                }

                if(portId.equals(p.getId()))
                {
                    throw new IllegalConfigurationException("Can't add Port because one with id " + portId + " already exists");
                }
            }

            _portAdapters.put(port.getId(), port);
        }
        port.addChangeListener(this);
    }

    private AccessControlProvider<?> createAccessControlProvider(Map<String, Object> attributes)
    {
        AccessControlProvider<?> accessControlProvider;
        synchronized (_accessControlProviders)
        {
            accessControlProvider = _accessControlProviderFactory.create(UUID.randomUUID(), this, attributes);
            addAccessControlProvider(accessControlProvider);
        }

        boolean quiesce = isManagementMode() ;
        accessControlProvider.setDesiredState(State.INITIALISING, quiesce ? State.QUIESCED : State.ACTIVE);

        return accessControlProvider;
    }

    /**
     * @throws IllegalConfigurationException if an AuthenticationProvider with the same name already exists
     */
    private void addAccessControlProvider(AccessControlProvider<?> accessControlProvider)
    {
        String name = accessControlProvider.getName();
        synchronized (_authenticationProviders)
        {
            if (_accessControlProviders.containsKey(accessControlProvider.getId()))
            {
                throw new IllegalConfigurationException("Can't add AccessControlProvider because one with id " + accessControlProvider.getId() + " already exists");
            }
            for (AccessControlProvider<?> provider : _accessControlProviders.values())
            {
                if (provider.getName().equals(name))
                {
                    throw new IllegalConfigurationException("Can't add AccessControlProvider because one with name " + name + " already exists");
                }
            }
            _accessControlProviders.put(accessControlProvider.getId(), accessControlProvider);
        }

        accessControlProvider.addChangeListener(this);
        accessControlProvider.addChangeListener(_securityManager);
    }

    private boolean deleteAccessControlProvider(AccessControlProvider<?> accessControlProvider)
    {
        AccessControlProvider removedAccessControlProvider;
        synchronized (_accessControlProviders)
        {
            removedAccessControlProvider = _accessControlProviders.remove(accessControlProvider.getId());
        }

        if(removedAccessControlProvider != null)
        {
            removedAccessControlProvider.removeChangeListener(this);
            removedAccessControlProvider.removeChangeListener(_securityManager);
        }

        return removedAccessControlProvider != null;
    }

    private AuthenticationProvider createAuthenticationProvider(Map<String, Object> attributes)
    {
        AuthenticationProvider<?> authenticationProvider = _authenticationProviderFactory.create(UUID.randomUUID(), this, attributes);
        authenticationProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
        addAuthenticationProvider(authenticationProvider);
        return authenticationProvider;
    }

    /**
     * @throws IllegalConfigurationException if an AuthenticationProvider with the same name already exists
     */
    private void addAuthenticationProvider(AuthenticationProvider<?> authenticationProvider)
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

    private GroupProvider<?> createGroupProvider(Map<String, Object> attributes)
    {
        GroupProvider<?> groupProvider = _groupProviderFactory.create(UUID.randomUUID(), this, attributes);
        groupProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
        addGroupProvider(groupProvider);
        return groupProvider;
    }

    private void addGroupProvider(GroupProvider<?> groupProvider)
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

    private boolean deleteGroupProvider(GroupProvider groupProvider)
    {
        GroupProvider removedGroupProvider = null;
        synchronized (_groupProviders)
        {
            removedGroupProvider = _groupProviders.remove(groupProvider.getName());
        }

        if(removedGroupProvider != null)
        {
            removedGroupProvider.removeChangeListener(this);
        }

        return removedGroupProvider != null;
    }

    private KeyStore createKeyStore(Map<String, Object> attributes)
    {
        KeyStore keyStore = new KeyStoreAdapter(UUIDGenerator.generateRandomUUID(), this, attributes);
        addKeyStore(keyStore);

        return keyStore;
    }

    private TrustStore createTrustStore(Map<String, Object> attributes)
    {
        TrustStore trustStore = new TrustStoreAdapter(UUIDGenerator.generateRandomUUID(), this, attributes);
        addTrustStore(trustStore);

        return trustStore;
    }

    private void addKeyStore(KeyStore keyStore)
    {
        synchronized (_keyStores)
        {
            if(_keyStores.containsKey(keyStore.getName()))
            {
                throw new IllegalConfigurationException("Can't add KeyStore because one with name " + keyStore.getName() + " already exists");
            }
            _keyStores.put(keyStore.getName(), keyStore);
        }
        keyStore.addChangeListener(this);
    }

    private boolean deleteKeyStore(KeyStore object)
    {
        synchronized(_keyStores)
        {
            String name = object.getName();
            KeyStore removedKeyStore = _keyStores.remove(name);
            if(removedKeyStore != null)
            {
                removedKeyStore.removeChangeListener(this);
            }

            return removedKeyStore != null;
        }
    }

    private void addTrustStore(TrustStore trustStore)
    {
        synchronized (_trustStores)
        {
            if(_trustStores.containsKey(trustStore.getName()))
            {
                throw new IllegalConfigurationException("Can't add TrustStore because one with name " + trustStore.getName() + " already exists");
            }
            _trustStores.put(trustStore.getName(), trustStore);
        }
        trustStore.addChangeListener(this);
    }

    private boolean deleteTrustStore(TrustStore object)
    {
        synchronized(_trustStores)
        {
            String name = object.getName();
            TrustStore removedTrustStore = _trustStores.remove(name);
            if(removedTrustStore != null)
            {
                removedTrustStore.removeChangeListener(this);
            }

            return removedTrustStore != null;
        }
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return getAttributeNames(Broker.class);
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
        else if(BUILD_VERSION.equals(name))
        {
            return QpidProperties.getBuildVersion();
        }
        else if(OPERATING_SYSTEM.equals(name))
        {
            return SystemUtils.getOSString();
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
        else if(SUPPORTED_BROKER_STORE_TYPES.equals(name))
        {
            return _supportedBrokerStoreTypes;
        }
        else if(SUPPORTED_VIRTUALHOST_STORE_TYPES.equals(name))
        {
            return _supportedVirtualHostStoreTypes;
        }
        else if(SUPPORTED_VIRTUALHOST_TYPES.equals(name))
        {
            return VirtualHostFactory.TYPES.get();
        }
        else if(SUPPORTED_AUTHENTICATION_PROVIDERS.equals(name))
        {
            return _authenticationProviderFactory.getSupportedAuthenticationProviders();
        }
        else if (SUPPORTED_PREFERENCES_PROVIDER_TYPES.equals(name))
        {
            return PreferencesProviderFactory.FACTORY_LOADER.getSupportedTypes();
        }
        else if (MODEL_VERSION.equals(name))
        {
            return Model.MODEL_VERSION;
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

    private boolean deletePort(State oldState, Port portAdapter)
    {
        Port<?> removedPort;
        synchronized (_portAdapters)
        {
            removedPort = _portAdapters.remove(portAdapter.getId());
        }

        if (removedPort != null)
        {
            removedPort.removeChangeListener(this);

            if(oldState == State.ACTIVE)
            {
                //Record the originally used port numbers of previously-active ports being deleted, to ensure
                //when creating new ports we don't try to re-bind a port number that we are currently still using
                recordPreviouslyUsedPortNumberIfNecessary(removedPort, removedPort.getPort());
            }
        }

        return removedPort != null;
    }

    private boolean deleteAuthenticationProvider(AuthenticationProvider<?> authenticationProvider)
    {
        AuthenticationProvider removedAuthenticationProvider;
        synchronized (_authenticationProviders)
        {
            removedAuthenticationProvider = _authenticationProviders.remove(authenticationProvider.getId());
        }

        if(removedAuthenticationProvider != null)
        {
            removedAuthenticationProvider.removeChangeListener(this);
        }

        return removedAuthenticationProvider != null;
    }

    private void addVirtualHost(VirtualHost<?> virtualHost)
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
            changeState(_accessControlProviders, currentState, State.ACTIVE, false);


            changeState(_vhostAdapters, currentState, State.ACTIVE, false);

            changeState(_portAdapters, currentState,State.ACTIVE, false);
            changeState(_plugins, currentState,State.ACTIVE, false);

            if (isManagementMode())
            {
                _eventLogger.message(BrokerMessages.MANAGEMENT_MODE(BrokerOptions.MANAGEMENT_MODE_USER_NAME,
                                                                 _brokerOptions.getManagementModePassword()));
            }
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
                if (State.ACTIVE.equals(desiredState) && State.QUIESCED.equals(configuredObject.getState()))
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
            else if(object instanceof AccessControlProvider)
            {
                childDeleted = deleteAccessControlProvider((AccessControlProvider)object);
            }
            else if(object instanceof Port)
            {
                childDeleted = deletePort(oldState, (Port)object);
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
        if(object instanceof Port)
        {
            //Record all the originally used port numbers of active ports, to ensure that when
            //creating new ports we don't try to re-bind a port number that we are still using
            if(Port.PORT.equals(attributeName) && object.getState() == State.ACTIVE)
            {
                recordPreviouslyUsedPortNumberIfNecessary((Port) object, (Integer)oldAttributeValue);
            }
        }
    }

    private void addPlugin(ConfiguredObject<?> plugin)
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


    private Collection<ConfiguredObject<?>> getPlugins()
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
        else if(object instanceof AccessControlProvider)
        {
            addAccessControlProvider((AccessControlProvider)object);
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
        AuthenticationProvider provider = getAuthenticationProvider(localAddress);

        if(provider == null)
        {
            throw new IllegalConfigurationException("Unable to determine authentication provider for address: " + localAddress);
        }

        return provider.getSubjectCreator();
    }

    @Override
    public AuthenticationProvider<?> getAuthenticationProvider(SocketAddress localAddress)
    {
        InetSocketAddress inetSocketAddress = (InetSocketAddress)localAddress;
        AuthenticationProvider provider = null;
        Collection<Port<?>> ports = getPorts();
        for (Port<?> p : ports)
        {
            if (inetSocketAddress.getPort() == p.getPort())
            {
                provider = p.getAuthenticationProvider();
                break;
            }
        }
        return provider;
    }

    @Override
    public Collection<KeyStore<?>> getKeyStores()
    {
        synchronized(_keyStores)
        {
            return Collections.unmodifiableCollection(_keyStores.values());
        }
    }

    @Override
    public Collection<TrustStore<?>> getTrustStores()
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
    public TaskExecutor getTaskExecutor()
    {
        return super.getTaskExecutor();
    }

    @Override
    protected void changeAttributes(Map<String, Object> attributes)
    {
        Map<String, Object> convertedAttributes = MapValueConverter.convert(attributes, ATTRIBUTE_TYPES);
        validateAttributes(convertedAttributes);

        super.changeAttributes(convertedAttributes);
    }

    private void validateAttributes(Map<String, Object> convertedAttributes)
    {
        if (convertedAttributes.containsKey(MODEL_VERSION) && !Model.MODEL_VERSION.equals(convertedAttributes.get(MODEL_VERSION)))
        {
            throw new IllegalConfigurationException("Cannot change the model version");
        }

        if (convertedAttributes.containsKey(STORE_VERSION)
                && !new Integer(_brokerStore.getVersion()).equals(convertedAttributes.get(STORE_VERSION)))
        {
            throw new IllegalConfigurationException("Cannot change the store version");
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
        Long queueFlowControlResumeSize = (Long) convertedAttributes.get(QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
        if (queueFlowControlSize != null || queueFlowControlResumeSize != null )
        {
            if (queueFlowControlSize == null)
            {
                queueFlowControlSize = (Long)getAttribute(QUEUE_FLOW_CONTROL_SIZE_BYTES);
            }
            if (queueFlowControlResumeSize == null)
            {
                queueFlowControlResumeSize = (Long)getAttribute(QUEUE_FLOW_CONTROL_RESUME_SIZE_BYTES);
            }
            if (queueFlowControlResumeSize > queueFlowControlSize)
            {
                throw new IllegalConfigurationException("Flow resume size can't be greater than flow control size");
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

    @Override
    protected void authoriseSetAttribute(String name, Object expected, Object desired) throws AccessControlException
    {
        if (!_securityManager.authoriseConfiguringBroker(getName(), Broker.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of broker attributes is denied");
        }
    }

    @Override
    protected <C extends ConfiguredObject> void authoriseCreateChild(Class<C> childClass, Map<String, Object> attributes,
            ConfiguredObject... otherParents) throws AccessControlException
    {
        if (!_securityManager.authoriseConfiguringBroker(String.valueOf(attributes.get(NAME)), childClass, Operation.CREATE))
        {
            throw new AccessControlException("Creation of new broker level entity is denied");
        }
    }

    @Override
    protected void authoriseSetAttributes(Map<String, Object> attributes) throws AccessControlException
    {
        if (!_securityManager.authoriseConfiguringBroker(getName(), Broker.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of broker attributes is denied");
        }
    }

    @Override
    public boolean isManagementMode()
    {
        return _brokerOptions.isManagementMode();
    }

    @Override
    public Collection<AccessControlProvider<?>> getAccessControlProviders()
    {
        synchronized (_accessControlProviders)
        {
            return new ArrayList<AccessControlProvider<?>>(_accessControlProviders.values());
        }
    }

    private void recordPreviouslyUsedPortNumberIfNecessary(Port port, Integer portNumber)
    {
        //If we haven't previously recorded its original port number, record it now
        if(!_stillInUsePortNumbers.containsKey(port))
        {
            _stillInUsePortNumbers.put(port, portNumber);
        }
    }

    private boolean isPreviouslyUsedPortNumber(Port port)
    {
        return _stillInUsePortNumbers.containsValue(port.getPort());
    }

    @Override
    public EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    @Override
    public void setEventLogger(final EventLogger eventLogger)
    {
        _eventLogger = eventLogger;
    }
}
