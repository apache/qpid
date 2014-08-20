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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.AuthenticationMethod;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;

public class VirtualHostAliasAdapter extends AbstractConfiguredObject<VirtualHostAliasAdapter> implements VirtualHostAlias<VirtualHostAliasAdapter>
{
    private VirtualHost _vhost;
    private Port _port;

    public VirtualHostAliasAdapter(VirtualHost virtualHost, Port port)
    {
        super(parentsMap(virtualHost,port), createAttributes(virtualHost, port));
        _vhost = virtualHost;
        _port = port;
    }

    private static Map<String, Object> createAttributes(final VirtualHost virtualHost, final Port port)
    {
        final Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, virtualHost.getName());
        attributes.put(DURABLE, false);
        return attributes;
    }

    @Override
    public Port getPort()
    {
        return _port;
    }

    @Override
    public VirtualHost getVirtualHost()
    {
        return _vhost;
    }

    @Override
    public Collection<AuthenticationMethod> getAuthenticationMethods()
    {
        return Collections.emptySet();  // TODO - Implement
    }

    @Override
    public State getState()
    {
        return State.ACTIVE;  // TODO - Implement
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }


}
