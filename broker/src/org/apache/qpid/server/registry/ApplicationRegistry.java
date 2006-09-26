/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.registry;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.Configurator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public abstract class ApplicationRegistry implements IApplicationRegistry
{
    private static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static Map _instanceMap = new HashMap();

    private final Map<Class<?>, Object> _configuredObjects = new HashMap<Class<?>, Object>();

    protected final Configuration _configuration;

    public static final int DEFAULT_INSTANCE = 1;
    public static final String DEFAULT_APPLICATION_REGISTRY = "org.apache.qpid.server.util.NullApplicationRegistry";
    public static String _APPLICATION_REGISTRY = DEFAULT_APPLICATION_REGISTRY;

    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownService()));
    }

    private static class ShutdownService implements Runnable
    {
        public void run()
        {
            _logger.info("Shutting down application registries...");
            try
            {
                synchronized (ApplicationRegistry.class)
                {
                    Iterator keyIterator = _instanceMap.keySet().iterator();

                    while (keyIterator.hasNext())
                    {
                        int key = (Integer) keyIterator.next();
                        IApplicationRegistry instance = (IApplicationRegistry) _instanceMap.get(key);

                        if ((instance != null))
                        {
                            if (instance.getMessageStore() != null)
                            {
                                instance.getMessageStore().close();
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                _logger.error("Error shutting down message store: " + e, e);
            }
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

    public static void remove(int instanceID)
    {
        try
        {
            ((IApplicationRegistry) _instanceMap.get(instanceID)).getMessageStore().close();
        }
        catch (Exception e)
        {

        }
        finally
        {
            _instanceMap.remove(instanceID);
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
        IApplicationRegistry instance = (IApplicationRegistry) _instanceMap.get(instanceID);

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
                throw new RuntimeException("Unable to create Application Registry");
            }
        }
        else
        {
            return instance;
        }
    }

    public Configuration getConfiguration()
    {
        return _configuration;
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
                throw new IllegalArgumentException("Unable to instantiate configuration class " + instanceType + " - ensure it has a public default constructor");
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
}
