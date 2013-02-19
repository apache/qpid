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
package org.apache.qpid.server.model.adapter;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.TrustStore;

public class TrustStoreAdapter extends AbstractKeyStoreAdapter implements TrustStore
{
    public TrustStoreAdapter(UUID id, Broker broker, Map<String, Object> attributes)
    {
        super(id, broker, attributes);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

}
