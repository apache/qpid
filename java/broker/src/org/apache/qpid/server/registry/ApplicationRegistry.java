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
import java.util.Map;

/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 *
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 *
 */
public abstract class ApplicationRegistry implements IApplicationRegistry
{
    private static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private static IApplicationRegistry _instance;

    private final Map<Class<?>, Object> _configuredObjects = new HashMap<Class<?>, Object>();

    protected final Configuration _configuration;

    private static class ShutdownService implements Runnable
    {
        public void run()
        {
            _logger.info("Shutting down application registry...");
            try
            {
                _instance.getMessageStore().close();
            }
            catch (Exception e)
            {
                _logger.error("Error shutting down message store: " + e, e);
            }
        }
    }

    public static void initialise(IApplicationRegistry instance) throws Exception
    {
        _instance = instance;
        instance.initialise();
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownService()));
    }

    protected ApplicationRegistry(Configuration configuration)
    {
        _configuration = configuration;
    }

    public static IApplicationRegistry getInstance()
    {
        if (_instance == null)
        {
            throw new RuntimeException("Application registry not initialised");
        }
        else
        {
            return _instance;
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
}
