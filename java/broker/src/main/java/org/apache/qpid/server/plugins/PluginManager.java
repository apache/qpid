/*
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
 */
package org.apache.qpid.server.plugins;

import static org.apache.felix.framework.util.FelixConstants.*;
import static org.apache.felix.main.AutoProcessor.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.StringMap;
import org.apache.log4j.Logger;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.apache.qpid.server.security.access.plugins.AllowAll;
import org.apache.qpid.server.security.access.plugins.DenyAll;
import org.apache.qpid.server.security.access.plugins.LegacyAccess;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Provides access to pluggable elements, such as exchanges
 */
@SuppressWarnings("unchecked")
public class PluginManager implements Closeable
{
    private static final Logger _logger = Logger.getLogger(PluginManager.class);

    private static final int FELIX_STOP_TIMEOUT = 30000;
    private static final String VERSION = "2.6.0.4";

    private Felix _felix;

    private ServiceTracker _exchangeTracker = null;
    private ServiceTracker _securityTracker = null;
    private ServiceTracker _configTracker = null;
    private ServiceTracker _virtualHostTracker = null;

    private Activator _activator;

    private Map<String, SecurityPluginFactory> _securityPlugins = new HashMap<String, SecurityPluginFactory>();
    private Map<List<String>, ConfigurationPluginFactory> _configPlugins = new IdentityHashMap<List<String>, ConfigurationPluginFactory>();

    public PluginManager(String pluginPath, String cachePath) throws Exception
    {
        // Store all non-OSGi plugins
        // A little gross that we have to add them here, but not all the plugins are OSGIfied
        for (SecurityPluginFactory<?> pluginFactory : Arrays.asList(
                AllowAll.FACTORY, DenyAll.FACTORY, LegacyAccess.FACTORY))
        {
            _securityPlugins.put(pluginFactory.getPluginName(), pluginFactory);
        }
        for (ConfigurationPluginFactory configFactory : Arrays.asList(
                SecurityManager.SecurityConfiguration.FACTORY,
                AllowAll.AllowAllConfiguration.FACTORY,
                DenyAll.DenyAllConfiguration.FACTORY,
                LegacyAccess.LegacyAccessConfiguration.FACTORY))
        {
            _configPlugins.put(configFactory.getParentPaths(), configFactory);
        }

        // Check the plugin directory path is set and exist
        if (pluginPath == null)
        {
            return;
        }
        File pluginDir = new File(pluginPath);
        if (!pluginDir.exists())
        {
            return;
        } 

        // Setup OSGi configuration propery map
        StringMap configMap = new StringMap(false);

        // Add the bundle provided service interface package and the core OSGi
        // packages to be exported from the class path via the system bundle.
        configMap.put(FRAMEWORK_SYSTEMPACKAGES,
                "org.osgi.framework; version=1.3.0," +
                "org.osgi.service.packageadmin; version=1.2.0," +
                "org.osgi.service.startlevel; version=1.0.0," +
                "org.osgi.service.url; version=1.0.0," +
                "org.osgi.util.tracker; version=1.0.0," +
                "org.apache.qpid.junit.extensions.util; version=0.7," +
                "org.apache.qpid; version=0.7," +
                "org.apache.qpid.exchange; version=0.7," +
                "org.apache.qpid.framing; version=0.7," +
                "org.apache.qpid.protocol; version=0.7," +
                "org.apache.qpid.server.binding; version=0.7," +
                "org.apache.qpid.server.configuration; version=0.7," +
                "org.apache.qpid.server.configuration.plugins; version=0.7," +
                "org.apache.qpid.server.configuration.management; version=0.7," +
                "org.apache.qpid.server.exchange; version=0.7," +
                "org.apache.qpid.server.logging; version=0.7," +
                "org.apache.qpid.server.logging.actors; version=0.7," +                
                "org.apache.qpid.server.management; version=0.7," +
                "org.apache.qpid.server.persistent; version=0.7," +
                "org.apache.qpid.server.plugins; version=0.7," +
                "org.apache.qpid.server.protocol; version=0.7," +
                "org.apache.qpid.server.queue; version=0.7," +
                "org.apache.qpid.server.registry; version=0.7," +
                "org.apache.qpid.server.security; version=0.7," +
                "org.apache.qpid.server.security.access; version=0.7," +
                "org.apache.qpid.server.security.access.plugins; version=0.7," +
                "org.apache.qpid.server.virtualhost; version=0.7," +
                "org.apache.qpid.server.virtualhost.plugins; version=0.7," +
                "org.apache.qpid.util; version=0.7," +
                "org.apache.commons.configuration; version=1.0.0," +
                "org.apache.commons.lang; version=1.0.0," +
                "org.apache.commons.lang.builder; version=1.0.0," +
                "org.apache.commons.logging; version=1.0.0," +
                "org.apache.log4j; version=1.2.12," +
                "javax.management.openmbean; version=1.0.0," +
                "javax.management; version=1.0.0"
            );
        
        // No automatic shutdown hook
        configMap.put("felix.shutdown.hook", "false");
        
        // Add system activator
        List<BundleActivator> activators = new ArrayList<BundleActivator>();
        _activator = new Activator();
        activators.add(_activator);
        configMap.put(SYSTEMBUNDLE_ACTIVATORS_PROP, activators);
        
        // Get the list of bundles to load
        StringBuffer pluginJars = new StringBuffer();
        if (pluginDir.isDirectory())
        {
            for (String file : pluginDir.list())            
            {
                if (file.endsWith(".jar"))
                {
                    pluginJars.append(String.format("file:%s%s%s ", pluginPath, File.separator, file));
                }
            }
        }

        if (cachePath != null)
            {
            File cacheDir = new File(cachePath);
            if (!cacheDir.exists() && cacheDir.canWrite())
            {
                _logger.info("Creating plugin cache directory: " + cachePath);
                cacheDir.mkdir();
            }
            
            // Set plugin cache directory and empty it
            _logger.info("Cache bundles in directory " + cachePath);
            configMap.put("org.osgi.framework.storage", cachePath);
        }
        configMap.put("org.osgi.framework.storage.clean", "onFirstInit");
        
        // Set directory with plugins
        _logger.info("Auto deploy bundles from directory " + pluginPath);

        // Set list of auto-start plugin JAR files
        configMap.put(AUTO_START_PROP + "." + FRAMEWORK_DEFAULT_STARTLEVEL, pluginJars.toString());
        
        // FIXME why does this not work?
        configMap.put(AUTO_DEPLOY_DIR_PROPERY, pluginPath);
        configMap.put(AUTO_DEPLOY_ACTION_PROPERY, AUTO_DEPLOY_START_VALUE);        
        
        // Start plugin manager and trackers
        _felix = new Felix(configMap);
        try
        {
            _logger.info("Starting plugin manager...");
            _felix.init();
	        process(configMap, _felix.getBundleContext());
            _felix.start();
            _logger.info("Started plugin manager");
        }
        catch (BundleException e)
        {
            throw new ConfigurationException("Could not start plugin manager: " + e.getMessage(), e);
        }
        
        // TODO save trackers in a map, keyed by class name
        
        _exchangeTracker = new ServiceTracker(_activator.getContext(), ExchangeType.class.getName(), null);
        _exchangeTracker.open();

        _securityTracker = new ServiceTracker(_activator.getContext(), SecurityPluginFactory.class.getName(), null);
        _securityTracker.open();

        _configTracker = new ServiceTracker(_activator.getContext(), ConfigurationPluginFactory.class.getName(), null);
        _configTracker.open();

        _virtualHostTracker = new ServiceTracker(_activator.getContext(), VirtualHostPluginFactory.class.getName(), null);
        _virtualHostTracker.open();

        _logger.info("Opened service trackers");
        
        // Load security and configuration plugins from their trackers for access
        _configPlugins.putAll(getConfigurationServices());
        _securityPlugins.putAll(getPlugins(SecurityPluginFactory.class));
    }

