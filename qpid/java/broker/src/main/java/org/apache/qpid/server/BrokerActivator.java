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
package org.apache.qpid.server;

import org.apache.log4j.Logger;
import org.apache.qpid.BrokerOptions;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class BrokerActivator implements BundleActivator
{
    private static final Logger _logger = Logger.getLogger(BrokerActivator.class);
    
    private BundleContext _context = null;
    private BrokerInstance _instance = null;
    
    public void start(BundleContext ctx) throws Exception
    {
        _context = ctx;
        _logger.info("Starting broker: " + _context.getBundle().getSymbolicName());
        
        BrokerOptions options = new BrokerOptions();
        options.setBind("*");
        options.setPorts(BrokerOptions.DEFAULT_PORT);
        
        _instance = new BrokerInstance();
        _instance.startup(options);
        
        _context.registerService(BrokerInstance.class.getName(), _instance, null);
    }

    public void stop(BundleContext ctx) throws Exception
    {
        _logger.info("Stopping broker: " + _context.getBundle().getSymbolicName());
        _instance.shutdown();
        
        _context = null;
        _instance = null;
    }

    public BundleContext getContext()
    {
        return _context;
    }
}
