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
    private static final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>
            PARENTS = new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();


    private static final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>
            CHILDREN = new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();

    static void addRelationship(Class<? extends ConfiguredObject> parent, Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parents = PARENTS.get(child);
        if(parents == null)
        {
            parents = new ArrayList<Class<? extends ConfiguredObject>>();
            PARENTS.put(child, parents);
        }
        parents.add(parent);

        Collection<Class<? extends ConfiguredObject>> children = CHILDREN.get(parent);
        if(children == null)
        {
            children = new ArrayList<Class<? extends ConfiguredObject>>();
            CHILDREN.put(parent, children);
        }
        children.add(child);
    }

    static
    {
        addRelationship(Broker.class, VirtualHost.class);
        addRelationship(Broker.class, Port.class);

        addRelationship(VirtualHost.class, Exchange.class);
        addRelationship(VirtualHost.class, Queue.class);
        addRelationship(VirtualHost.class, Connection.class);
        addRelationship(VirtualHost.class, VirtualHostAlias.class);

        addRelationship(Connection.class, Session.class);

        addRelationship(Exchange.class, Binding.class);
        addRelationship(Exchange.class, Publisher.class);

        addRelationship(Queue.class, Binding.class);
        addRelationship(Queue.class, Consumer.class);

        addRelationship(Session.class, Consumer.class);
        addRelationship(Session.class, Publisher.class);

    }

    public static Collection<Class<? extends ConfiguredObject>> getParentTypes(Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parentTypes = PARENTS.get(child);
        return parentTypes == null ? Collections.EMPTY_LIST
                                   : Collections.unmodifiableCollection(parentTypes);
    }

    public static Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent)
    {
        Collection<Class<? extends ConfiguredObject>> childTypes = CHILDREN.get(parent);
        return childTypes == null ? Collections.EMPTY_LIST
                                  : Collections.unmodifiableCollection(childTypes);
    }
}
