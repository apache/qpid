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
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.model.port.PortWithAuthProvider;
import org.apache.qpid.server.plugin.ConfiguredObjectTypeFactory;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleAuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.util.SystemUtils;

@ManagedObject(category = false, type = "adapter")
public class BrokerAdapter extends AbstractConfiguredObject<BrokerAdapter> implements Broker<BrokerAdapter>, ConfigurationChangeListener, StatisticsGatherer, StatisticsGatherer.Source
{
    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    private static final Pattern MODEL_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+$");

    @SuppressWarnings("serial")
    public static final Map<String, Type> ATTRIBUTE_TYPES = Collections.unmodifiableMap(new HashMap<String, Type>(){{

        put(STATISTICS_REPORTING_RESET_ENABLED, Boolean.class);

        put(CONNECTION_SESSION_COUNT_LIMIT, Integer.class);
        put(CONNECTION_HEART_BEAT_DELAY, Integer.class);
        put(CONNECTION_CLOSE_WHEN_NO_ROUTE, Boolean.class);
        put(STATISTICS_REPORTING_PERIOD, Integer.class);

        put(NAME, String.class);
        put(DEFAULT_VIRTUAL_HOST, String.class);

        put(MODEL_VERSION, String.class);
        put(STORE_VERSION, String.class);
    }});

    public static final String MANAGEMENT_MODE_AUTHENTICATION = "MANAGEMENT_MODE_AUTHENTICATION";
    private final ConfiguredObjectFactory _objectFactory;

    private String[] POSITIVE_NUMERIC_ATTRIBUTES = { CONNECTION_SESSION_COUNT_LIMIT,
            CONNECTION_HEART_BEAT_DELAY, STATISTICS_REPORTING_PERIOD };


    private EventLogger _eventLogger;
    private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;

    private final Map<String, VirtualHost<?,?,?>> _vhostAdapters = new HashMap<String, VirtualHost<?,?,?>>();
    private final Map<UUID, Port<?>> _portAdapters = new HashMap<UUID, Port<?>>();
    private final Map<Port, Integer> _stillInUsePortNumbers = new HashMap<Port, Integer>();
    private final Map<UUID, AuthenticationProvider<?>> _authenticationProviders = new HashMap<UUID, AuthenticationProvider<?>>();
    private final Map<String, GroupProvider<?>> _groupProviders = new HashMap<String, GroupProvider<?>>();
    private final Map<UUID, ConfiguredObject<?>> _plugins = new HashMap<UUID, ConfiguredObject<?>>();
    private final Map<String, KeyStore<?>> _keyStores = new HashMap<String, KeyStore<?>>();
    private final Map<String, TrustStore<?>> _trustStores = new HashMap<String, TrustStore<?>>();
    private final Map<UUID, AccessControlProvider<?>> _accessControlProviders = new HashMap<UUID, AccessControlProvider<?>>();

    private final SecurityManager _securityManager;

    private final Collection<String> _supportedVirtualHostStoreTypes;

    private AuthenticationProvider<?> _managementModeAuthenticationProvider;
    private BrokerOptions _brokerOptions;

    private Timer _reportingTimer;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    @ManagedAttributeField
    private String _defaultVirtualHost;
    @ManagedAttributeField
    private int _connection_sessionCountLimit;
    @ManagedAttributeField
    private int _connection_heartBeatDelay;
    @ManagedAttributeField
    private boolean _connection_closeWhenNoRoute;
    @ManagedAttributeField
    private int _statisticsReportingPeriod;
    @ManagedAttributeField
    private boolean _statisticsReportingResetEnabled;


