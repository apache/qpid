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

import java.util.Map;
import org.apache.qpid.server.model.AuthenticationMethod;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;

import java.security.AccessControlException;
import java.util.Collection;
import java.util.Collections;

public class VirtualHostAliasAdapter  extends AbstractAdapter implements VirtualHostAlias
{
    private VirtualHostAdapter _vhost;
    private Port _port;

    public VirtualHostAliasAdapter(VirtualHostAdapter virtualHostAdapter, Port port)
    {
        super(UUIDGenerator.generateVhostAliasUUID(virtualHostAdapter.getName(), port.getName()), virtualHostAdapter.getTaskExecutor());
        _vhost = virtualHostAdapter;
        _port = port;
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
    public String getName()
    {
        return _vhost.getName();
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();  // TODO - Implement
    }

    @Override
    public State getActualState()
    {
        return State.ACTIVE;  // TODO - Implement
    }

    @Override
    public boolean isDurable()
    {
        return true;  // TODO - Implement
    }

    @Override
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;  // TODO - Implement
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();  // TODO - Implement
    }

    @Override
    public long getTimeToLive()
    {
        return 0;  // TODO - Implement
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();  // TODO - Implement
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        return Collections.emptySet();
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        // TODO: state is not supported at the moment
        return false;
    }
}
