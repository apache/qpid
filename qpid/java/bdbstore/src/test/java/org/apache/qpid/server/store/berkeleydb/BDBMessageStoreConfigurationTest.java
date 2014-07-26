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
package org.apache.qpid.server.store.berkeleydb;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.store.AbstractDurableConfigurationStoreTestCase;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.virtualhostnode.berkeleydb.BDBVirtualHostNode;

public class BDBMessageStoreConfigurationTest extends AbstractDurableConfigurationStoreTestCase
{
    @Override
    protected VirtualHostNode createVirtualHostNode(String storeLocation, ConfiguredObjectFactory factory)
    {
        final BDBVirtualHostNode parent = mock(BDBVirtualHostNode.class);
        when(parent.getStorePath()).thenReturn(storeLocation);
        return parent;
    }

    @Override
    protected DurableConfigurationStore createConfigStore() throws Exception
    {
        return new BDBConfigurationStore(VirtualHost.class);
    }
}
