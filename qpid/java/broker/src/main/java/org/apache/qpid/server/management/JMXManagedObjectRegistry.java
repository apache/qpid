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
package org.apache.qpid.server.management;

import java.lang.management.ManagementFactory;

import javax.management.JMException;
import javax.management.MBeanServer;

import org.apache.log4j.Logger;

public class JMXManagedObjectRegistry implements ManagedObjectRegistry
{
    private static final Logger _log = Logger.getLogger(JMXManagedObjectRegistry.class);

    private final MBeanServer _mbeanServer;

    public JMXManagedObjectRegistry()
    {
        _log.info("Initialising managed object registry using platform MBean server");
        // we use the platform MBean server currently but this must be changed or at least be configuurable
        _mbeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    public void registerObject(ManagedObject managedObject) throws JMException
    {
         _mbeanServer.registerMBean(managedObject, managedObject.getObjectName());
    }

    public void unregisterObject(ManagedObject managedObject) throws JMException
    {
        _mbeanServer.unregisterMBean(managedObject.getObjectName());
    }

}