    public BrokerAdapter(UUID id,
                         Map<String, Object> attributes,
                         SystemContext parent)
    {
        super(Collections.<Class<? extends ConfiguredObject>, ConfiguredObject<?>>singletonMap(SystemContext.class, parent),
              combineIdWithAttributes(id,MapValueConverter.convert(attributes, ATTRIBUTE_TYPES)), parent.getTaskExecutor());

        _objectFactory = parent.getObjectFactory();
        _virtualHostRegistry = new VirtualHostRegistry(parent.getEventLogger());

        _logRecorder = parent.getLogRecorder();
        _eventLogger = parent.getEventLogger();
        _brokerOptions = parent.getBrokerOptions();
        _securityManager = new SecurityManager(this, _brokerOptions.isManagementMode());
        _supportedVirtualHostStoreTypes = MessageStoreFactory.FACTORY_LOADER.getSupportedTypes();
        if (_brokerOptions.isManagementMode())
        {
            Map<String,Object> authManagerAttrs = new HashMap<String, Object>();
            authManagerAttrs.put(NAME,"MANAGEMENT_MODE_AUTHENTICATION");
            authManagerAttrs.put(ID, UUID.randomUUID());
            SimpleAuthenticationManager authManager = new SimpleAuthenticationManager(this, authManagerAttrs);
            authManager.addUser(BrokerOptions.MANAGEMENT_MODE_USER_NAME, _brokerOptions.getManagementModePassword());
            _managementModeAuthenticationProvider = authManager;
        }
        initialiseStatistics();
    }

    public void validate()
    {
        super.validate();
        String modelVersion = (String) getActualAttributes().get(Broker.MODEL_VERSION);
        if (modelVersion == null)
        {
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " must be specified");
        }

