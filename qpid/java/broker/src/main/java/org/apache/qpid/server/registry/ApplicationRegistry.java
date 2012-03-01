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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.qmf.QMFService;
import org.apache.qpid.server.configuration.BrokerConfig;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfigurationManager;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.configuration.SystemConfig;
import org.apache.qpid.server.configuration.SystemConfigImpl;
import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.logging.CompositeStartupMessageLogger;
import org.apache.qpid.server.logging.Log4jMessageLogger;
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.AbstractActor;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.Plugin;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SecurityManager.SecurityConfiguration;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManagerPluginFactory;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.osgi.framework.BundleContext;


/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public abstract class ApplicationRegistry implements IApplicationRegistry
{
    protected static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static AtomicReference<IApplicationRegistry> _instance = new AtomicReference<IApplicationRegistry>(null);

    protected final ServerConfiguration _configuration;

    protected final Map<InetSocketAddress, QpidAcceptor> _acceptors = new HashMap<InetSocketAddress, QpidAcceptor>();

    protected ManagedObjectRegistry _managedObjectRegistry;

    protected AuthenticationManager _authenticationManager;

    protected VirtualHostRegistry _virtualHostRegistry;

    protected SecurityManager _securityManager;

    protected PluginManager _pluginManager;

    protected ConfigurationManager _configurationManager;

    protected RootMessageLogger _rootMessageLogger;

    protected CompositeStartupMessageLogger _startupMessageLogger;

    protected UUID _brokerId = UUID.randomUUID();

    protected QMFService _qmfService;

    private BrokerConfig _broker;

    private ConfigStore _configStore;
    
    private Timer _reportingTimer;
    private boolean _statisticsEnabled = false;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private BundleContext _bundleContext;

    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownService()));
    }

    private static class ShutdownService implements Runnable
    {
        public void run()
        {
            remove();
        }
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


        final ConfigStore store = ConfigStore.newInstance();
        store.setRoot(new SystemConfigImpl(store));
        instance.setConfigStore(store);

        BrokerConfig broker = new BrokerConfigAdapter(instance);

        SystemConfig system = (SystemConfig) store.getRoot();
        system.addBroker(broker);
        instance.setBroker(broker);

        try
        {
            instance.initialise();
        }
        catch (Exception e)
        {
            _instance.set(null);

            //remove the Broker instance, then re-throw
            try
            {
                system.removeBroker(broker);
            }
            catch(Throwable t)
            {
                //ignore
            }

            throw e;
        }
    }

    public ConfigStore getConfigStore()
    {
        return _configStore;
    }

    public void setConfigStore(final ConfigStore configStore)
    {
        _configStore = configStore;
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
                instance.getBroker().getSystem().removeBroker(instance.getBroker());
            }
        }
        catch (Exception e)
        {
            _logger.error("Error shutting down Application Registry(" + instance + "): " + e, e);
        }
    }

    protected ApplicationRegistry(ServerConfiguration configuration)
    {
        this(configuration, null);
    }

    protected ApplicationRegistry(ServerConfiguration configuration, BundleContext bundleContext)
    {
        _configuration = configuration;
        _bundleContext = bundleContext;
    }

    public void configure() throws ConfigurationException
    {
        _configurationManager = new ConfigurationManager();

        try
        {
            _pluginManager = new PluginManager(_configuration.getPluginDirectory(), _configuration.getCacheDirectory(), _bundleContext);
        }
        catch (Exception e)
        {
            throw new ConfigurationException(e);
        }

        _configuration.initialise();
    }

    public void initialise() throws Exception
    {
        //Create the RootLogger to be used during broker operation
        _rootMessageLogger = new Log4jMessageLogger(_configuration);

        //Create the composite (log4j+SystemOut MessageLogger to be used during startup
        RootMessageLogger[] messageLoggers = {new SystemOutMessageLogger(), _rootMessageLogger};
        _startupMessageLogger = new CompositeStartupMessageLogger(messageLoggers);
        
        CurrentActor.set(new BrokerActor(_startupMessageLogger));

        try
        {
            initialiseManagedObjectRegistry();

            configure();

            _qmfService = new QMFService(getConfigStore(), this);

            CurrentActor.get().message(BrokerMessages.STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

            _virtualHostRegistry = new VirtualHostRegistry(this);

            _securityManager = new SecurityManager(_configuration, _pluginManager);

            _authenticationManager = createAuthenticationManager();

            _managedObjectRegistry.start();
        }
        finally
        {
            CurrentActor.remove();
        }

        CurrentActor.set(new BrokerActor(_rootMessageLogger));
        try
        {
            initialiseVirtualHosts();
            initialiseStatistics();
            initialiseStatisticsReporting();
        }
        finally
        {
            // Startup complete, so pop the current actor
            CurrentActor.remove();
        }
    }

    /**
     * Iterates across all discovered authentication manager factories, offering the security configuration to each.
     * Expects <b>exactly</b> one authentication manager to configure and initialise itself.
     * 
     * It is an error to configure more than one authentication manager, or to configure none.
     *
     * @return authentication manager
     * @throws ConfigurationException
     */
    protected AuthenticationManager createAuthenticationManager() throws ConfigurationException
    {
        final SecurityConfiguration securityConfiguration = _configuration.getConfiguration(SecurityConfiguration.class.getName());
        final Collection<AuthenticationManagerPluginFactory<? extends Plugin>> factories = _pluginManager.getAuthenticationManagerPlugins().values();
        
        if (factories.size() == 0)
        {
            throw new ConfigurationException("No authentication manager factory plugins found.  Check the desired authentication" +
                    "manager plugin has been placed in the plugins directory.");
        }
        
        AuthenticationManager authMgr = null;
        
        for (final Iterator<AuthenticationManagerPluginFactory<? extends Plugin>> iterator = factories.iterator(); iterator.hasNext();)
        {
            final AuthenticationManagerPluginFactory<? extends Plugin> factory = (AuthenticationManagerPluginFactory<? extends Plugin>) iterator.next();
            final AuthenticationManager tmp = factory.newInstance(securityConfiguration);
            if (tmp != null)
            {
                if (authMgr != null)
                {
                    throw new ConfigurationException("Cannot configure more than one authentication manager."
                            + " Both " + tmp.getClass() + " and " + authMgr.getClass() + " are configured."
                            + " Remove configuration for one of the authentication manager, or remove the plugin JAR"
                            + " from the classpath.");
                }
                authMgr = tmp;
            }
        }

        if (authMgr == null)
        {
            throw new ConfigurationException("No authentication managers configured within the configure file.");
        }
        
        return authMgr;
    }

    protected void initialiseVirtualHosts() throws Exception
    {
        for (String name : _configuration.getVirtualHosts())
        {
            createVirtualHost(_configuration.getVirtualHostConfig(name));
        }
        getVirtualHostRegistry().setDefaultVirtualHostName(_configuration.getDefaultVirtualHost());
    }

    protected void initialiseManagedObjectRegistry() throws AMQException
    {
        _managedObjectRegistry = new NoopManagedObjectRegistry();
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
            
            class StatisticsReportingTask extends TimerTask
            {
                private final int DELIVERED = 0;
                private final int RECEIVED = 1;
                
                public void run()
                {
                    CurrentActor.set(new AbstractActor(ApplicationRegistry.getInstance().getRootMessageLogger()) {
                        public String getLogMessage()
                        {
                            return "[" + Thread.currentThread().getName() + "] ";
                        }
                    });
                    
                    if (broker)
                    {
                        CurrentActor.get().message(BrokerMessages.STATS_DATA(DELIVERED, _dataDelivered.getPeak() / 1024.0, _dataDelivered.getTotal()));
                        CurrentActor.get().message(BrokerMessages.STATS_MSGS(DELIVERED, _messagesDelivered.getPeak(), _messagesDelivered.getTotal()));
                        CurrentActor.get().message(BrokerMessages.STATS_DATA(RECEIVED, _dataReceived.getPeak() / 1024.0, _dataReceived.getTotal()));
                        CurrentActor.get().message(BrokerMessages.STATS_MSGS(RECEIVED, _messagesReceived.getPeak(), _messagesReceived.getTotal()));
                    }
                    
                    if (virtualhost)
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
                    
                    if (reset)
                    {
                        resetStatistics();
                    }

                    CurrentActor.remove();
                }
            }

            _reportingTimer.scheduleAtFixedRate(new StatisticsReportingTask(),
                                                report / 2,
                                                report);
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
        
        //Stop Statistics Reporting
        if (_reportingTimer != null)
        {
            _reportingTimer.cancel();
        }

        //Stop incoming connections
        unbind();

        //Shutdown virtualhosts
        close(_virtualHostRegistry);

        close(_authenticationManager);

        close(_qmfService);

        close(_pluginManager);

        close(_managedObjectRegistry);

        CurrentActor.get().message(BrokerMessages.STOPPED());
    }

    private void unbind()
    {
        synchronized (_acceptors)
        {
            for (InetSocketAddress bindAddress : _acceptors.keySet())
            {
                QpidAcceptor acceptor = _acceptors.get(bindAddress);

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
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public SecurityManager getSecurityManager()
    {
        return _securityManager;
    }

    public ManagedObjectRegistry getManagedObjectRegistry()
    {
        return _managedObjectRegistry;
    }

    public AuthenticationManager getAuthenticationManager()
    {
        return _authenticationManager;
    }

    public PluginManager getPluginManager()
    {
        return _pluginManager;
    }

    public ConfigurationManager getConfigurationManager()
    {
        return _configurationManager;
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
        return _brokerId;
    }

    public QMFService getQMFService()
    {
        return _qmfService;
    }

    public BrokerConfig getBroker()
    {
        return _broker;
    }

    public void setBroker(final BrokerConfig broker)
    {
        _broker = broker;
    }

    public VirtualHost createVirtualHost(final VirtualHostConfiguration vhostConfig) throws Exception
    {
        VirtualHostImpl virtualHost = new VirtualHostImpl(this, vhostConfig, null);
        _virtualHostRegistry.registerVirtualHost(virtualHost);
        getBroker().addVirtualHost(virtualHost);
        return virtualHost;
    }
    
    public void registerMessageDelivered(long messageSize)
    {
        if (isStatisticsEnabled())
        {
            _messagesDelivered.registerEvent(1L);
            _dataDelivered.registerEvent(messageSize);
        }
    }
    
    public void registerMessageReceived(long messageSize, long timestamp)
    {
        if (isStatisticsEnabled())
        {
            _messagesReceived.registerEvent(1L, timestamp);
            _dataReceived.registerEvent(messageSize, timestamp);
        }
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
        setStatisticsEnabled(!StatisticsCounter.DISABLE_STATISTICS &&
                getConfiguration().isStatisticsGenerationBrokerEnabled());
        
        _messagesDelivered = new StatisticsCounter("messages-delivered");
        _dataDelivered = new StatisticsCounter("bytes-delivered");
        _messagesReceived = new StatisticsCounter("messages-received");
        _dataReceived = new StatisticsCounter("bytes-received");
    }

    public boolean isStatisticsEnabled()
    {
        return _statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean enabled)
    {
        _statisticsEnabled = enabled;
    }
}
