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
package org.apache.qpid.server.management.plugin;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class ManagementActivator implements  BundleActivator
{
    private static final Logger _logger = Logger.getLogger(ManagementActivator.class);


    private BundleContext _ctx;
    private String _bundleName;
    private Management _managementService;


    public void start(final BundleContext ctx) throws Exception
    {
        _ctx = ctx;
        _managementService = new Management();
        _bundleName = ctx.getBundle().getSymbolicName();

        // register the service
        _logger.info("Registering management plugin: " + _bundleName);
        _ctx.registerService(Management.class.getName(), _managementService, null);
        _ctx.registerService(ConfigurationPluginFactory.class.getName(), ManagementConfiguration.FACTORY, null);
    }

    public void stop(final BundleContext bundleContext) throws Exception
    {
        _logger.info("Stopping management plugin: " + _bundleName);

	    // null object references
	    _managementService = null;
		_ctx = null;
    }
}
