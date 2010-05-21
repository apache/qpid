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
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.main.AutoProcessor;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.security.access.ACLPlugin;
import org.apache.qpid.server.security.access.ACLPluginFactory;
import org.apache.qpid.server.security.access.plugins.AllowAll;
import org.apache.qpid.server.security.access.plugins.DenyAll;
import org.apache.qpid.server.security.access.plugins.LegacyAccessPlugin;
import org.apache.qpid.server.security.access.plugins.SimpleXML;
import org.apache.qpid.server.security.access.plugins.network.FirewallPlugin;
import org.apache.qpid.server.virtualhost.plugins.VirtualHostPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.ServiceTracker;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author aidan
 *
 *         Provides access to pluggable elements, such as exchanges
 */

public class PluginManager implements Closeable
{
    private ServiceTracker _exchangeTracker = null;
    private ServiceTracker _securityTracker = null;
    private ServiceTracker _configTracker = null;
    private ServiceTracker _virtualHostTracker = null;

    private Felix _felix;

    Activator _activator;

    private Map<String, ACLPluginFactory> _securityPlugins;
    private static final int FELIX_STOP_TIMEOUT = 30000;

    public PluginManager(String plugindir) throws Exception
    {
        StringMap configMap = new StringMap(false);

        // Add the bundle provided service interface package and the core OSGi
        // packages to be exported from the class path via the system bundle.
        configMap.put(FelixConstants.FRAMEWORK_SYSTEMPACKAGES,
                      "org.osgi.framework; version=1.3.0," +
                      "org.osgi.service.packageadmin; version=1.2.0," +
                      "org.osgi.service.startlevel; version=1.0.0," +
                      "org.osgi.service.url; version=1.0.0," +
                      "org.osgi.util.tracker; version=1.0.0," +
                      "org.apache.qpid.junit.extensions.util; version=0.7," +
                      "org.apache.qpid; version=0.7," +
                      "org.apache.qpid.framing; version=0.7," +
                      "org.apache.qpid.protocol; version=0.7," +
                      "org.apache.qpid.server.exchange; version=0.7," +
                      "org.apache.qpid.server.management; version=0.7," +
                      "org.apache.qpid.server.protocol; version=0.7," +
                      "org.apache.qpid.server.virtualhost; version=0.7," +
                      "org.apache.qpid.server.virtualhost.plugins; version=0.7," +
                      "org.apache.qpid.server.registry; version=0.7," +
                      "org.apache.qpid.server.queue; version=0.7," +
                      "org.apache.qpid.server.binding; version=0.7," +
                      "org.apache.qpid.server.configuration; version=0.7," +
                      "org.apache.qpid.server.configuration.plugins; version=0.7," +
                      "org.apache.qpid.server.configuration.management; version=0.7," +
                      "org.apache.qpid.server.persistent; version=0.7," +
                      "org.apache.qpid.server.plugins; version=0.7," +
                      "org.apache.qpid.server.queue; version=0.7," +
                      "org.apache.qpid.server.security; version=0.7," +
                      "org.apache.qpid.framing.AMQShortString; version=0.7," +
                      "org.apache.qpid.server.queue.AMQQueue; version=0.7," +
                      "org.apache.qpid.server.security.access; version=0.7," +
                      "org.apache.commons.configuration; version=0.7," +
                      "org.apache.log4j; version=1.2.12," +
                      "javax.management.openmbean; version=1.0.0," +
                      "javax.management; version=1.0.0,"
        );

        if (plugindir == null)
        {
            return;
        }

        // Set the list of bundles to load
        File dir = new File(plugindir);
        if (!dir.exists())
        {
            return;
        }

        StringBuffer pluginJars = new StringBuffer();

        if (dir.isDirectory())
        {
            for (File child : dir.listFiles())
            {
                if (child.getName().endsWith("jar"))
                {
                    pluginJars.append(String.format(" file:%s%s%s", plugindir, File.separator, child.getName()));
                }
            }
        }

        if (pluginJars.length() == 0)
        {
            return;
        }

//        configMap.put(FelixConstants.AUTO_START_PROP + ".1", pluginJars.toString());
//        configMap.put(BundleCache.CACHE_PROFILE_DIR_PROP, plugindir);

         configMap.put(AutoProcessor.AUTO_START_PROP + ".1", pluginJars.toString());

        configMap.put(FelixConstants.FRAMEWORK_STORAGE, plugindir);


        List<BundleActivator> activators = new ArrayList<BundleActivator>();
        _activator = new Activator();
        activators.add(_activator);
        configMap.put(FelixConstants.SYSTEMBUNDLE_ACTIVATORS_PROP, activators);

        _felix = new Felix(configMap);
        try
        {
            System.out.println("Starting Plugin manager");

            _felix.start();


           AutoProcessor.process(configMap, _felix.getBundleContext());
                                                         
            System.out.println("Started Plugin manager");

            _exchangeTracker = new ServiceTracker(_activator.getContext(), ExchangeType.class.getName(), null);
            _exchangeTracker.open();

            _securityTracker = new ServiceTracker(_activator.getContext(), ACLPlugin.class.getName(), null);
            _securityTracker.open();

            _configTracker = new ServiceTracker(_activator.getContext(), ConfigurationPluginFactory.class.getName(), null);
            _configTracker.open();

            _virtualHostTracker = new ServiceTracker(_activator.getContext(), VirtualHostPluginFactory.class.getName(), null);
            _virtualHostTracker.open();

        }
        catch (BundleException e)
        {
            throw new ConfigurationException("Could not start PluginManager:" + e.getMessage(), e);
        }
    }

