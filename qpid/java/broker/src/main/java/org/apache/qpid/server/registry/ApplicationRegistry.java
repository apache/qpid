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
package org.apache.qpid.server.registry;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.logging.*;

import org.apache.qpid.common.Closeable;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.logging.actors.AbstractActor;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.management.plugin.ManagementPlugin;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.adapter.BrokerAdapter;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.ManagementFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManagerRegistry;
import org.apache.qpid.server.security.auth.manager.IAuthenticationManagerRegistry;
import org.apache.qpid.server.security.group.GroupManager;
import org.apache.qpid.server.security.group.GroupPrincipalAccessor;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;


/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public class ApplicationRegistry implements IApplicationRegistry
{
    private static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static AtomicReference<IApplicationRegistry> _instance = new AtomicReference<IApplicationRegistry>(null);

    private final ServerConfiguration _configuration;

    private final Map<InetSocketAddress, QpidAcceptor> _acceptors =
            Collections.synchronizedMap(new HashMap<InetSocketAddress, QpidAcceptor>());

    private IAuthenticationManagerRegistry _authenticationManagerRegistry;

    private final VirtualHostRegistry _virtualHostRegistry = new VirtualHostRegistry(this);

    private SecurityManager _securityManager;

    private volatile RootMessageLogger _rootMessageLogger;

    private CompositeStartupMessageLogger _startupMessageLogger;

    private Broker _broker;

    private Timer _reportingTimer;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private final List<PortBindingListener> _portBindingListeners = new ArrayList<PortBindingListener>();

    private int _httpManagementPort = -1, _httpsManagementPort = -1;

    private LogRecorder _logRecorder;

    private List<IAuthenticationManagerRegistry.RegistryChangeListener> _authManagerChangeListeners =
            new ArrayList<IAuthenticationManagerRegistry.RegistryChangeListener>();

    private List<GroupManagerChangeListener> _groupManagerChangeListeners =
            new ArrayList<GroupManagerChangeListener>();

    private List<GroupManager> _groupManagerList = new ArrayList<GroupManager>();

    private QpidServiceLoader<GroupManagerFactory> _groupManagerServiceLoader = new QpidServiceLoader<GroupManagerFactory>();

    private final List<ManagementPlugin> _managmentInstanceList = new ArrayList<ManagementPlugin>();

    public Map<InetSocketAddress, QpidAcceptor> getAcceptors()
    {
        synchronized (_acceptors)
        {
            return new HashMap<InetSocketAddress, QpidAcceptor>(_acceptors);
        }
    }

    protected void setSecurityManager(SecurityManager securityManager)
    {
        _securityManager = securityManager;
    }

    protected void setRootMessageLogger(RootMessageLogger rootMessageLogger)
    {
        _rootMessageLogger = rootMessageLogger;
    }

    protected CompositeStartupMessageLogger getStartupMessageLogger()
    {
        return _startupMessageLogger;
    }

    protected void setStartupMessageLogger(CompositeStartupMessageLogger startupMessageLogger)
    {
        _startupMessageLogger = startupMessageLogger;
    }

    public static void initialise(IApplicationRegistry instance) throws Exception
    {
        if(instance == null)
        {
            throw new IllegalArgumentException("ApplicationRegistry instance must not be null");
        }

        if(!_instance.compareAndSet(null, instance))
        {
            throw new IllegalStateException("An ApplicationRegistry is already initialised");
        }

        _logger.info("Initialising Application Registry(" + instance + ")");


        try
        {
            instance.initialise();
        }
        catch (Exception e)
        {
            _instance.set(null);

            //remove the Broker instance, then re-throw

            throw e;
        }
    }

    public static boolean isConfigured()
    {
        return _instance.get() != null;
    }

    public static void remove()
    {
        IApplicationRegistry instance = _instance.getAndSet(null);
        try
        {
            if (instance != null)
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Shutting down ApplicationRegistry(" + instance + ")");
                }
                instance.close();
            }
        }
        catch (Exception e)
        {
            _logger.error("Error shutting down Application Registry(" + instance + "): " + e, e);
        }
    }

    public ApplicationRegistry(ServerConfiguration configuration)
    {
        _configuration = configuration;
    }

    public void initialise() throws Exception
    {
        _logRecorder = new LogRecorder();
        //Create the RootLogger to be used during broker operation
        _rootMessageLogger = new Log4jMessageLogger(_configuration);

        //Create the composite (log4j+SystemOut MessageLogger to be used during startup
        RootMessageLogger[] messageLoggers = {new SystemOutMessageLogger(), _rootMessageLogger};
        _startupMessageLogger = new CompositeStartupMessageLogger(messageLoggers);

        BrokerActor actor = new BrokerActor(_startupMessageLogger);
        CurrentActor.setDefault(actor);
        CurrentActor.set(actor);

        try
        {
            initialiseStatistics();

            if(_configuration.getHTTPManagementEnabled())
            {
                _httpManagementPort = _configuration.getHTTPManagementPort();
            }
            if (_configuration.getHTTPSManagementEnabled())
            {
                _httpsManagementPort = _configuration.getHTTPSManagementPort();
            }

            _broker = new BrokerAdapter(this);

            _configuration.initialise();
            logStartupMessages(CurrentActor.get());

            // Management needs to be registered so that JMXManagement.childAdded can create optional management objects
            createAndStartManagementPlugins(_configuration, _broker);

            _securityManager = new SecurityManager(_configuration.getConfig());

            _groupManagerList = createGroupManagers(_configuration);

            _authenticationManagerRegistry = createAuthenticationManagerRegistry(_configuration, new GroupPrincipalAccessor(_groupManagerList));

            if(!_authManagerChangeListeners.isEmpty())
            {
                for(IAuthenticationManagerRegistry.RegistryChangeListener listener : _authManagerChangeListeners)
                {

                    _authenticationManagerRegistry.addRegistryChangeListener(listener);
                    for(AuthenticationManager authMgr : _authenticationManagerRegistry.getAvailableAuthenticationManagers().values())
                    {
                        listener.authenticationManagerRegistered(authMgr);
                    }
                }
                _authManagerChangeListeners.clear();
            }
        }
        finally
        {
            CurrentActor.remove();
        }

        CurrentActor.set(new BrokerActor(_rootMessageLogger));
        try
        {
            initialiseVirtualHosts();
            initialiseStatisticsReporting();
        }
        finally
        {
            // Startup complete, so pop the current actor
            CurrentActor.remove();
        }
    }

    private void createAndStartManagementPlugins(ServerConfiguration configuration, Broker broker) throws Exception
    {
        QpidServiceLoader<ManagementFactory> factories = new QpidServiceLoader<ManagementFactory>();
        for (ManagementFactory managementFactory: factories.instancesOf(ManagementFactory.class))
        {
            ManagementPlugin managementPlugin = managementFactory.createInstance(configuration, broker);
            if(managementPlugin != null)
            {
                try
                {
                    managementPlugin.start();
                }
                catch(Exception e)
                {
                    _logger.error("Management plugin " + managementPlugin.getClass().getSimpleName() + " failed to start normally, stopping it now", e);
                    managementPlugin.stop();
                    throw e;
                }

                _managmentInstanceList.add(managementPlugin);
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Configured " + _managmentInstanceList.size() + " management instance(s)");
        }
    }

    private void closeAllManagementPlugins()
    {
        for (ManagementPlugin managementPlugin : _managmentInstanceList)
        {
            try
            {
                managementPlugin.stop();
            }
            catch (Exception e)
            {
                _logger.error("Exception thrown whilst stopping management plugin " + managementPlugin.getClass().getSimpleName(), e);
            }
        }
    }

    private List<GroupManager> createGroupManagers(ServerConfiguration configuration) throws ConfigurationException
    {
        List<GroupManager> groupManagerList = new ArrayList<GroupManager>();
        Configuration securityConfig = configuration.getConfig().subset("security");

        for(GroupManagerFactory factory : _groupManagerServiceLoader.instancesOf(GroupManagerFactory.class))
        {
            GroupManager groupManager = factory.createInstance(securityConfig);
            if (groupManager != null)
            {
                groupManagerList.add(groupManager);
                for(GroupManagerChangeListener listener : _groupManagerChangeListeners)
                {
                    listener.groupManagerRegistered(groupManager);
                }
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Configured " + groupManagerList.size() + " group manager(s)");
        }
        return groupManagerList;
    }

    protected IAuthenticationManagerRegistry createAuthenticationManagerRegistry(ServerConfiguration configuration, GroupPrincipalAccessor groupPrincipalAccessor)
            throws ConfigurationException
    {
        return new AuthenticationManagerRegistry(configuration, groupPrincipalAccessor);
    }

    protected void initialiseVirtualHosts() throws Exception
    {
        for (String name : _configuration.getVirtualHosts())
        {
            createVirtualHost(_configuration.getVirtualHostConfig(name));
        }
        getVirtualHostRegistry().setDefaultVirtualHostName(_configuration.getDefaultVirtualHost());
    }

    public void initialiseStatisticsReporting()
    {
        long report = _configuration.getStatisticsReportingPeriod() * 1000; // convert to ms
        final boolean broker = _configuration.isStatisticsGenerationBrokerEnabled();
        final boolean virtualhost = _configuration.isStatisticsGenerationVirtualhostsEnabled();
        final boolean reset = _configuration.isStatisticsReportResetEnabled();

        /* add a timer task to report statistics if generation is enabled for broker or virtualhosts */
        if (report > 0L && (broker || virtualhost))
        {
            _reportingTimer = new Timer("Statistics-Reporting", true);



            _reportingTimer.scheduleAtFixedRate(new StatisticsReportingTask(broker, virtualhost, reset),
                                                report / 2,
                                                report);
        }
    }

    private class StatisticsReportingTask extends TimerTask
    {
        private final int DELIVERED = 0;
        private final int RECEIVED = 1;

        private boolean _broker;
        private boolean _virtualhost;
        private boolean _reset;


        public StatisticsReportingTask(boolean broker, boolean virtualhost, boolean reset)
        {
            _broker = broker;
            _virtualhost = virtualhost;
            _reset = reset;
        }

        public void run()
        {
            CurrentActor.set(new AbstractActor(ApplicationRegistry.getInstance().getRootMessageLogger()) {
                public String getLogMessage()
                {
                    return "[" + Thread.currentThread().getName() + "] ";
                }
            });

            if (_broker)
            {
                CurrentActor.get().message(BrokerMessages.STATS_DATA(DELIVERED, _dataDelivered.getPeak() / 1024.0, _dataDelivered.getTotal()));
                CurrentActor.get().message(BrokerMessages.STATS_MSGS(DELIVERED, _messagesDelivered.getPeak(), _messagesDelivered.getTotal()));
                CurrentActor.get().message(BrokerMessages.STATS_DATA(RECEIVED, _dataReceived.getPeak() / 1024.0, _dataReceived.getTotal()));
                CurrentActor.get().message(BrokerMessages.STATS_MSGS(RECEIVED, _messagesReceived.getPeak(), _messagesReceived.getTotal()));
            }

            if (_virtualhost)
            {
                for (VirtualHost vhost : getVirtualHostRegistry().getVirtualHosts())
                {
                    String name = vhost.getName();
                    StatisticsCounter dataDelivered = vhost.getDataDeliveryStatistics();
                    StatisticsCounter messagesDelivered = vhost.getMessageDeliveryStatistics();
                    StatisticsCounter dataReceived = vhost.getDataReceiptStatistics();
                    StatisticsCounter messagesReceived = vhost.getMessageReceiptStatistics();

                    CurrentActor.get().message(VirtualHostMessages.STATS_DATA(name, DELIVERED, dataDelivered.getPeak() / 1024.0, dataDelivered.getTotal()));
                    CurrentActor.get().message(VirtualHostMessages.STATS_MSGS(name, DELIVERED, messagesDelivered.getPeak(), messagesDelivered.getTotal()));
                    CurrentActor.get().message(VirtualHostMessages.STATS_DATA(name, RECEIVED, dataReceived.getPeak() / 1024.0, dataReceived.getTotal()));
                    CurrentActor.get().message(VirtualHostMessages.STATS_MSGS(name, RECEIVED, messagesReceived.getPeak(), messagesReceived.getTotal()));
                }
            }

            if (_reset)
            {
                resetStatistics();
            }

            CurrentActor.remove();
        }
    }

    /**
     * Get the ApplicationRegistry
     * @return the IApplicationRegistry instance
     * @throws IllegalStateException if no registry instance has been initialised.
     */
    public static IApplicationRegistry getInstance() throws IllegalStateException
    {
        IApplicationRegistry iApplicationRegistry = _instance.get();
        if (iApplicationRegistry == null)
        {
            throw new IllegalStateException("No ApplicationRegistry has been initialised");
        }
        else
        {
            return iApplicationRegistry;
        }
    }

    /**
     * Close non-null Closeable items and log any errors
     * @param close
     */
    private void close(Closeable close)
    {
        try
        {
            if (close != null)
            {
                close.close();
            }
        }
        catch (Throwable e)
        {
            _logger.error("Error thrown whilst closing " + close.getClass().getSimpleName(), e);
        }
    }

    public void close()
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Shutting down ApplicationRegistry:" + this);
        }

        //Set the Actor for Broker Shutdown
        CurrentActor.set(new BrokerActor(getRootMessageLogger()));
        try
        {
            //Stop Statistics Reporting
            if (_reportingTimer != null)
            {
                _reportingTimer.cancel();
            }

            //Stop incoming connections
            unbind();

            //Shutdown virtualhosts
            close(_virtualHostRegistry);

            close(_authenticationManagerRegistry);

            CurrentActor.get().message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

            closeAllManagementPlugins();
        }
        finally
        {
            CurrentActor.remove();
        }
    }

    private void unbind()
    {
        List<QpidAcceptor> removedAcceptors = new ArrayList<QpidAcceptor>();
        synchronized (_acceptors)
        {
            for (InetSocketAddress bindAddress : _acceptors.keySet())
            {
                QpidAcceptor acceptor = _acceptors.get(bindAddress);

                removedAcceptors.add(acceptor);
                try
                {
                    acceptor.getNetworkTransport().close();
                }
                catch (Throwable e)
                {
                    _logger.error("Unable to close network driver due to:" + e.getMessage());
                }

               CurrentActor.get().message(BrokerMessages.SHUTTING_DOWN(acceptor.toString(), bindAddress.getPort()));
            }
        }
        synchronized (_portBindingListeners)
        {
            for(QpidAcceptor acceptor : removedAcceptors)
            {
                for(PortBindingListener listener : _portBindingListeners)
                {
                    listener.unbound(acceptor);
                }
            }
        }
    }

    public ServerConfiguration getConfiguration()
    {
        return _configuration;
    }

    public void addAcceptor(InetSocketAddress bindAddress, QpidAcceptor acceptor)
    {
        synchronized (_acceptors)
        {
            _acceptors.put(bindAddress, acceptor);
        }
        synchronized (_portBindingListeners)
        {
            for(PortBindingListener listener : _portBindingListeners)
            {
                listener.bound(acceptor, bindAddress);
            }
        }
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public SecurityManager getSecurityManager()
    {
        return _securityManager;
    }

    @Override
    public SubjectCreator getSubjectCreator(SocketAddress localAddress)
    {
        return _authenticationManagerRegistry.getSubjectCreator(localAddress);
    }

    @Override
    public IAuthenticationManagerRegistry getAuthenticationManagerRegistry()
    {
        return _authenticationManagerRegistry;
    }

    @Override
    public List<GroupManager> getGroupManagers()
    {
        return _groupManagerList;
    }

    public RootMessageLogger getRootMessageLogger()
    {
        return _rootMessageLogger;
    }

    public RootMessageLogger getCompositeStartupMessageLogger()
    {
        return _startupMessageLogger;
    }

    public UUID getBrokerId()
    {
        return getBroker().getId();
    }

    public VirtualHost createVirtualHost(final VirtualHostConfiguration vhostConfig) throws Exception
    {
        VirtualHostImpl virtualHost = new VirtualHostImpl(this, vhostConfig);
        _virtualHostRegistry.registerVirtualHost(virtualHost);
        return virtualHost;
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

        for (VirtualHost vhost : _virtualHostRegistry.getVirtualHosts())
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

    private void logStartupMessages(LogActor logActor)
    {
        logActor.message(BrokerMessages.STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

        logActor.message(BrokerMessages.PLATFORM(System.getProperty("java.vendor"),
                                                 System.getProperty("java.runtime.version", System.getProperty("java.version")),
                                                 System.getProperty("os.name"),
                                                 System.getProperty("os.version"),
                                                 System.getProperty("os.arch")));

        logActor.message(BrokerMessages.MAX_MEMORY(Runtime.getRuntime().maxMemory()));
    }

    public Broker getBroker()
    {
        return _broker;
    }

    @Override
    public void addPortBindingListener(PortBindingListener listener)
    {
        synchronized (_portBindingListeners)
        {
            _portBindingListeners.add(listener);
        }
    }


    @Override
    public boolean useHTTPManagement()
    {
        return _httpManagementPort != -1;
    }

    @Override
    public int getHTTPManagementPort()
    {
        return _httpManagementPort;
    }

    @Override
    public boolean useHTTPSManagement()
    {
        return _httpsManagementPort != -1;
    }

    @Override
    public int getHTTPSManagementPort()
    {
        return _httpsManagementPort;
    }

    public LogRecorder getLogRecorder()
    {
        return _logRecorder;
    }

    @Override
    public void addAuthenticationManagerRegistryChangeListener(IAuthenticationManagerRegistry.RegistryChangeListener registryChangeListener)
    {
        if(_authenticationManagerRegistry == null)
        {
            _authManagerChangeListeners.add(registryChangeListener);
        }
        else
        {
            _authenticationManagerRegistry.addRegistryChangeListener(registryChangeListener);
        }
    }

    @Override
    public void addGroupManagerChangeListener(GroupManagerChangeListener groupManagerChangeListener)
    {
        _groupManagerChangeListeners.add(groupManagerChangeListener);
    }
}
