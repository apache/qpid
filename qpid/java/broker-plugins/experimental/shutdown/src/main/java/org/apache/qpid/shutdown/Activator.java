/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.shutdown;


import org.apache.log4j.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{
    private static final Logger _logger = Logger.getLogger(Activator.class);

    private Shutdown _shutdown = null;

    /** @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext) */
    public void start(BundleContext ctx) throws Exception {
        /*_shutdown = new Shutdown();
        if (ctx != null)
        {
            ctx.registerService(ShutdownMBean.class.getName(), _shutdown, null);
        }

        _shutdown.register();*/

        _logger.info("Shutdown plugin MBean registered");
    }

    /** @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext) */
    public void stop(BundleContext ctx) throws Exception
    {
    /*
        if (_shutdown != null)
        {
            _shutdown.unregister();
            _shutdown = null;
        }*/

        _logger.info("Shutdown plugin MBean unregistered");
    }
}
