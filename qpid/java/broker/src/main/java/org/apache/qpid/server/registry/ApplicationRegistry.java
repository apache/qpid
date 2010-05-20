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
import org.apache.qpid.server.logging.RootMessageLogger;
import org.apache.qpid.server.logging.RootMessageLoggerImpl;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.rawloggers.Log4jMessageLogger;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.management.NoopManagedObjectRegistry;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.security.auth.database.ConfigurationFilePrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public abstract class ApplicationRegistry implements IApplicationRegistry
{
    protected static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static Map<Integer, IApplicationRegistry> _instanceMap = new HashMap<Integer, IApplicationRegistry>();

    private final Map<Class<?>, Object> _configuredObjects = new HashMap<Class<?>, Object>();

    protected final ServerConfiguration _configuration;

    public static final int DEFAULT_INSTANCE = 1;
    public static final String DEFAULT_APPLICATION_REGISTRY = "org.apache.qpid.server.util.NullApplicationRegistry";
    public static String _APPLICATION_REGISTRY = DEFAULT_APPLICATION_REGISTRY;

    protected final Map<InetSocketAddress, QpidAcceptor> _acceptors = new HashMap<InetSocketAddress, QpidAcceptor>();

    protected ManagedObjectRegistry _managedObjectRegistry;

    protected AuthenticationManager _authenticationManager;

    protected VirtualHostRegistry _virtualHostRegistry;

    protected ACLManager _accessManager;

    protected PrincipalDatabaseManager _databaseManager;

    protected PluginManager _pluginManager;

    protected ConfigurationManager _configurationManager;

    protected RootMessageLogger _rootMessageLogger;

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
            removeAll();
        }
    }

    public static void initialise(IApplicationRegistry instance) throws Exception
    {
        initialise(instance, DEFAULT_INSTANCE);
    }

    public static void initialise(IApplicationRegistry instance, int instanceID) throws Exception
    {
        if (instance != null)
        {
            _logger.info("Initialising Application Registry(" + instance + "):" + instanceID);
            _instanceMap.put(instanceID, instance);

            final ConfigStore store = ConfigStore.newInstance();
            store.setRoot(new SystemConfigImpl(store));
            instance.setConfigStore(store);

            BrokerConfig broker = new BrokerConfigAdapter(instance, instanceID);

            SystemConfig system = (SystemConfig) store.getRoot();
            system.addBroker(broker);
            instance.setBroker(broker);

            try
            {
                instance.initialise(instanceID);
            }
            catch (Exception e)
            {
                _instanceMap.remove(instanceID);
                try
                {
                    system.removeBroker(broker);
                }
                finally
                {
                    throw e;
                }
            }
        }
        else
        {
            remove(instanceID);
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
        return isConfigured(DEFAULT_INSTANCE);
    }

    public static boolean isConfigured(int instanceID)
    {
        return _instanceMap.containsKey(instanceID);
    }

    /** Method to cleanly shutdown the default registry running in this JVM */
    public static void remove()
    {
        remove(DEFAULT_INSTANCE);
    }

    /**
     * Method to cleanly shutdown specified registry running in this JVM
     *
     * @param instanceID the instance to shutdown
     */
    public static void remove(int instanceID)
    {
        try
        {
            IApplicationRegistry instance = _instanceMap.get(instanceID);
            if (instance != null)
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Shutting down ApplicationRegistry(" + instanceID + "):" + instance);
                }
                instance.close();
                instance.getBroker().getSystem().removeBroker(instance.getBroker());
            }
        }
        catch (Exception e)
        {
            _logger.error("Error shutting down Application Registry(" + instanceID + "): " + e, e);
        }
        finally
        {
            _instanceMap.remove(instanceID);
        }
    }

    /** Method to cleanly shutdown all registries currently running in this JVM */
    public static void removeAll()
    {
        Object[] keys = _instanceMap.keySet().toArray();
        for (Object k : keys)
        {
            remove((Integer) k);
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
            _pluginManager = new PluginManager(_configuration.getPluginDirectory());
        }
        catch (Exception e)
        {
            throw new ConfigurationException(e);
        }

        _configuration.configure();
    }

    public void initialise(int instanceID) throws Exception
    {
        _rootMessageLogger = new RootMessageLoggerImpl(_configuration,
                                                       new Log4jMessageLogger());
        _registryName = String.valueOf(instanceID);

        // Set the Actor for current log messages
        CurrentActor.set(new BrokerActor(_registryName, _rootMessageLogger));

        configure();

        _qmfService = new QMFService(getConfigStore(), this);

        CurrentActor.get().message(BrokerMessages.BRK_STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

        initialiseManagedObjectRegistry();

        _virtualHostRegistry = new VirtualHostRegistry(this);

        _accessManager = new ACLManager(_configuration.getSecurityConfiguration(), _pluginManager);

        createDatabaseManager(_configuration);

        _authenticationManager = new PrincipalDatabaseAuthenticationManager(null, null);

        _databaseManager.initialiseManagement(_configuration);

        _managedObjectRegistry.start();

        initialiseVirtualHosts();

        // Startup complete pop the current actor
        CurrentActor.remove();
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
        return getInstance(DEFAULT_INSTANCE);
    }

    public static IApplicationRegistry getInstance(int instanceID)
    {
        synchronized (IApplicationRegistry.class)
        {
            IApplicationRegistry instance = _instanceMap.get(instanceID);

            if (instance == null)
            {
                try
                {
                    _logger.info("Creating DEFAULT_APPLICATION_REGISTRY: " + _APPLICATION_REGISTRY + " : Instance:" + instanceID);
                    IApplicationRegistry registry = (IApplicationRegistry) Class.forName(_APPLICATION_REGISTRY).getConstructor((Class[]) null).newInstance((Object[]) null);
                    ApplicationRegistry.initialise(registry, instanceID);
                    _logger.info("Initialised Application Registry:" + instanceID);
                    return registry;
                }
                catch (Exception e)
                {
                    _logger.error("Error configuring application: " + e, e);
                    //throw new AMQBrokerCreationException(instanceID, "Unable to create Application Registry instance " + instanceID);
                    throw new RuntimeException("Unable to create Application Registry", e);
                }
            }
            else
            {
                return instance;
            }
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

        CurrentActor.get().message(BrokerMessages.BRK_STOPPED());
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
                    acceptor.getNetworkDriver().close();
                }
                catch (Throwable e)
                {
                    _logger.error("Unable to close network driver due to:" + e.getMessage());
                }

               CurrentActor.get().message(BrokerMessages.BRK_SHUTTING_DOWN(acceptor.toString(), bindAddress.getPort()));
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

    public static void setDefaultApplicationRegistry(String clazz)
    {
        _APPLICATION_REGISTRY = clazz;
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public ACLManager getAccessManager() throws ConfigurationException
    {
        return new ACLManager(_configuration.getSecurityConfiguration(), _pluginManager);
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
        VirtualHostImpl virtualHost = new VirtualHostImpl(this, vhostConfig);
        _virtualHostRegistry.registerVirtualHost(virtualHost);
        getBroker().addVirtualHost(virtualHost);
        return virtualHost;
    }
}
