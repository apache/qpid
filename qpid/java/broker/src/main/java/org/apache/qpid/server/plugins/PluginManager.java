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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.felix.framework.Felix;
import org.apache.felix.framework.util.StringMap;
import org.apache.log4j.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Version;
import org.osgi.framework.launch.Framework;
import org.osgi.util.tracker.ServiceTracker;

import org.apache.qpid.common.Closeable;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.configuration.TopicConfiguration;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionConfiguration.SlowConsumerDetectionConfigurationFactory;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionPolicyConfiguration.SlowConsumerDetectionPolicyConfigurationFactory;
import org.apache.qpid.server.configuration.plugins.SlowConsumerDetectionQueueConfiguration.SlowConsumerDetectionQueueConfigurationFactory;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SecurityPluginFactory;
import org.apache.qpid.server.security.access.plugins.LegacyAccess;
import org.apache.qpid.server.security.auth.manager.AuthenticationManagerPluginFactory;
import org.apache.qpid.server.security.auth.manager.PrincipalDatabaseAuthenticationManager;
import org.apache.qpid.server.virtualhost.plugins.SlowConsumerDetection;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;
import org.apache.qpid.server.virtualhost.plugins.policies.TopicDeletePolicy;
import org.apache.qpid.slowconsumerdetection.policies.SlowConsumerPolicyPluginFactory;
import org.apache.qpid.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.apache.felix.framework.util.FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_ACTION_PROPERY;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_DIR_PROPERY;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_INSTALL_VALUE;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_START_VALUE;
import static org.apache.felix.main.AutoProcessor.process;
import static org.osgi.framework.Constants.FRAMEWORK_STORAGE;
import static org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN;
import static org.osgi.framework.Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT;
import static org.osgi.framework.Constants.FRAMEWORK_SYSTEMPACKAGES;

/**
 * Provides access to pluggable elements, such as exchanges
 */
@SuppressWarnings("unchecked")
public class PluginManager implements Closeable
{
    private static final Logger _logger = Logger.getLogger(PluginManager.class);

    private static final int FELIX_STOP_TIMEOUT = 30000;

    private Framework _felix;

    private ServiceTracker _exchangeTracker = null;
    private ServiceTracker _securityTracker = null;
    private ServiceTracker _configTracker = null;
    private ServiceTracker _virtualHostTracker = null;
    private ServiceTracker _policyTracker = null;
    private ServiceTracker _authenticationManagerTracker = null;

    private Activator _activator;

    private final List<ServiceTracker> _trackers = new ArrayList<ServiceTracker>();
    private Map<String, SecurityPluginFactory> _securityPlugins = new HashMap<String, SecurityPluginFactory>();
    private Map<List<String>, ConfigurationPluginFactory> _configPlugins = new IdentityHashMap<List<String>, ConfigurationPluginFactory>();
    private Map<String, VirtualHostPluginFactory> _vhostPlugins = new HashMap<String, VirtualHostPluginFactory>();
    private Map<String, SlowConsumerPolicyPluginFactory> _policyPlugins = new HashMap<String, SlowConsumerPolicyPluginFactory>();
    private Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> _authenticationManagerPlugins = new HashMap<String, AuthenticationManagerPluginFactory<? extends Plugin>>();

    /** The default name of the OSGI system package list. */
    private static final String DEFAULT_RESOURCE_NAME = "org/apache/qpid/server/plugins/OsgiSystemPackages.properties";
    
    /** The name of the override system property that holds the name of the OSGI system package list. */
    private static final String FILE_PROPERTY = "qpid.osgisystempackages.properties";
    
    private static final String OSGI_SYSTEM_PACKAGES;
    
