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

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class JMXActivator implements  BundleActivator
{
    private static final Logger _logger = Logger.getLogger(JMXActivator.class);


    private BundleContext _ctx;
    private String _bundleName;
    private JMXService _jmxService;


    public void start(final BundleContext ctx) throws Exception
    {
        _ctx = ctx;
        _jmxService = new JMXService();
        _jmxService.start();
        _bundleName = ctx.getBundle().getSymbolicName();

        // register the service
        _logger.info("Registering jmx plugin: " + _bundleName);
        _ctx.registerService(JMXService.class.getName(), _jmxService, null);
        _ctx.registerService(ConfigurationPluginFactory.class.getName(), JMXConfiguration.FACTORY, null);
    }

    public void stop(final BundleContext bundleContext) throws Exception
    {
        _logger.info("Stopping jmx plugin: " + _bundleName);
        try
        {
            _jmxService.close();
        }
        finally
        {
            // null object references
            _jmxService = null;
            _ctx = null;
        }
    }
}