    private static <T> Map<String, T> getServices(ServiceTracker tracker)
    {   
        Map<String, T> services = new HashMap<String, T>();
        
        if ((tracker != null) && (tracker.getServices() != null))
        {
            for (Object service : tracker.getServices())
            {
                if (service instanceof PluginFactory<?>)
                {
                    services.put(((PluginFactory<?>) service).getPluginName(), (T) service);
                }
                else
                {
                    services.put(service.getClass().getName(), (T) service);
                }
            }
        }

        return services;
    }

    private Map<List<String>, ConfigurationPluginFactory> getConfigurationServices()
    {   
        Map<List<String>, ConfigurationPluginFactory> services = new IdentityHashMap<List<String>, ConfigurationPluginFactory>();
        
        if (_configTracker.getServices() != null)
        {
            for (Object service : _configTracker.getServices())
            {
                ConfigurationPluginFactory factory = (ConfigurationPluginFactory) service;
                services.put(factory.getParentPaths(), factory);
            }
        }

        return services;
    }

    public Map<String, ExchangeType<?>> getExchanges()
    {
        return getServices(_exchangeTracker);
    }

    public Map<String, VirtualHostPluginFactory> getVirtualHostPlugins()
    {
        return getServices(_virtualHostTracker);
    }

    public <P extends PluginFactory<?>> Map<String, P> getPlugins(Class<P> plugin)
    {
        // If plugins are not configured then return an empty set
        if (_activator == null)
        {
            return new HashMap<String, P>();
        }

        ServiceTracker tracker = new ServiceTracker(_activator.getContext(), plugin.getName(), null);
        tracker.open();

        try
        {
            return getServices(tracker);
        }
        finally
        {
            tracker.close();
        }
    }
    
    public Map<String, SecurityPluginFactory> getSecurityPlugins()
    {
        return _securityPlugins;
    }
    
    public Map<List<String>, ConfigurationPluginFactory> getConfigurationPlugins()
    {
        return _configPlugins;
    }

    public void close()
    {
        if (_felix != null)
        {
            try
            {
                // Close all bundle trackers
                _exchangeTracker.close();
                _securityTracker.close();
                _configTracker.close();
                _virtualHostTracker.close();
            }
            finally
            {
                _logger.info("Stopping plugin manager");
                try
                {
                    // FIXME should be stopAndWait() but hangs VM, need upgrade in felix
                    _felix.stop();
                }
                catch (BundleException e)
                {
                    // Ignore
                }

                try
                {
                    _felix.waitForStop(FELIX_STOP_TIMEOUT);
                }
                catch (InterruptedException e)
                {
                    // Ignore
                }
                _logger.info("Stopped plugin manager");
            }
        }
    }
}