    private <T> Map<String, T> getServices(ServiceTracker tracker)
    {
        Map<String, T> services = new HashMap<String, T>();

        if ((tracker != null) && (tracker.getServices() != null))
        {
            for (Object service : tracker.getServices())
            {
                if (service instanceof PluginFactory)
                {
                    services.put(((PluginFactory) service).getPluginName(), (T) service);
                }
                else
                {
                    services.put(service.getClass().getName(), (T) service);
                }
            }
        }

        return services;
    }

    public Map<String, ExchangeType<?>> getExchanges()
    {
        return getServices(_exchangeTracker);
    }

    public Map<String, ACLPluginFactory> getSecurityPlugins()
    {
        _securityPlugins = getServices(_securityTracker);
        // A little gross that we have to add them here, but not all the plugins are OSGIfied
        _securityPlugins.put(SimpleXML.class.getName(), SimpleXML.FACTORY);
        _securityPlugins.put(AllowAll.class.getName(), AllowAll.FACTORY);
        _securityPlugins.put(DenyAll.class.getName(), DenyAll.FACTORY);
        _securityPlugins.put(LegacyAccessPlugin.class.getName(), LegacyAccessPlugin.FACTORY);
        _securityPlugins.put(FirewallPlugin.class.getName(), FirewallPlugin.FACTORY);

        return _securityPlugins;
    }

    public Map<String, ConfigurationPluginFactory> getConfigurationPlugins()
    {
        Map<String, ConfigurationPluginFactory> services = new HashMap<String, ConfigurationPluginFactory>();

        if ((_configTracker != null) && (_configTracker.getServices() != null))
        {
            for (Object service : _configTracker.getServices())
            {
                for (String parent : ((ConfigurationPluginFactory) service).getParentPaths())
                {
                    services.put(parent, ((ConfigurationPluginFactory) service));
                }
            }
        }

        return services;

    }

    public Map<String, VirtualHostPluginFactory> getVirtualHostPlugins()
    {
        return getServices(_virtualHostTracker);
    }

    public <P extends PluginFactory> Map<String, P> getPlugins(Class<P> plugin)
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

    public void close()
    {
        if (_felix != null)
        {
            try
            {
                _exchangeTracker.close();

                _securityTracker.close();

                _configTracker.close();

                _virtualHostTracker.close();
            }
            finally
            {
                System.out.println("Stopping Plugin manager");
                //fixme should be stopAndWait() but hangs VM, need upgrade in felix
                try
                {
                    _felix.stop();
                }
                catch (BundleException e)
                {
                    //ignore
                }

                try
                {
                    _felix.waitForStop(FELIX_STOP_TIMEOUT);
                }
                catch (InterruptedException e)
                {
                    //ignore
                }

                System.out.println("Stopped Plugin manager");
            }
        }
    }

}