    static 
    {
        final String filename = System.getProperty(FILE_PROPERTY);
        final InputStream is = FileUtils.openFileOrDefaultResource(filename, DEFAULT_RESOURCE_NAME,
                    PluginManager.class.getClassLoader());
        
        try
        {
            Version qpidReleaseVersion;
            try
            {
                qpidReleaseVersion = Version.parseVersion(QpidProperties.getReleaseVersion());
            }
            catch (IllegalArgumentException iae)
            {
                qpidReleaseVersion = null;
            }
            
            final Properties p  = new Properties();
            p.load(is);
            
            final OsgiSystemPackageUtil osgiSystemPackageUtil = new OsgiSystemPackageUtil(qpidReleaseVersion, (Map)p);
            
            OSGI_SYSTEM_PACKAGES = osgiSystemPackageUtil.getFormattedSystemPackageString();
            
            _logger.debug("List of OSGi system packages to be added: " + OSGI_SYSTEM_PACKAGES);
        }
        catch (IOException e)
        {
            _logger.error("Error reading OSGI system package list", e);
            throw new ExceptionInInitializerError(e);
        }
    }
    
    
    public PluginManager(String pluginPath, String cachePath, BundleContext bundleContext) throws Exception
    {
        // Store all non-OSGi plugins
        // A little gross that we have to add them here, but not all the plugins are OSGIfied
        for (SecurityPluginFactory<?> pluginFactory : Arrays.asList(LegacyAccess.FACTORY))
        {
            _securityPlugins.put(pluginFactory.getPluginName(), pluginFactory);
        }
        for (ConfigurationPluginFactory configFactory : Arrays.asList(
                TopicConfiguration.FACTORY,
                SecurityManager.SecurityConfiguration.FACTORY,
                LegacyAccess.LegacyAccessConfiguration.FACTORY,
                new SlowConsumerDetectionConfigurationFactory(),
                new SlowConsumerDetectionPolicyConfigurationFactory(),
                new SlowConsumerDetectionQueueConfigurationFactory(),
                PrincipalDatabaseAuthenticationManager.PrincipalDatabaseAuthenticationManagerConfiguration.FACTORY))
        {
            _configPlugins.put(configFactory.getParentPaths(), configFactory);
        }
        for (SlowConsumerPolicyPluginFactory pluginFactory : Arrays.asList(
                new TopicDeletePolicy.TopicDeletePolicyFactory()))
        {
            _policyPlugins.put(pluginFactory.getPluginName(), pluginFactory);
        }
        for (VirtualHostPluginFactory pluginFactory : Arrays.asList(
                new SlowConsumerDetection.SlowConsumerFactory()))
        {
            _vhostPlugins.put(pluginFactory.getClass().getName(), pluginFactory);
        }

        for (AuthenticationManagerPluginFactory<? extends Plugin> pluginFactory : Arrays.asList(
                PrincipalDatabaseAuthenticationManager.FACTORY))
        {
            _authenticationManagerPlugins.put(pluginFactory.getPluginName(), pluginFactory);
        }

        if(bundleContext == null)
        {
            // Check the plugin directory path is set and exist
            if (pluginPath == null)
            {
                _logger.info("No plugin path specified, no plugins will be loaded.");
                return;
            }
            File pluginDir = new File(pluginPath);
            if (!pluginDir.exists())
            {
                _logger.warn("Plugin dir : "  + pluginDir + " does not exist.");
                return;
            }

            // Add the bundle provided service interface package and the core OSGi
            // packages to be exported from the class path via the system bundle.

            // Setup OSGi configuration property map
            final StringMap configMap = new StringMap(false);
            configMap.put(FRAMEWORK_SYSTEMPACKAGES, OSGI_SYSTEM_PACKAGES);

            // No automatic shutdown hook
            configMap.put("felix.shutdown.hook", "false");

            // Add system activator
            List<BundleActivator> activators = new ArrayList<BundleActivator>();
            _activator = new Activator();
            activators.add(_activator);
            configMap.put(SYSTEMBUNDLE_ACTIVATORS_PROP, activators);

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
                configMap.put(FRAMEWORK_STORAGE, cachePath);
            }
            configMap.put(FRAMEWORK_STORAGE_CLEAN, FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);

            // Set directory with plugins to auto-deploy
            _logger.info("Auto deploying bundles from directory " + pluginPath);
            configMap.put(AUTO_DEPLOY_DIR_PROPERY, pluginPath);
            configMap.put(AUTO_DEPLOY_ACTION_PROPERY, AUTO_DEPLOY_INSTALL_VALUE + "," + AUTO_DEPLOY_START_VALUE);

            // Start plugin manager
            _felix = new Felix(configMap);
            try
            {
                _logger.info("Starting plugin manager framework");
                _felix.init();
                process(configMap, _felix.getBundleContext());
                _felix.start();
                _logger.info("Started plugin manager framework");
            }
            catch (BundleException e)
            {
                throw new ConfigurationException("Could not start plugin manager: " + e.getMessage(), e);
            }

            bundleContext = _activator.getContext();
        }
        else
        {
            _logger.info("Using the specified external BundleContext");
        }

