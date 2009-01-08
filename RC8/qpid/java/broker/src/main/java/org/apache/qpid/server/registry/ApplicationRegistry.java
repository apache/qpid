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
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.Configurator;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.server.management.ManagedObjectRegistry;
import org.apache.qpid.server.security.auth.manager.AuthenticationManager;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.security.auth.database.PrincipalDatabaseManager;
import org.apache.qpid.server.security.auth.database.ConfigurationFilePrincipalDatabaseManager;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.ACLManager;
import org.apache.qpid.server.plugins.PluginManager;
import org.apache.mina.common.IoAcceptor;

import java.util.HashMap;
import java.util.Map;
import java.net.InetSocketAddress;

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

    protected final Configuration _configuration;

    public static final int DEFAULT_INSTANCE = 1;
    public static final String DEFAULT_APPLICATION_REGISTRY = "org.apache.qpid.server.util.NullApplicationRegistry";
    public static String _APPLICATION_REGISTRY = DEFAULT_APPLICATION_REGISTRY;

    protected final Map<InetSocketAddress, IoAcceptor> _acceptors = new HashMap<InetSocketAddress, IoAcceptor>();

    protected ManagedObjectRegistry _managedObjectRegistry;

    protected AuthenticationManager _authenticationManager;

    protected VirtualHostRegistry _virtualHostRegistry;

    protected ACLPlugin _accessManager;

    protected PrincipalDatabaseManager _databaseManager;

    protected PluginManager _pluginManager;

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
            _logger.info("Initialising Application Registry:" + instanceID);
            _instanceMap.put(instanceID, instance);

            try
            {
                instance.initialise();
            }
            catch (Exception e)
            {
                _instanceMap.remove(instanceID);
                throw e;
            }
        }
        else
        {
            remove(instanceID);
        }
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
                    _logger.info("Shuting down ApplicationRegistry(" + instanceID + "):" + instance);
                }
                instance.close();
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

    protected ApplicationRegistry(Configuration configuration)
    {
        _configuration = configuration;
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

    public void close() throws Exception
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Shutting down ApplicationRegistry:"+this);
        }

        //Stop incomming connections
        unbind();

        //Shutdown virtualhosts
        for (VirtualHost virtualHost : getVirtualHostRegistry().getVirtualHosts())
        {
            virtualHost.close();
        }

        // Replace above with this
//        _virtualHostRegistry.close();

//        _accessManager.close();

//        _databaseManager.close();

        _authenticationManager.close();

//        _databaseManager.close();

        // close the rmi registry(if any) started for management
        if (_managedObjectRegistry != null)
        {
            _managedObjectRegistry.close();
        }

//        _pluginManager.close();
    }

    private void unbind()
    {
        synchronized (_acceptors)
        {
            for (InetSocketAddress bindAddress : _acceptors.keySet())
            {
                IoAcceptor acceptor = _acceptors.get(bindAddress);
                acceptor.unbind(bindAddress);
            }
        }
    }

    public Configuration getConfiguration()
    {
        return _configuration;
    }

    public void addAcceptor(InetSocketAddress bindAddress, IoAcceptor acceptor)
    {
        synchronized (_acceptors)
        {
            _acceptors.put(bindAddress, acceptor);
        }
    }

    public <T> T getConfiguredObject(Class<T> instanceType)
    {
        T instance = (T) _configuredObjects.get(instanceType);
        if (instance == null)
        {
            try
            {
                instance = instanceType.newInstance();
            }
            catch (Exception e)
            {
                _logger.error("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor");
                throw new IllegalArgumentException("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor", e);
            }
            Configurator.configure(instance);
            _configuredObjects.put(instanceType, instance);
        }
        return instance;
    }

    public static void setDefaultApplicationRegistry(String clazz)
    {
        _APPLICATION_REGISTRY = clazz;
    }

    public VirtualHostRegistry getVirtualHostRegistry()
    {
        return _virtualHostRegistry;
    }

    public ACLPlugin getAccessManager()
    {
        return _accessManager;
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

}