        if (!MODEL_VERSION_PATTERN.matcher(modelVersion).matches())
        {
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " is specified in incorrect format: "
                                                    + modelVersion);
        }

        int versionSeparatorPosition = modelVersion.indexOf(".");
        String majorVersionPart = modelVersion.substring(0, versionSeparatorPosition);
        int majorModelVersion = Integer.parseInt(majorVersionPart);
        int minorModelVersion = Integer.parseInt(modelVersion.substring(versionSeparatorPosition + 1));

        if (majorModelVersion != Model.MODEL_MAJOR_VERSION || minorModelVersion > Model.MODEL_MINOR_VERSION)
        {
            throw new IllegalConfigurationException("The model version '" + modelVersion
                                                    + "' in configuration is incompatible with the broker model version '" + Model.MODEL_VERSION + "'");
        }

    }

    protected void onOpen()
    {
        super.onOpen();
        if(_brokerOptions.isManagementMode())
        {
            _managementModeAuthenticationProvider.open();
        }
        _virtualHostRegistry.setDefaultVirtualHostName(getDefaultVirtualHost());

        for(KeyStore<?> keyStore : getChildren(KeyStore.class))
        {
            addKeyStore(keyStore);
        }
        for(TrustStore<?> trustStore : getChildren(TrustStore.class))
        {
            addTrustStore(trustStore);
        }
        for(AuthenticationProvider<?> authenticationProvider : getChildren(AuthenticationProvider.class))
        {
            addAuthenticationProvider(authenticationProvider);
        }
        for(Port<?> port : getChildren(Port.class))
        {
            addPort(port);
        }
        for(Plugin<?> plugin : getChildren(Plugin.class))
        {
            addPlugin(plugin);
        }
        for(GroupProvider<?> groupProvider : getChildren(GroupProvider.class))
        {
            addGroupProvider(groupProvider);
        }
        for(AccessControlProvider<?> accessControlProvider : getChildren(AccessControlProvider.class))
        {
            addAccessControlProvider(accessControlProvider);
        }
        for(VirtualHost<?,?,?> virtualHost : getChildren(VirtualHost.class))
        {
            addVirtualHost(virtualHost);
        }


        initialiseStatistics();
    }

    private void initialiseStatisticsReporting()
    {
        long report = ((Number)getAttribute(Broker.STATISTICS_REPORTING_PERIOD)).intValue() * 1000; // convert to ms
        final boolean reset = (Boolean)getAttribute(Broker.STATISTICS_REPORTING_RESET_ENABLED);

        /* add a timer task to report statistics if generation is enabled for broker or virtualhosts */
        if (report > 0L)
        {
            _reportingTimer = new Timer("Statistics-Reporting", true);
            StatisticsReportingTask task = new StatisticsReportingTask(reset, _eventLogger);
            _reportingTimer.scheduleAtFixedRate(task, report / 2, report);
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
    public Collection<String> getSupportedVirtualHostStoreTypes()
    {
        return _supportedVirtualHostStoreTypes;
    }

    @Override
    public Collection<String> getSupportedAuthenticationProviders()
    {
        return _objectFactory.getSupportedTypes(AuthenticationProvider.class);
    }

    @Override
    public Collection<String> getSupportedPreferencesProviderTypes()
    {
        return _objectFactory.getSupportedTypes(PreferencesProvider.class);
    }

    @Override
    public String getDefaultVirtualHost()
    {
        return _defaultVirtualHost;
    }

    @Override
    public int getConnection_sessionCountLimit()
    {
        return _connection_sessionCountLimit;
    }

    @Override
    public int getConnection_heartBeatDelay()
    {
        return _connection_heartBeatDelay;
    }

    @Override
    public boolean getConnection_closeWhenNoRoute()
    {
        return _connection_closeWhenNoRoute;
    }

    @Override
    public int getStatisticsReportingPeriod()
    {
        return _statisticsReportingPeriod;
    }

    @Override
    public boolean getStatisticsReportingResetEnabled()
    {
        return _statisticsReportingResetEnabled;
    }

    @Override
    public String getModelVersion()
    {
        return Model.MODEL_VERSION;
    }

    public Collection<VirtualHost<?,?,?>> getVirtualHosts()
    {
        synchronized(_vhostAdapters)
        {
            return new ArrayList<VirtualHost<?,?,?>>(_vhostAdapters.values());
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
            return _managementModeAuthenticationProvider;
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
        ConfiguredObjectTypeFactory virtualHostFactory =
                _objectFactory.getConfiguredObjectTypeFactory(VirtualHost.class, attributes);
        final VirtualHost virtualHost = (VirtualHost) virtualHostFactory.create(attributes,this);

        // permission has already been granted to create the virtual host
        // disable further access check on other operations, e.g. create exchange
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
                            {
                                @Override
                                public Object run()
                                {
                                    virtualHost.setDesiredState(State.INITIALISING, State.ACTIVE);
                                    return null;
                                }
                            });
        return virtualHost;
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
        return getDataReceiptStatistics().getTotal();
    }

    @Override
    public long getBytesOut()
    {
        return getDataDeliveryStatistics().getTotal();
    }

    @Override
    public long getMessagesIn()
    {
        return getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getMessagesOut()
    {
        return getMessageDeliveryStatistics().getTotal();
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
    private Port<?> createPort(Map<String, Object> attributes)
    {
        Port<?> port = createChild(Port.class, attributes);
        addPort(port);
        //1. AMQP ports are disabled during ManagementMode.
        //2. The management plugins can currently only start ports at broker startup and
        //   not when they are newly created via the management interfaces.
        //3. When active ports are deleted, or their port numbers updated, the broker must be
        //   restarted for it to take effect so we can't reuse port numbers until it is.
        boolean quiesce = isManagementMode() || !(port instanceof AmqpPort) || isPreviouslyUsedPortNumber(port);

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
            accessControlProvider = (AccessControlProvider<?>) createChild(AccessControlProvider.class, attributes);
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
        synchronized (_accessControlProviders)
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
        AuthenticationProvider<?> authenticationProvider = createChild(AuthenticationProvider.class, attributes);
        addAuthenticationProvider(authenticationProvider);
        authenticationProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
        return authenticationProvider;
    }

    private <X extends ConfiguredObject> X createChild(Class<X> clazz, Map<String, Object> attributes)
    {
        ConfiguredObjectTypeFactory factory =
                _objectFactory.getConfiguredObjectTypeFactory(clazz, attributes);
        if(!attributes.containsKey(ConfiguredObject.ID))
        {
            attributes = new HashMap<String, Object>(attributes);
            attributes.put(ConfiguredObject.ID, UUID.randomUUID());
        }
        final X instance = (X) factory.create(attributes, this);

        return instance;
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
        GroupProvider<?> groupProvider = createChild(GroupProvider.class, attributes);
        addGroupProvider(groupProvider);
        groupProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
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

        KeyStore<?> keyStore = createChild(KeyStore.class, attributes);

        addKeyStore(keyStore);
        return keyStore;
    }

    private TrustStore createTrustStore(Map<String, Object> attributes)
    {
        TrustStore trustStore = createChild(TrustStore.class, attributes);
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
        else if(SUPPORTED_VIRTUALHOST_STORE_TYPES.equals(name))
        {
            return _supportedVirtualHostStoreTypes;
        }
        else if(SUPPORTED_VIRTUALHOST_TYPES.equals(name))
        {
            return _objectFactory.getSupportedTypes(VirtualHost.class);
        }
        else if(SUPPORTED_AUTHENTICATION_PROVIDERS.equals(name))
        {
            return getSupportedAuthenticationProviders();
        }
        else if (SUPPORTED_PREFERENCES_PROVIDER_TYPES.equals(name))
        {
            return getSupportedPreferencesProviderTypes();
        }
        else if (MODEL_VERSION.equals(name))
        {
            return Model.MODEL_VERSION;
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

    private void addVirtualHost(VirtualHost<?,?,?> virtualHost)
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
            initialiseStatisticsReporting();
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
            //Stop Statistics Reporting
            if (_reportingTimer != null)
            {
                _reportingTimer.cancel();
            }

            changeState(_plugins, currentState,State.STOPPED, true);
            changeState(_portAdapters, currentState, State.STOPPED, true);
            changeState(_vhostAdapters,currentState, State.STOPPED, true);
            changeState(_authenticationProviders, currentState, State.STOPPED, true);
            changeState(_groupProviders, currentState, State.STOPPED, true);
            _virtualHostRegistry.close();
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
            if (p instanceof PortWithAuthProvider && inetSocketAddress.getPort() == p.getPort())
            {
                provider = ((PortWithAuthProvider<?>) p).getAuthenticationProvider();
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

    @Override
    public StatisticsGatherer getStatisticsGatherer()
    {
        return this;
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();

        for (VirtualHostImpl vhost : _virtualHostRegistry.getVirtualHosts())
        {
            vhost.resetStatistics();
        }
    }

    public void initialiseStatistics()
    {
        _messagesDelivered = new StatisticsCounter("messages-delivered");
        _dataDelivered = new StatisticsCounter("bytes-delivered");
        _messagesReceived = new StatisticsCounter("messages-received");
        _dataReceived = new StatisticsCounter("bytes-received");
    }

    private class StatisticsReportingTask extends TimerTask
    {
        private final int DELIVERED = 0;
        private final int RECEIVED = 1;

        private final boolean _reset;
        private final EventLogger _logger;
        private final Subject _subject;

        public StatisticsReportingTask(boolean reset, EventLogger logger)
        {
            _reset = reset;
            _logger = logger;
            _subject = SecurityManager.getSystemTaskSubject("Statistics");
        }

        public void run()
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    reportStatistics();
                    return null;
                }
            });
        }

        protected void reportStatistics()
        {
            try
            {
                _eventLogger.message(BrokerMessages.STATS_DATA(DELIVERED, _dataDelivered.getPeak() / 1024.0, _dataDelivered.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_MSGS(DELIVERED, _messagesDelivered.getPeak(), _messagesDelivered.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_DATA(RECEIVED, _dataReceived.getPeak() / 1024.0, _dataReceived.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_MSGS(RECEIVED,
                                                               _messagesReceived.getPeak(),
                                                               _messagesReceived.getTotal()));
                Collection<VirtualHostImpl> hosts = _virtualHostRegistry.getVirtualHosts();

                if (hosts.size() > 1)
                {
                    for (VirtualHostImpl vhost : hosts)
                    {
                        String name = vhost.getName();
                        StatisticsCounter dataDelivered = vhost.getDataDeliveryStatistics();
                        StatisticsCounter messagesDelivered = vhost.getMessageDeliveryStatistics();
                        StatisticsCounter dataReceived = vhost.getDataReceiptStatistics();
                        StatisticsCounter messagesReceived = vhost.getMessageReceiptStatistics();
                        EventLogger logger = vhost.getEventLogger();
                        logger.message(VirtualHostMessages.STATS_DATA(name,
                                                                      DELIVERED,
                                                                      dataDelivered.getPeak() / 1024.0,
                                                                      dataDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name, DELIVERED, messagesDelivered.getPeak(), messagesDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_DATA(name, RECEIVED, dataReceived.getPeak() / 1024.0, dataReceived.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name, RECEIVED, messagesReceived.getPeak(), messagesReceived.getTotal()));
                    }
                }

                if (_reset)
                {
                    resetStatistics();
                }
            }
            catch(Exception e)
            {
                LOGGER.warn("Unexpected exception occurred while reporting the statistics", e);
            }
        }
    }

    public AuthenticationProvider<?> getManagementModeAuthenticationProvider()
    {
        return _managementModeAuthenticationProvider;
    }
}
