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

package org.apache.qpid.server.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Model
{
    private static final Model MODEL_INSTANCE = new Model();

    private final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>
            _parents = new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();

    private final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>
            _children = new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();

    public static Model getInstance()
    {
        return MODEL_INSTANCE;
    }

    private Model()
    {
        addRelationship(Broker.class, VirtualHost.class);
        addRelationship(Broker.class, Port.class);
        addRelationship(Broker.class, AuthenticationProvider.class);
        addRelationship(Broker.class, GroupProvider.class);
        addRelationship(Broker.class, TrustStore.class);
        addRelationship(Broker.class, KeyStore.class);
        addRelationship(Broker.class, Plugin.class);

        addRelationship(VirtualHost.class, Exchange.class);
        addRelationship(VirtualHost.class, Queue.class);
        addRelationship(VirtualHost.class, Connection.class);
        addRelationship(VirtualHost.class, VirtualHostAlias.class);

        addRelationship(AuthenticationProvider.class, User.class);
        addRelationship(User.class, GroupMember.class);

        addRelationship(GroupProvider.class, Group.class);
        addRelationship(Group.class, GroupMember.class);

        addRelationship(Connection.class, Session.class);

        addRelationship(Exchange.class, Binding.class);
        addRelationship(Exchange.class, Publisher.class);

        addRelationship(Queue.class, Binding.class);
        addRelationship(Queue.class, Consumer.class);

        addRelationship(Session.class, Consumer.class);
        addRelationship(Session.class, Publisher.class);
    }

    public Collection<Class<? extends ConfiguredObject>> getParentTypes(Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parentTypes = _parents.get(child);
        return parentTypes == null ? Collections.<Class<? extends ConfiguredObject>>emptyList()
                                   : Collections.unmodifiableCollection(parentTypes);
    }

    public Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent)
    {
        Collection<Class<? extends ConfiguredObject>> childTypes = _children.get(parent);
        return childTypes == null ? Collections.<Class<? extends ConfiguredObject>>emptyList()
                                  : Collections.unmodifiableCollection(childTypes);
    }

    private void addRelationship(Class<? extends ConfiguredObject> parent, Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parents = _parents.get(child);
        if(parents == null)
        {
            parents = new ArrayList<Class<? extends ConfiguredObject>>();
            _parents.put(child, parents);
        }
        parents.add(parent);

        Collection<Class<? extends ConfiguredObject>> children = _children.get(parent);
        if(children == null)
        {
            children = new ArrayList<Class<? extends ConfiguredObject>>();
            _children.put(parent, children);
        }
        children.add(child);
    }

}
