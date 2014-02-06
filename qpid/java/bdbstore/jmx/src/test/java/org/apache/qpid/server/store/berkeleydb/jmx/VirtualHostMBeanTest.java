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
 *
 */
package org.apache.qpid.server.store.berkeleydb.jmx;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import junit.framework.TestCase;

import org.apache.qpid.server.jmx.DefaultManagedObject;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.jmx.mbeans.VirtualHostMBean;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.berkeleydb.replication.LocalReplicationNode;

public class VirtualHostMBeanTest extends TestCase
{
    private VirtualHost _mockVirtualHost;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private VirtualHostMBean _virtualHostMBean;
    private LocalReplicationNode _mockLocalReplicationNode;

    @Override
    protected void setUp() throws Exception
    {
        _mockVirtualHost = mock(VirtualHost.class);
        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        _mockLocalReplicationNode = mock(LocalReplicationNode.class);
        when(_mockLocalReplicationNode.getParent(VirtualHost.class)).thenReturn(_mockVirtualHost);
        when(_mockVirtualHost.getName()).thenReturn("vhost");

        _virtualHostMBean = new VirtualHostMBean(_mockVirtualHost, _mockManagedObjectRegistry);
    }

    public void testAdditionalMbeanRegistered_LocalReplicationNodeAdded() throws Exception
    {
        _virtualHostMBean.childAdded(_mockVirtualHost, _mockLocalReplicationNode);

        verify(_mockManagedObjectRegistry, times(2)).registerObject(any(DefaultManagedObject.class));
    }

    public void testAdditionalMbeanUnregisteredOnUnregisterOfThisMbean() throws Exception
    {
        _virtualHostMBean.childAdded(_mockVirtualHost, _mockLocalReplicationNode);
        _virtualHostMBean.unregister();

        verify(_mockManagedObjectRegistry, times(2)).unregisterObject(any(DefaultManagedObject.class));
    }

    public void testAdditionalMbeanUnregistered_LocalReplicationNodeRemoved() throws Exception
    {
        _virtualHostMBean.childAdded(_mockVirtualHost, _mockLocalReplicationNode);

        _virtualHostMBean.childRemoved(_mockVirtualHost, _mockLocalReplicationNode);
        verify(_mockManagedObjectRegistry).unregisterObject(any(BDBHAMessageStoreManagerMBean.class));
    }
}
