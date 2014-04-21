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
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import org.apache.qpid.server.model.port.AbstractPortWithAuthProvider;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.plugin.MessageStoreFactory;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.security.auth.manager.SimpleAuthenticationManager;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.util.SystemUtils;

public class BrokerAdapter extends AbstractConfiguredObject<BrokerAdapter> implements Broker<BrokerAdapter>, ConfigurationChangeListener, StatisticsGatherer, StatisticsGatherer.Source
{
    private static final Logger LOGGER = Logger.getLogger(BrokerAdapter.class);

    private static final Pattern MODEL_VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+$");


    public static final String MANAGEMENT_MODE_AUTHENTICATION = "MANAGEMENT_MODE_AUTHENTICATION";

    private String[] POSITIVE_NUMERIC_ATTRIBUTES = { CONNECTION_SESSION_COUNT_LIMIT,
            CONNECTION_HEART_BEAT_DELAY, STATISTICS_REPORTING_PERIOD };


    private EventLogger _eventLogger;
    //private final VirtualHostRegistry _virtualHostRegistry;
    private final LogRecorder _logRecorder;

    private final Map<Port, Integer> _stillInUsePortNumbers = new HashMap<Port, Integer>();

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


    public BrokerAdapter(Map<String, Object> attributes,
                         SystemContext parent)
    {
        super(parentsMap(parent),
              attributes,
              parent.getTaskExecutor());

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
            SimpleAuthenticationManager authManager = new SimpleAuthenticationManager(authManagerAttrs, this);
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
            deleted();
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " must be specified");
        }

        if (!MODEL_VERSION_PATTERN.matcher(modelVersion).matches())
        {
            deleted();
            throw new IllegalConfigurationException("Broker " + Broker.MODEL_VERSION + " is specified in incorrect format: "
                                                    + modelVersion);
        }

        int versionSeparatorPosition = modelVersion.indexOf(".");
        String majorVersionPart = modelVersion.substring(0, versionSeparatorPosition);
        int majorModelVersion = Integer.parseInt(majorVersionPart);
        int minorModelVersion = Integer.parseInt(modelVersion.substring(versionSeparatorPosition + 1));

        if (majorModelVersion != BrokerModel.MODEL_MAJOR_VERSION || minorModelVersion > BrokerModel.MODEL_MINOR_VERSION)
        {
            deleted();
            throw new IllegalConfigurationException("The model version '" + modelVersion
                                                    + "' in configuration is incompatible with the broker model version '" + BrokerModel.MODEL_VERSION + "'");
        }

        if(!isDurable())
        {
            deleted();
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        Broker updated = (Broker) proxyForValidation;
        if (changedAttributes.contains(MODEL_VERSION) && !BrokerModel.MODEL_VERSION.equals(updated.getModelVersion()))
        {
            throw new IllegalConfigurationException("Cannot change the model version");
        }

        String defaultVirtualHost = updated.getDefaultVirtualHost();
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
            Number value = (Number) updated.getAttribute(attributeName);
            if (value != null && value.longValue() < 0)
            {
                throw new IllegalConfigurationException("Only positive integer value can be specified for the attribute "
                                                        + attributeName);
            }
        }
    }

    protected void onOpen()
    {
        super.onOpen();
        if(_brokerOptions.isManagementMode())
        {
            _managementModeAuthenticationProvider.open();
        }

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
        for(VirtualHostNode<?> virtualHostNode : getChildren(VirtualHostNode.class))
        {
            addVirtualHostNode(virtualHostNode);
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
        return QpidProperties.getBuildVersion();
    }

    @Override
    public String getOperatingSystem()
    {
        return SystemUtils.getOSString();
    }

    @Override
    public String getPlatform()
    {
        return System.getProperty("java.vendor") + " "
                      + System.getProperty("java.runtime.version", System.getProperty("java.version"));
    }

    @Override
    public String getProcessPid()
    {
        // TODO
        return null;
    }

    @Override
    public String getProductVersion()
    {
        return QpidProperties.getReleaseVersion();
    }

    @Override
    public Collection<String> getSupportedVirtualHostStoreTypes()
    {
        return _supportedVirtualHostStoreTypes;
    }

    @Override
    public Collection<String> getSupportedVirtualHostTypes()
    {
        return getObjectFactory().getSupportedTypes(VirtualHost.class);
    }

    @Override
    public Collection<String> getSupportedAuthenticationProviders()
    {
        return getObjectFactory().getSupportedTypes(AuthenticationProvider.class);
    }

    @Override
    public Collection<String> getSupportedPreferencesProviderTypes()
    {
        return getObjectFactory().getSupportedTypes(PreferencesProvider.class);
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
        return BrokerModel.MODEL_VERSION;
    }

    @Override
    public Collection<VirtualHostNode<?>> getVirtualHostNodes()
    {
        Collection children = getChildren(VirtualHostNode.class);
        return children;
    }

    public Collection<Port<?>> getPorts()
    {
        Collection children = getChildren(Port.class);
        return children;
    }

    public Collection<AuthenticationProvider<?>> getAuthenticationProviders()
    {
        Collection children = getChildren(AuthenticationProvider.class);
        return children;
    }

    public AuthenticationProvider<?> findAuthenticationProviderByName(String authenticationProviderName)
    {
        if (isManagementMode())
        {
            return _managementModeAuthenticationProvider;
        }
        return getChildByName(AuthenticationProvider.class, authenticationProviderName);
    }

    public KeyStore<?> findKeyStoreByName(String keyStoreName)
    {
        return getChildByName(KeyStore.class, keyStoreName);
    }

    public TrustStore<?> findTrustStoreByName(String trustStoreName)
    {
        return getChildByName(TrustStore.class, trustStoreName);
    }

    @Override
    public Collection<GroupProvider<?>> getGroupProviders()
    {
        Collection children = getChildren(GroupProvider.class);
        return children;
    }

    private VirtualHostNode<?> createVirtualHostNode(Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {

        final VirtualHostNode virtualHostNode = getObjectFactory().create(VirtualHostNode.class,attributes, this);

        // permission has already been granted to create the virtual host
        // disable further access check on other operations, e.g. create exchange
        Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<Object>()
                            {
                                @Override
                                public Object run()
                                {
                                    virtualHostNode.setDesiredState(State.INITIALISING, State.ACTIVE);
                                    return null;
                                }
                            });
        return virtualHostNode;
    }

    public State getState()
    {
        return null;  //TODO
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
    public <C extends ConfiguredObject> C addChild(final Class<C> childClass, final Map<String, Object> attributes, final ConfiguredObject... otherParents)
    {
        return runTask( new TaskExecutor.Task<C>()
        {
            @Override
            public C execute()
            {
                if (childClass == VirtualHostNode.class)
                {
                    return (C) createVirtualHostNode(attributes);
                }
                else if (childClass == Port.class)
                {
                    return (C) createPort(attributes);
                }
                else if (childClass == AccessControlProvider.class)
                {
                    return (C) createAccessControlProvider(attributes);
                }
                else if (childClass == AuthenticationProvider.class)
                {
                    return (C) createAuthenticationProvider(attributes);
                }
                else if (childClass == KeyStore.class)
                {
                    return (C) createKeyStore(attributes);
                }
                else if (childClass == TrustStore.class)
                {
                    return (C) createTrustStore(attributes);
                }
                else if (childClass == GroupProvider.class)
                {
                    return (C) createGroupProvider(attributes);
                }
                else
                {
                    throw new IllegalArgumentException("Cannot create child of class " + childClass.getSimpleName());
                }
            }
        });

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

    private void addPort(final Port<?> port)
    {
        assert getTaskExecutor().isTaskExecutorThread();

        int portNumber = port.getPort();
        String portName = port.getName();

        for (Port<?> p : getChildren(Port.class))
        {
            if(p != port)
            {
                if (portNumber == p.getPort())
                {
                    throw new IllegalConfigurationException("Can't add port "
                                                            + portName
                                                            + " because port number "
                                                            + portNumber
                                                            + " is already configured for port "
                                                            + p.getName());
                }
            }
        }

        port.addChangeListener(this);

    }

    private AccessControlProvider<?> createAccessControlProvider(final Map<String, Object> attributes)
    {
        assert getTaskExecutor().isTaskExecutorThread();

        AccessControlProvider<?> accessControlProvider = (AccessControlProvider<?>) createChild(AccessControlProvider.class, attributes);
        addAccessControlProvider(accessControlProvider);

        boolean quiesce = isManagementMode();
        accessControlProvider.setDesiredState(State.INITIALISING, quiesce ? State.QUIESCED : State.ACTIVE);

        return accessControlProvider;

    }

    private void addAccessControlProvider(final AccessControlProvider<?> accessControlProvider)
    {
        assert getTaskExecutor().isTaskExecutorThread();

        accessControlProvider.addChangeListener(this);
        accessControlProvider.addChangeListener(_securityManager);

    }

    private boolean deleteAccessControlProvider(AccessControlProvider<?> accessControlProvider)
    {
        accessControlProvider.removeChangeListener(this);
        accessControlProvider.removeChangeListener(_securityManager);

        return true;
    }

    private AuthenticationProvider createAuthenticationProvider(final Map<String, Object> attributes)
    {
        return runTask(new TaskExecutor.Task<AuthenticationProvider>()
        {
            @Override
            public AuthenticationProvider execute()
            {
                AuthenticationProvider<?> authenticationProvider = createChild(AuthenticationProvider.class, attributes);
                addAuthenticationProvider(authenticationProvider);
                authenticationProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
                return authenticationProvider;
            }
        });
    }

    private <X extends ConfiguredObject> X createChild(Class<X> clazz, Map<String, Object> attributes)
    {
        if(!attributes.containsKey(ConfiguredObject.ID))
        {
            attributes = new HashMap<String, Object>(attributes);
            attributes.put(ConfiguredObject.ID, UUID.randomUUID());
        }
        final X instance = (X) getObjectFactory().create(clazz,attributes, this);

        return instance;
    }

    /**
     * @throws IllegalConfigurationException if an AuthenticationProvider with the same name already exists
     */
    private void addAuthenticationProvider(AuthenticationProvider<?> authenticationProvider)
    {
        assert getTaskExecutor().isTaskExecutorThread();

        authenticationProvider.addChangeListener(this);
    }

    private GroupProvider<?> createGroupProvider(final Map<String, Object> attributes)
    {
        return runTask(new TaskExecutor.Task<GroupProvider<?>>()
        {
            @Override
            public GroupProvider<?> execute()
            {
                GroupProvider<?> groupProvider = createChild(GroupProvider.class, attributes);
                addGroupProvider(groupProvider);
                groupProvider.setDesiredState(State.INITIALISING, State.ACTIVE);
                return groupProvider;
            }
        });
    }

    private void addGroupProvider(GroupProvider<?> groupProvider)
    {
        groupProvider.addChangeListener(this);
    }

    private boolean deleteGroupProvider(GroupProvider groupProvider)
    {
        groupProvider.removeChangeListener(this);
        return true;
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
        keyStore.addChangeListener(this);
    }

    private boolean deleteKeyStore(KeyStore keyStore)
    {
        keyStore.removeChangeListener(this);
        return true;
    }

    private void addTrustStore(TrustStore trustStore)
    {
        trustStore.addChangeListener(this);
    }

    private boolean deleteTrustStore(TrustStore trustStore)
    {
        trustStore.removeChangeListener(this);
        return true;

    }

    @Override
    public Object getAttribute(String name)
    {
        if(STATE.equals(name))
        {
            return State.ACTIVE;
        }
        return super.getAttribute(name);
    }

    private boolean deletePort(State oldState, Port port)
    {
        port.removeChangeListener(this);

        // TODO - this seems suspicious, wouldn't it make more sense to not allow deletion from active
        // (must be stopped first) or something?

        if(oldState == State.ACTIVE)
        {
            //Record the originally used port numbers of previously-active ports being deleted, to ensure
            //when creating new ports we don't try to re-bind a port number that we are currently still using
            recordPreviouslyUsedPortNumberIfNecessary(port, port.getPort());
        }


        return port != null;
    }

    private boolean deleteAuthenticationProvider(AuthenticationProvider<?> authenticationProvider)
    {
        if(authenticationProvider != null)
        {
            authenticationProvider.removeChangeListener(this);
        }
        return true;
    }

    private void addVirtualHostNode(VirtualHostNode<?> virtualHostNode)
    {
        virtualHostNode.addChangeListener(this);
    }


    private boolean deleteVirtualHostNode(final VirtualHostNode virtualHostNode) throws AccessControlException, IllegalStateException
    {
        virtualHostNode.removeChangeListener(this);
        return true;
    }

    @Override
    public boolean setState(State currentState, State desiredState)
    {
        if (desiredState == State.ACTIVE)
        {
            initialiseStatisticsReporting();
            changeChildState(currentState, State.ACTIVE, false);
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

            changeChildState(currentState, State.STOPPED, true);
            return true;
        }
        return false;
    }

    private void changeChildState(final State currentState,
                                  final State desiredState,
                                  final boolean swallowException)
    {
        runTask(new TaskExecutor.VoidTask()
        {
            @Override
            public void execute()
            {
                for (Class<? extends ConfiguredObject> clazz : getModel().getChildTypes(getCategoryClass()))
                {
                    for (ConfiguredObject configuredObject : getChildren(clazz))
                    {
                        if (State.ACTIVE.equals(desiredState) && State.QUIESCED.equals(configuredObject.getState()))
                        {
                            if (LOGGER.isDebugEnabled())
                            {
                                LOGGER.debug(configuredObject + " cannot be activated as it is " + State.QUIESCED);
                            }
                            continue;
                        }
                        try
                        {
                            configuredObject.setDesiredState(currentState, desiredState);
                        }
                        catch (RuntimeException e)
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
        });

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
            else if(object instanceof VirtualHostNode)
            {
                childDeleted = deleteVirtualHostNode((VirtualHostNode)object);
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
        plugin.addChangeListener(this);
    }


    private Collection<ConfiguredObject<?>> getPlugins()
    {
        Collection children = getChildren(Plugin.class);
        return children;
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
    public VirtualHost<?,?,?> findVirtualHostByName(String name)
    {
        for (VirtualHostNode<?> virtualHostNode : getChildren(VirtualHostNode.class))
        {
            VirtualHost<?, ?, ?> virtualHost = virtualHostNode.getVirtualHost();
            if (virtualHost != null && virtualHost.getName().equals(name))
            {
                return virtualHost;
            }
        }
        return null;
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
            if (p instanceof AbstractPortWithAuthProvider && inetSocketAddress.getPort() == p.getPort())
            {
                provider = ((AbstractPortWithAuthProvider<?>) p).getAuthenticationProvider();
                break;
            }
        }
        return provider;
    }

    @Override
    public Collection<KeyStore<?>> getKeyStores()
    {
        Collection children = getChildren(KeyStore.class);
        return children;
    }

    @Override
    public Collection<TrustStore<?>> getTrustStores()
    {
        Collection children = getChildren(TrustStore.class);
        return children;
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
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
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
        Collection children = getChildren(AccessControlProvider.class);
        return children;
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

        for (VirtualHostNode<?> virtualHostNode : getChildren(VirtualHostNode.class))
        {
            VirtualHost<?, ?, ?> virtualHost = virtualHostNode.getVirtualHost();
            if (virtualHost instanceof VirtualHostImpl)
            {
                ((VirtualHostImpl) virtualHost).resetStatistics();
            }
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

                for (VirtualHostNode<?> virtualHostNode : getChildren(VirtualHostNode.class))
                {
                    VirtualHost<?, ?, ?> virtualHost = virtualHostNode.getVirtualHost();
                    if (virtualHost instanceof VirtualHostImpl)
                    {
                        VirtualHostImpl vhostImpl = (VirtualHostImpl) virtualHost;
                        String name = virtualHost.getName();
                        StatisticsCounter dataDelivered = vhostImpl.getDataDeliveryStatistics();
                        StatisticsCounter messagesDelivered = vhostImpl.getMessageDeliveryStatistics();
                        StatisticsCounter dataReceived = vhostImpl.getDataReceiptStatistics();
                        StatisticsCounter messagesReceived = vhostImpl.getMessageReceiptStatistics();
                        EventLogger logger = vhostImpl.getEventLogger();
                        logger.message(VirtualHostMessages.STATS_DATA(name,
                                                                      DELIVERED,
                                                                      dataDelivered.getPeak() / 1024.0,
                                                                      dataDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name,
                                                                      DELIVERED,
                                                                      messagesDelivered.getPeak(),
                                                                      messagesDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_DATA(name,
                                                                      RECEIVED,
                                                                      dataReceived.getPeak() / 1024.0,
                                                                      dataReceived.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name,
                                                                      RECEIVED,
                                                                      messagesReceived.getPeak(),
                                                                      messagesReceived.getTotal()));

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
