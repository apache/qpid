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
package org.apache.qpid.server.virtualhostnode;

import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;

@ManagedObject(type="TestMemory", category=false)
public class TestVirtualHostNode extends AbstractStandardVirtualHostNode<TestVirtualHostNode>
{
    private final DurableConfigurationStore _store;

    public TestVirtualHostNode(Broker<?> parent, Map<String, Object> attributes)
    {
        this(parent, attributes, null);
    }

    public TestVirtualHostNode(Broker<?> parent,
                               Map<String, Object> attributes,
                               DurableConfigurationStore store)
    {
        super(attributes, parent);
        _store = store;
    }

    @Override
    protected DurableConfigurationStore createConfigurationStore()
    {
        return _store;
    }

    @Override
    public Map<String, Object> getDefaultMessageStoreSettings()
    {
        return Collections.<String, Object>singletonMap(MessageStore.STORE_TYPE, TestMemoryMessageStore.TYPE);
    }
}
