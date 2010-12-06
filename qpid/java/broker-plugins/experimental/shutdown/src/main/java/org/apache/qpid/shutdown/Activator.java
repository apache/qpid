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

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{
    private static final Logger _logger = Logger.getLogger(Activator.class);

    private static final String SHUTDOWN_MBEAN_NAME = "org.apache.qpid:type=ShutdownMBean";

    /** @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext) */
    public void start(BundleContext ctx) throws Exception {
        Shutdown shutdown = new Shutdown();
        if (ctx != null)
        {
            ctx.registerService(ShutdownMBean.class.getName(), shutdown, null);
        }

        // MBean registration
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(SHUTDOWN_MBEAN_NAME);
        mbs.registerMBean(shutdown, name);

        _logger.info("Shutdown plugin MBean registered");
    }

    /** @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext) */
    public void stop(BundleContext ctx) throws Exception
    {
        // Unregister MBean
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(SHUTDOWN_MBEAN_NAME);
        try
        {
            mbs.unregisterMBean(name);
        }
        catch (InstanceNotFoundException e)
        {
            //ignore
        }

        _logger.info("Shutdown plugin MBean unregistered");
    }
}