        // TODO save trackers in a map, keyed by class name
        
        _exchangeTracker = new ServiceTracker(bundleContext, ExchangeType.class.getName(), null);
        _exchangeTracker.open();
        _trackers.add(_exchangeTracker);

        _securityTracker = new ServiceTracker(bundleContext, SecurityPluginFactory.class.getName(), null);
        _securityTracker.open();
        _trackers.add(_securityTracker);

        _configTracker = new ServiceTracker(bundleContext, ConfigurationPluginFactory.class.getName(), null);
        _configTracker.open();
        _trackers.add(_configTracker);

        _virtualHostTracker = new ServiceTracker(bundleContext, VirtualHostPluginFactory.class.getName(), null);
        _virtualHostTracker.open();
        _trackers.add(_virtualHostTracker);
 
        _policyTracker = new ServiceTracker(bundleContext, SlowConsumerPolicyPluginFactory.class.getName(), null);
        _policyTracker.open();
        _trackers.add(_policyTracker);

        _authenticationManagerTracker = new ServiceTracker(bundleContext, AuthenticationManagerPluginFactory.class.getName(), null);
        _authenticationManagerTracker.open();
        _trackers.add(_authenticationManagerTracker);

        _logger.info("Opened service trackers");
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

    public static <T> Map<String, T> getServices(ServiceTracker tracker, Map<String, T> plugins)
    {   
        Map<String, T> services = getServices(tracker);
        services.putAll(plugins);
        return services;
    }

    public Map<List<String>, ConfigurationPluginFactory> getConfigurationPlugins()
    {   
        Map<List<String>, ConfigurationPluginFactory> services = new IdentityHashMap<List<String>, ConfigurationPluginFactory>();
        
        if (_configTracker != null && _configTracker.getServices() != null)
        {
            for (Object service : _configTracker.getServices())
            {
                ConfigurationPluginFactory factory = (ConfigurationPluginFactory) service;
                services.put(factory.getParentPaths(), factory);
            }
        }
        
        services.putAll(_configPlugins);

        return services;
    }

    public Map<String, VirtualHostPluginFactory> getVirtualHostPlugins()
    {   
        return getServices(_virtualHostTracker, _vhostPlugins);
    }

    public Map<String, SlowConsumerPolicyPluginFactory> getSlowConsumerPlugins()
    {   
        return getServices(_policyTracker, _policyPlugins);
    }

    public Map<String, ExchangeType<?>> getExchanges()
    {
        return getServices(_exchangeTracker);
    }
    
    public Map<String, SecurityPluginFactory> getSecurityPlugins()
    {
        return getServices(_securityTracker, _securityPlugins);
    }

    public Map<String, AuthenticationManagerPluginFactory<? extends Plugin>> getAuthenticationManagerPlugins()
    {
        return getServices(_authenticationManagerTracker, _authenticationManagerPlugins);
    }

    public void close()
    {
        try
        {
            // Close all bundle trackers
            for(ServiceTracker tracker : _trackers)
            {
                tracker.close();
            }
        }
        finally
        {
            if (_felix != null)
            {
                _logger.info("Stopping plugin manager framework");
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
                _logger.info("Stopped plugin manager framework");
            }
            else
            {
                _logger.info("Plugin manager was started with an external BundleContext, " +
                             "skipping remaining shutdown tasks");
            }
        }
    }
}
