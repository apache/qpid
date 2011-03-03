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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.database.ConfigurationFilePrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.transport.network.NetworkTransport;

/**
 * An abstract application registry.
 *
 * This class provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p>
 * Subclasses should handle the construction of the <em>registered objects</em>
 * such as the exchange registry.
 *
 * @see ConfigurationFileApplicationRegistry
 * @see TestApplicationRegistry
 */
public abstract class ApplicationRegistry implements IApplicationRegistry
{
    protected static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static IApplicationRegistry _instance = null;

    protected final ServerConfiguration _configuration;

    public static final int DEFAULT_INSTANCE = 1;

    protected final Map<Integer, NetworkTransport> _transports = new HashMap<Integer, NetworkTransport>();

    protected ManagedObjectRegistry _managedObjectRegistry;

    protected AuthenticationManager _authenticationManager;

    protected VirtualHostRegistry _virtualHostRegistry;

    protected SecurityManager _securityManager;

    protected PrincipalDatabaseManager _databaseManager;

    protected PluginManager _pluginManager;

    protected ConfigurationManager _configurationManager;

    protected RootMessageLogger _rootMessageLogger;

    protected CompositeStartupMessageLogger _startupMessageLogger;

    protected UUID _brokerId = UUID.randomUUID();

    protected QMFService _qmfService;

    private BrokerConfig _broker;

    private ConfigStore _configStore;

    protected String _registryName;

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
        _logger.info("Initialising Application Registry(" + instance + ")");
        _instance = instance;

        final ConfigStore store = ConfigStore.newInstance();
        store.setRoot(new SystemConfigImpl(store));
        instance.setConfigStore(store);

        BrokerConfig broker = new BrokerConfigAdapter(instance);

        SystemConfig system = (SystemConfig) store.getRoot();
        system.setBrokerConfig(broker);
        instance.setBrokerConfig(broker);

        try
        {
            instance.initialise();
        }
        catch (Exception e)
        {
            try
            {
                remove();
            }
            finally
            {
                throw e;
            }
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
        return _instance !=  null;
    }

    /** Method to cleanly shutdown the default registry running in this JVM */
    public static void remove()
    {
        try
        {
            if (isConfigured())
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Shutting down ApplicationRegistry (" + _instance + ")");
                }
                _instance.setBrokerConfig(null);
                _instance.close();
            }
        }
        catch (Exception e)
        {
            _logger.error("Error shutting down ApplicationRegistry (" + _instance + ") " + e.getMessage(), e);
        }
        finally
        {
            _instance = null;
        }
    }

    protected ApplicationRegistry(ServerConfiguration configuration)
    {
        _configuration = configuration;
    }

    public void configure() throws ConfigurationException
    {
        _configurationManager = new ConfigurationManager();

        try
        {
            _pluginManager = new PluginManager(_configuration.getPluginDirectory(), _configuration.getCacheDirectory());
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
        _registryName = _instance.getBrokerId().toString();

        //Create the composite (log4j+SystemOut MessageLogger to be used during startup
        RootMessageLogger[] messageLoggers = {new SystemOutMessageLogger(), _rootMessageLogger};
        _startupMessageLogger = new CompositeStartupMessageLogger(messageLoggers);
        
        CurrentActor.set(new BrokerActor(_startupMessageLogger));

        try
        {
            configure();

            _qmfService = new QMFService(getConfigStore(), this);

            CurrentActor.get().message(BrokerMessages.STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

            initialiseManagedObjectRegistry();

            _virtualHostRegistry = new VirtualHostRegistry(this);

            _securityManager = new SecurityManager(_configuration, _pluginManager);

            createDatabaseManager(_configuration);

            _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

            _databaseManager.initialiseManagement(_configuration);

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
        }
        finally
        {
            // Startup complete, so pop the current actor
            CurrentActor.remove();
        }
    }

    protected void createDatabaseManager(ServerConfiguration configuration) throws Exception
    {
        _databaseManager = new ConfigurationFilePrincipalDatabaseManager(_configuration);
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

    public static IApplicationRegistry getInstance()
    {
        if (!isConfigured())
        {
            throw new IllegalStateException("Application Registry not created");
        }
        else
        {
            return _instance;
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

        //Stop incoming connections
        unbind();

        //Shutdown virtualhosts
        close(_virtualHostRegistry);

//      close(_accessManager);
//
//      close(_databaseManager);

        close(_authenticationManager);

        close(_managedObjectRegistry);

        close(_qmfService);

        close(_pluginManager);

        CurrentActor.get().message(BrokerMessages.STOPPED());
    }

    private void unbind()
    {
        synchronized (_transports)
        {
            for (Integer port: _transports.keySet())
            {
                NetworkTransport transport = _transports.get(port);
                try
                {
                    transport.close();
                }
                catch (Throwable e)
                {
                    _logger.error("Unable to close network driver due to:" + e.getMessage());
                }
                CurrentActor.get().message(BrokerMessages.SHUTTING_DOWN(transport.getAddress().toString(), port));
            }
        }
    }

    public ServerConfiguration getConfiguration()
    {
        return _configuration;
    }

    public void registerTransport(int port, NetworkTransport transport)
    {
        synchronized (_transports)
        {
            _transports.put(port, transport);
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

    public PrincipalDatabaseManager getDatabaseManager()
    {
        return _databaseManager;
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

    public BrokerConfig getBrokerConfig()
    {
        return _broker;
    }

    public void setBrokerConfig(final BrokerConfig broker)
    {
        _broker = broker;
    }

    public VirtualHost createVirtualHost(final VirtualHostConfiguration vhostConfig) throws Exception
    {
        VirtualHostImpl virtualHost = new VirtualHostImpl(this, vhostConfig);
        _virtualHostRegistry.registerVirtualHost(virtualHost);
        getBrokerConfig().addVirtualHost(virtualHost);
        return virtualHost;
    }
}
