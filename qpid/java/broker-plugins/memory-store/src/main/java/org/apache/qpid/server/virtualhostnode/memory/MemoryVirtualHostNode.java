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
package org.apache.qpid.server.virtualhostnode.memory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MemoryConfigurationStore;
import org.apache.qpid.server.virtualhostnode.AbstractStandardVirtualHostNode;

@ManagedObject(type=MemoryVirtualHostNode.VIRTUAL_HOST_NODE_TYPE, category=false, validChildTypes = "org.apache.qpid.server.virtualhostnode.memory.MemoryVirtualHostNode#getSupportedChildTypes()")
public class MemoryVirtualHostNode extends AbstractStandardVirtualHostNode<MemoryVirtualHostNode>
{
   public static final String VIRTUAL_HOST_NODE_TYPE = "Memory";

    @ManagedObjectFactoryConstructor
    public MemoryVirtualHostNode(Map<String, Object> attributes, Broker<?> parent)
    {
        super(attributes, parent);
    }

    @Override
    protected void writeLocationEventLog()
    {
    }

    @Override
    protected DurableConfigurationStore createConfigurationStore()
    {
        return new MemoryConfigurationStore(VirtualHost.class);
    }

    public static Map<String, Collection<String>> getSupportedChildTypes()
    {
        return Collections.singletonMap(VirtualHost.class.getSimpleName(), getSupportedVirtualHostTypes(true));
    }
}
