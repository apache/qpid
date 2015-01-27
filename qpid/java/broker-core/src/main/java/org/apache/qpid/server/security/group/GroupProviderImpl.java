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
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Group;
import org.apache.qpid.server.model.GroupMember;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.StateTransition;

@ManagedObject(category = false, type = GroupProviderImpl.CONFIG_TYPE)
public class GroupProviderImpl extends AbstractConfiguredObject<GroupProviderImpl> implements GroupProvider<GroupProviderImpl>
{

    public static final String CONFIG_TYPE = "ManagedGroupProvider";

    @ManagedObjectFactoryConstructor
    public GroupProviderImpl(Map<String, Object> attributes,
                                      Broker broker)
    {
        super(parentsMap(broker), attributes);
    }


    @Override
    public Set<Principal> getGroupPrincipalsForUser(final String username)
    {
        Set<Principal> principals = new HashSet<>();

        final Collection<Group> groups = getChildren(Group.class);
        for(Group<?> group : groups)
        {
            for(GroupMember<?> member : group.getChildren(GroupMember.class))
            {
                if(member.getPrincipal().getName().equals(username))
                {
                    principals.add(group.getGroupPrincipal());
                }
            }
        }
        return principals;
    }

    @Override
    protected <C extends ConfiguredObject> C addChild(final Class<C> childClass,
                                                      final Map<String, Object> attributes,
                                                      final ConfiguredObject... otherParents)
    {
        if(childClass == Group.class)
        {
            C child = (C) getObjectFactory().create(childClass, attributes, this);

            return child;
        }
        else
        {
            return super.addChild(childClass, attributes, otherParents);
        }
    }

    @StateTransition( currentState = { State.UNINITIALIZED, State.QUIESCED, State.ERRORED }, desiredState = State.ACTIVE )
    private void activate()
    {
        setState(State.ACTIVE);
    }


    @StateTransition(currentState = {State.ACTIVE}, desiredState = State.DELETED)
    private void doDelete()
    {
        deleted();
    }

}
