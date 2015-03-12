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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.RemoteReplicationNode;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.DurableConfigurationStore;


public class RedirectingVirtualHostNodeImpl
        extends AbstractConfiguredObject<RedirectingVirtualHostNodeImpl> implements RedirectingVirtualHostNode<RedirectingVirtualHostNodeImpl>
{
    private static final Logger LOGGER = LoggerFactory.getLogger(RedirectingVirtualHostImpl.class);
    public static final String VIRTUAL_HOST_NODE_TYPE = "Redirector";


    @ManagedAttributeField
    private String _virtualHostInitialConfiguration;

    @ManagedAttributeField
    private Map<Port<?>,String> _redirects;

    private RedirectingVirtualHostImpl _virtualHost;

    @ManagedObjectFactoryConstructor
    public RedirectingVirtualHostNodeImpl(Map<String, Object> attributes, Broker<?> parent)
    {
        super(Collections.<Class<? extends ConfiguredObject>,ConfiguredObject<?>>singletonMap(Broker.class, parent),
              attributes);
    }

    @StateTransition( currentState = {State.UNINITIALIZED, State.STOPPED, State.ERRORED }, desiredState = State.ACTIVE )
    protected ListenableFuture<Void> doActivate()
    {
        try
        {
            _virtualHost = new RedirectingVirtualHostImpl(Collections.<String,Object>singletonMap(ConfiguredObject.NAME,getName()), this);
            _virtualHost.create();
            setState(State.ACTIVE);
        }
        catch(RuntimeException e)
        {
            setState(State.ERRORED);
            if (getParent(Broker.class).isManagementMode())
            {
                LOGGER.warn("Failed to make " + this + " active.", e);
            }
            else
            {
                throw e;
            }
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public String getVirtualHostInitialConfiguration()
    {
        return _virtualHostInitialConfiguration;
    }

    @Override
    public VirtualHost<?, ?, ?> getVirtualHost()
    {
        return _virtualHost;
    }

    @Override
    public DurableConfigurationStore getConfigurationStore()
    {
        return null;
    }

    @Override
    public Collection<? extends RemoteReplicationNode> getRemoteReplicationNodes()
    {
        return Collections.emptySet();
    }

    @Override
    public Map<Port<?>, String> getRedirects()
    {
        return _redirects;
    }

    public static Map<String, Collection<String>> getSupportedChildTypes()
    {
        Collection<String> validVhostTypes = Collections.singleton(RedirectingVirtualHostImpl.TYPE);
        return Collections.singletonMap(VirtualHost.class.getSimpleName(), validVhostTypes);
    }

}
