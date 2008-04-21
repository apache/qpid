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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.cache.BundleCache;
import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.StringMap;
import org.apache.qpid.server.exchange.ExchangeType;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;
import org.osgi.util.tracker.ServiceTracker;

/**
 * 
 * @author aidan
 *
 * Provides access to pluggable elements, such as exchanges
 */

public class PluginManager
{

    private Felix _felix = null;
    private ServiceTracker _exchangeTracker = null;
    private Activator _activator = null;
    private boolean _empty;

    public PluginManager(String plugindir) throws Exception
    {
        StringMap configMap = new StringMap(false);

        // Tell felix it's being embedded
        configMap.put(FelixConstants.EMBEDDED_EXECUTION_PROP, "true");
        // Add the bundle provided service interface package and the core OSGi
        // packages to be exported from the class path via the system bundle.
        configMap.put(FelixConstants.FRAMEWORK_SYSTEMPACKAGES, "org.osgi.framework; version=1.3.0,"
                + "org.osgi.service.packageadmin; version=1.2.0," + 
                "org.osgi.service.startlevel; version=1.0.0," + 
                "org.osgi.service.url; version=1.0.0," + 
                "org.apache.qpid.framing; version=0.2.1," +
                "org.apache.qpid.server.exchange; version=0.2.1," +
                "org.apache.qpid.server.management; version=0.2.1,"+
                "org.apache.qpid.protocol; version=0.2.1,"+
                "org.apache.qpid.server.virtualhost; version=0.2.1," +
                "org.apache.qpid; version=0.2.1," +
                "org.apache.qpid.server.queue; version=0.2.1," +
                "javax.management.openmbean; version=1.0.0,"+
                "javax.management; version=1.0.0,"+
                "org.apache.qpid.junit.extensions.util; version=0.6.1,"
                );
        
        if (plugindir == null)
        {
        	_empty = true;
            return;
        }
        
        // Set the list of bundles to load
        File dir = new File(plugindir);
        if (!dir.exists())
        {
        	_empty = true;
            return;
        }
        StringBuffer pluginJars = new StringBuffer();
        
        if (dir.isDirectory())
        {
            for (String child : dir.list())
            {
                if (child.endsWith("jar"))
                {
                    pluginJars.append(String.format(" file:%s%s%s", plugindir,File.separator,child));
                }
            }
        }
        if (pluginJars.length() == 0)
        {
            _empty = true;
            return;
        }
            
        configMap.put(FelixConstants.AUTO_START_PROP + ".1", pluginJars.toString());
        configMap.put(BundleCache.CACHE_PROFILE_DIR_PROP, plugindir);
        
        List<BundleActivator> activators = new ArrayList<BundleActivator>();
        _activator = new Activator();
        activators.add(_activator);

        _felix = new Felix(configMap, activators);
        try
        {
            _felix.start();
            _exchangeTracker = new ServiceTracker(_activator.getContext(), ExchangeType.class.getName(), null);
            _exchangeTracker.open();
        }
        catch (BundleException e)
        {
            throw new Exception("Could not create bundle");
        }
    }

    public Map<String, ExchangeType<?>> getExchanges()
    {
        if (_empty)
        {
            return null;
        }
        Map<String, ExchangeType<?>>exchanges = new HashMap<String, ExchangeType<?>>();
        for (Object service : _exchangeTracker.getServices())
        {
            if (service instanceof ExchangeType<?>)
            {
                exchanges.put(service.getClass().getName(), (ExchangeType<?>) service);
            }
        }
        
        return exchanges;
    }

}
