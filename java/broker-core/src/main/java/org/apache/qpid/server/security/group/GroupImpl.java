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
package org.apache.qpid.server.security.group;

import java.util.Map;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;

@ManagedObject(category = false, type = GroupImpl.CONFIG_TYPE)
public class GroupImpl extends AbstractConfiguredObject<GroupImpl> implements Group<GroupImpl>
{

    public static final String CONFIG_TYPE = "ManagedGroup";

    private GroupPrincipal _groupPrincipal;

    @ManagedObjectFactoryConstructor
    public GroupImpl(Map<String, Object> attributes,
                     GroupProvider<?> provider)
    {
        super(parentsMap(provider), attributes);
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _groupPrincipal = new GroupPrincipal(getName());
    }

    @Override
    protected <C extends ConfiguredObject> ListenableFuture<C> addChildAsync(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        if(childClass == GroupMember.class)
        {
            return getObjectFactory().createAsync(childClass, attributes, this);
        }
        else
        {
            return super.addChildAsync(childClass, attributes, otherParents);
        }
    }

    @Override
    public GroupPrincipal getGroupPrincipal()
    {
        return _groupPrincipal;
    }


    @StateTransition( currentState = { State.UNINITIALIZED, State.QUIESCED, State.ERRORED }, desiredState = State.ACTIVE )
    private ListenableFuture<Void> activate()
    {
        setState(State.ACTIVE);
        return Futures.immediateFuture(null);
    }


    @StateTransition(currentState = {State.ACTIVE}, desiredState = State.DELETED)
    private ListenableFuture<Void> doDelete()
    {
        deleted();
        return Futures.immediateFuture(null);
    }

}
