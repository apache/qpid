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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BrokerModel extends Model
{

    /*
     * API version for the broker model
     *
     * 1.0 Initial version
     * 1.1 Addition of mandatory virtual host type / different types of virtual host
     * 1.3 Truststore/Keystore type => trustStoreType / type => keyStoreType
     * 1.4 Separate messageStoreSettings from virtualhost
     * 2.0 Introduce VirtualHostNode as a child of a Broker instead of VirtualHost
     */
    public static final int MODEL_MAJOR_VERSION = 2;
    public static final int MODEL_MINOR_VERSION = 0;
    public static final String MODEL_VERSION = MODEL_MAJOR_VERSION + "." + MODEL_MINOR_VERSION;
    private static final Model MODEL_INSTANCE = new BrokerModel();
    private final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>> _parents =
            new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();

    private final Map<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>> _children =
            new HashMap<Class<? extends ConfiguredObject>, Collection<Class<? extends ConfiguredObject>>>();

    private final Set<Class<? extends ConfiguredObject>> _supportedTypes =
            new HashSet<Class<? extends ConfiguredObject>>();

    private Class<? extends ConfiguredObject> _rootCategory;
    private final ConfiguredObjectFactory _objectFactory;

    private BrokerModel()
    {
        setRootCategory(SystemConfig.class);

        addRelationship(SystemConfig.class, Broker.class);

        addRelationship(Broker.class, VirtualHostNode.class);
        addRelationship(Broker.class, Port.class);
        addRelationship(Broker.class, AccessControlProvider.class);
        addRelationship(Broker.class, AuthenticationProvider.class);
        addRelationship(Broker.class, GroupProvider.class);
        addRelationship(Broker.class, TrustStore.class);
        addRelationship(Broker.class, KeyStore.class);
        addRelationship(Broker.class, Plugin.class);

        addRelationship(VirtualHostNode.class, VirtualHost.class);
        addRelationship(VirtualHostNode.class, RemoteReplicationNode.class);

        addRelationship(VirtualHost.class, Exchange.class);
        addRelationship(VirtualHost.class, Queue.class);
        addRelationship(VirtualHost.class, Connection.class);
        addRelationship(VirtualHost.class, VirtualHostAlias.class);

        addRelationship(Port.class, VirtualHostAlias.class);


        addRelationship(AuthenticationProvider.class, User.class);
        addRelationship(AuthenticationProvider.class, PreferencesProvider.class);
        addRelationship(User.class, GroupMember.class);

        addRelationship(GroupProvider.class, Group.class);
        addRelationship(Group.class, GroupMember.class);

        addRelationship(Connection.class, Session.class);

        addRelationship(Queue.class, Binding.class);
        addRelationship(Queue.class, Consumer.class);

        addRelationship(Exchange.class, Binding.class);
        addRelationship(Exchange.class, Publisher.class);

        addRelationship(Session.class, Consumer.class);
        addRelationship(Session.class, Publisher.class);

        _objectFactory = new ConfiguredObjectFactoryImpl(this);
    }

    public static Model getInstance()
    {
        return MODEL_INSTANCE;
    }

    @Override
    public Class<? extends ConfiguredObject> getRootCategory()
    {
        return _rootCategory;
    }

    public Collection<Class<? extends ConfiguredObject>> getParentTypes(Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parentTypes = _parents.get(child);
        return parentTypes == null ? Collections.<Class<? extends ConfiguredObject>>emptyList()
                : Collections.unmodifiableCollection(parentTypes);
    }

    @Override
    public int getMajorVersion()
    {
        return MODEL_MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion()
    {
        return MODEL_MINOR_VERSION;
    }

    @Override
    public ConfiguredObjectFactory getObjectFactory()
    {
        return _objectFactory;
    }

    public Collection<Class<? extends ConfiguredObject>> getChildTypes(Class<? extends ConfiguredObject> parent)
    {
        Collection<Class<? extends ConfiguredObject>> childTypes = _children.get(parent);
        return childTypes == null ? Collections.<Class<? extends ConfiguredObject>>emptyList()
                : Collections.unmodifiableCollection(childTypes);
    }

    @Override
    public Collection<Class<? extends ConfiguredObject>> getSupportedCategories()
    {
        return Collections.unmodifiableSet(_supportedTypes);
    }

    public void setRootCategory(final Class<? extends ConfiguredObject> rootCategory)
    {
        _rootCategory = rootCategory;
    }

    private void addRelationship(Class<? extends ConfiguredObject> parent, Class<? extends ConfiguredObject> child)
    {
        Collection<Class<? extends ConfiguredObject>> parents = _parents.get(child);
        if (parents == null)
        {
            parents = new ArrayList<Class<? extends ConfiguredObject>>();
            _parents.put(child, parents);
        }
        parents.add(parent);

        Collection<Class<? extends ConfiguredObject>> children = _children.get(parent);
        if (children == null)
        {
            children = new ArrayList<Class<? extends ConfiguredObject>>();
            _children.put(parent, children);
        }
        children.add(child);

        _supportedTypes.add(parent);
        _supportedTypes.add(child);

    }

}
