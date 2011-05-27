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
package org.apache.qpid.server.security;

import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.plugins.ConfigurationPluginFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * An OSGi {@link BundleActivator} that loads a {@link SecurityPluginFactory}.
 */
public abstract class SecurityPluginActivator implements BundleActivator
{
	private static final Logger _logger = Logger.getLogger(SecurityPluginActivator.class);

    private SecurityPluginFactory _factory;
    private ConfigurationPluginFactory _config;
    private BundleContext _ctx;
    private String _bundleName;
    
    /** Implement this to return the factory this plugin activates. */
    public abstract SecurityPluginFactory getFactory(); 
    
    /** Implement this to return the factory this plugin activates. */
    public abstract ConfigurationPluginFactory getConfigurationFactory(); 
    
	/**
     * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext ctx) throws Exception
    {
        _ctx = ctx;
        _factory = getFactory();
        _config = getConfigurationFactory();
        _bundleName = ctx.getBundle().getSymbolicName();

        // register the service
        _logger.info("Registering security plugin: " + _bundleName);
        _ctx.registerService(SecurityPluginFactory.class.getName(), _factory, null);
        _ctx.registerService(ConfigurationPluginFactory.class.getName(), _config, null);
    }

	/**
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception
    {
        _logger.info("Stopping security plugin: " + _bundleName);
        
	    // null object references
	    _factory = null;
	    _config = null;
		_ctx = null;
	}
}
