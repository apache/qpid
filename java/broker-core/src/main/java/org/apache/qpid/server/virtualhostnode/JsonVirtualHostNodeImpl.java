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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.apache.qpid.server.logging.messages.ConfigStoreMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.JsonFileConfigStore;

@ManagedObject(type=JsonVirtualHostNodeImpl.VIRTUAL_HOST_NODE_TYPE, category=false, validChildTypes = "org.apache.qpid.server.virtualhostnode.JsonVirtualHostNodeImpl#getSupportedChildTypes()")
public class JsonVirtualHostNodeImpl extends AbstractStandardVirtualHostNode<JsonVirtualHostNodeImpl> implements JsonVirtualHostNode<JsonVirtualHostNodeImpl>
{
    public static final String VIRTUAL_HOST_NODE_TYPE = "JSON";

    @ManagedAttributeField
    private String _storePath;

    @ManagedObjectFactoryConstructor
    public JsonVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> parent)
    {
        super(attributes, parent);
    }

    @Override
    protected void writeLocationEventLog()
    {
        getEventLogger().message(getConfigurationStoreLogSubject(), ConfigStoreMessages.STORE_LOCATION(getStorePath()));
    }

    @Override
    protected DurableConfigurationStore createConfigurationStore()
    {
        return new JsonFileConfigStore(VirtualHost.class);
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", storePath=" + getStorePath() + "]";
    }

    public static Map<String, Collection<String>> getSupportedChildTypes()
    {
        return Collections.singletonMap(VirtualHost.class.getSimpleName(), getSupportedVirtualHostTypes(false));
    }
}
