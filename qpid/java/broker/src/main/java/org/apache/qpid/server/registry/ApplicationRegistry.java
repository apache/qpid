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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.log4j.Logger;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.configuration.startup.AuthenticationProviderRecoverer;
import org.apache.qpid.server.configuration.startup.BrokerRecoverer;
import org.apache.qpid.server.configuration.startup.GroupProviderRecoverer;
import org.apache.qpid.server.configuration.startup.PortRecoverer;
import org.apache.qpid.server.configuration.startup.VirtualHostRecoverer;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;
import org.apache.qpid.server.logging.CompositeStartupMessageLogger;
import org.apache.qpid.server.logging.Log4jMessageLogger;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.AbstractActor;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.management.plugin.ManagementPlugin;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.adapter.AuthenticationProviderFactory;
import org.apache.qpid.server.model.adapter.PortFactory;
import org.apache.qpid.server.plugin.AuthenticationManagerFactory;
import org.apache.qpid.server.plugin.GroupManagerFactory;
import org.apache.qpid.server.plugin.ManagementFactory;
import org.apache.qpid.server.plugin.QpidServiceLoader;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;


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

    private final VirtualHostRegistry _virtualHostRegistry = new VirtualHostRegistry(this);

    private SecurityManager _securityManager;

    private volatile RootMessageLogger _rootMessageLogger;

    private CompositeStartupMessageLogger _startupMessageLogger;

    private Broker _broker;

    private Timer _reportingTimer;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private LogRecorder _logRecorder;

    private final List<ManagementPlugin> _managmentInstanceList = new ArrayList<ManagementPlugin>();

    private final BrokerOptions _brokerOptions;

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
            try
            {
                instance.close();
            }
            catch(Exception e1)
            {
                _logger.error("Failed to close uninitialized registry", e1);
            }

            //remove the Broker instance, then re-throw
            _instance.set(null);
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

    /** intended to be used when broker options are not applicable, eg from tests */
    public ApplicationRegistry(ServerConfiguration configuration)
    {
        this(configuration, new BrokerOptions());
    }

    public ApplicationRegistry(ServerConfiguration configuration, BrokerOptions brokerOptions)
    {
        _configuration = configuration;
        _brokerOptions = brokerOptions;
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
            _configuration.initialise();

            // We have already loaded the BrokerMessages class by this point so we
            // need to refresh the locale setting in case we had a different value in
            // the configuration.
            BrokerMessages.reload();

            // AR.initialise() sets and removes its own actor so we now need to set the actor
            // for the remainder of the startup, and the default actor if the stack is empty
            //CurrentActor.set(new BrokerActor(applicationRegistry.getCompositeStartupMessageLogger()));
            CurrentActor.setDefault(new BrokerActor(_rootMessageLogger));
            GenericActor.setDefaultMessageLogger(_rootMessageLogger);

            logStartupMessages(CurrentActor.get());

            _securityManager = new SecurityManager(_configuration.getConfig());

            createBroker();

            getVirtualHostRegistry().setDefaultVirtualHostName(_configuration.getDefaultVirtualHost());
            initialiseStatisticsReporting();

            // starting the broker
            _broker.setDesiredState(State.INITIALISING, State.ACTIVE);

            createAndStartManagementPlugins(_configuration);
            CurrentActor.get().message(BrokerMessages.READY());
        }
        finally
        {
            CurrentActor.remove();
        }

    }

    private void createBroker()
    {
        // XXX move all the code below into store and broker creator
        ConfigurationEntryStore store = new XMLConfigurationEntryStore(_configuration, _brokerOptions);

        Map<String, VirtualHostConfiguration> virtualHostConfigurations = new HashMap<String, VirtualHostConfiguration>();
        String[] vhNames = _configuration.getVirtualHostsNames();
        for (String name : vhNames)
        {
            virtualHostConfigurations.put(name, _configuration.getVirtualHostConfig(name));
        }

        // XXX : introduce RecovererGeneraor/Registry/Factory?

        PortFactory portFactory = new PortFactory(this);
        PortRecoverer portRecoverer = new PortRecoverer(portFactory);
        VirtualHostRecoverer virtualHostRecoverer = new VirtualHostRecoverer(getVirtualHostRegistry(),
                this, getSecurityManager(), virtualHostConfigurations);

        AuthenticationProviderFactory authenticationProviderFactory =
                new AuthenticationProviderFactory(new QpidServiceLoader<AuthenticationManagerFactory>());
        AuthenticationProviderRecoverer authenticationProviderRecoverer =
                new AuthenticationProviderRecoverer(authenticationProviderFactory);

        GroupProviderRecoverer groupProviderRecoverer = new GroupProviderRecoverer(new QpidServiceLoader<GroupManagerFactory>());

        BrokerRecoverer brokerRecoverer =  new BrokerRecoverer(
                portRecoverer,
                virtualHostRecoverer,
                authenticationProviderRecoverer,
                authenticationProviderFactory,
                portFactory,
                groupProviderRecoverer,
                this);

        _broker = brokerRecoverer.create(store.getRootEntry());
    }

    private void createAndStartManagementPlugins(ServerConfiguration configuration) throws Exception
    {
        QpidServiceLoader<ManagementFactory> factories = new QpidServiceLoader<ManagementFactory>();
        for (ManagementFactory managementFactory: factories.instancesOf(ManagementFactory.class))
        {
            ManagementPlugin managementPlugin = managementFactory.createInstance(configuration, _broker);
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

            closeAllManagementPlugins();

            if (_broker != null)
            {
                _broker.setDesiredState(_broker.getActualState(), State.STOPPED);
            }

            //Shutdown virtualhosts
            close(_virtualHostRegistry);

            CurrentActor.get().message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

        }
        finally
        {
            CurrentActor.remove();
        }
    }

    public ServerConfiguration getConfiguration()
    {
        return _configuration;
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
        AuthenticationProvider provider = _broker.getDefaultAuthenticationProvider();
        InetSocketAddress inetSocketAddress = (InetSocketAddress)localAddress;
        Collection<Port> ports = _broker.getPorts();
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
        VirtualHostImpl virtualHost = new VirtualHostImpl(this.getVirtualHostRegistry(), this, this.getSecurityManager(), vhostConfig);
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

    public LogRecorder getLogRecorder()
    {
        return _logRecorder;
    }
}
