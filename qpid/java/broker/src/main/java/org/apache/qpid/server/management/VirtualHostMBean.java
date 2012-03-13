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

import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.virtualhost.ManagedVirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

/**
 * Virtual host JMX MBean class.
 *
 * This has some of the methods implemented from management intrerface for exchanges. Any
 * implementaion of an Exchange MBean should extend this class.
 */
public class VirtualHostMBean extends AMQManagedObject implements ManagedVirtualHost
{
    private VirtualHostImpl _virtualHost;

    public VirtualHostMBean(VirtualHostImpl virtualHost) throws NotCompliantMBeanException
    {
        super(ManagedVirtualHost.class, ManagedVirtualHost.TYPE);
        _virtualHost = virtualHost;
    }

    public String getObjectInstanceName()
    {
        return ObjectName.quote(_virtualHost.getName());
    }

    public String getName()
    {
        return _virtualHost.getName();
    }

    public VirtualHostImpl getVirtualHost()
    {
        return _virtualHost;
    }
}
