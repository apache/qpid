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

import org.apache.qpid.server.model.AbstractConfiguredObjectTypeFactory;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BDBHAVirtualHostAdapterFactory extends AbstractConfiguredObjectTypeFactory<BDBHAVirtualHostAdapter>
{

    public BDBHAVirtualHostAdapterFactory()
    {
        super(BDBHAVirtualHostAdapter.class);
    }

    @Override
    public BDBHAVirtualHostAdapter createInstance(final Map<String, Object> attributes,
                                                 final ConfiguredObject<?>... parents)
    {
        Map<String,Object> attributesWithoutId = new HashMap<String, Object>(attributes);
        Object idObj = attributesWithoutId.remove(ConfiguredObject.ID);
        UUID id = idObj == null ? UUID.randomUUID() : idObj instanceof UUID ? (UUID) idObj : UUID.fromString(idObj.toString());
        final Broker broker = getParent(Broker.class, parents);
        return new BDBHAVirtualHostAdapter(id, attributesWithoutId, broker);
    }


}
