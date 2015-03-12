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

import java.security.Principal;
import java.util.Map;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;
import org.apache.qpid.server.security.auth.UsernamePrincipal;

@ManagedObject(category = false, type = GroupMemberImpl.CONFIG_TYPE)
public class GroupMemberImpl extends AbstractConfiguredObject<GroupMemberImpl> implements GroupMember<GroupMemberImpl>
{
    public static final String CONFIG_TYPE = "ManagedGroupMember";
    private UsernamePrincipal _principal;


    @ManagedObjectFactoryConstructor
    public GroupMemberImpl(Map<String, Object> attributes,
                           Group group)
    {
        super(parentsMap(group), attributes);
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _principal = new UsernamePrincipal(getName());
    }

    @Override
    public Principal getPrincipal()
    {
        return _principal;
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
