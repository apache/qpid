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
package org.apache.qpid.server.jmx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class JMXActivator implements  BundleActivator
{
    private static final Logger LOGGER = Logger.getLogger(JMXActivator.class);

    private String _bundleName;
    private JMXService _jmxService;

    private List<ServiceRegistration> _registeredServices;


    public void start(final BundleContext ctx) throws Exception
    {
        boolean jmxManagementEnabled = ApplicationRegistry.getInstance().getConfiguration().getJMXManagementEnabled();

        if (jmxManagementEnabled)
        {
            _jmxService = new JMXService();
            startJmsService(_jmxService);

            _bundleName = ctx.getBundle().getSymbolicName();

            _registeredServices = registerServices(ctx);
        }
        else
        {
            LOGGER.debug("Skipping registration of JMX plugin as JMX Management disabled in config. ");
        }
    }

    public void stop(final BundleContext bundleContext) throws Exception
    {
        try
        {
            if (_jmxService != null)
            {
                if (LOGGER.isInfoEnabled())
                {
                    LOGGER.info("Stopping jmx plugin: " + _bundleName);
                }
                _jmxService.close();
            }

            if (_registeredServices != null)
            {
                unregisterServices();
            }
        }
        finally
        {
            _jmxService = null;
            _registeredServices = null;
        }
    }


    private List<ServiceRegistration> registerServices(BundleContext ctx)
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Registering jmx plugin: " + _bundleName);
        }

        List<ServiceRegistration> serviceRegistrations = new ArrayList<ServiceRegistration>();

        ServiceRegistration jmxServiceRegistration = ctx.registerService(JMXService.class.getName(), _jmxService, null);
        ServiceRegistration jmxConfigFactoryRegistration = ctx.registerService(ConfigurationPluginFactory.class.getName(), JMXConfiguration.FACTORY, null);

        serviceRegistrations.add(jmxServiceRegistration);
        serviceRegistrations.add(jmxConfigFactoryRegistration);
        return serviceRegistrations;
    }

    private void startJmsService(JMXService jmxService) throws Exception
    {
        if (LOGGER.isInfoEnabled())
        {
            LOGGER.info("Starting JMX service");
        }
        boolean startedSuccessfully = false;
        try
        {
            jmxService.start();
            startedSuccessfully = true;
        }
        finally
        {
            if (!startedSuccessfully)
            {
                LOGGER.error("JMX failed to start normally, closing service");
                jmxService.close();
            }
        }
    }

    private void unregisterServices()
    {
        for (Iterator<ServiceRegistration> iterator = _registeredServices.iterator(); iterator.hasNext();)
        {
            ServiceRegistration service = iterator.next();
            service.unregister();
        }
    }
}
