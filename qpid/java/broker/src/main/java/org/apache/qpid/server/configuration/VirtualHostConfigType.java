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

package org.apache.qpid.server.configuration;

import java.util.*;

public class VirtualHostConfigType extends ConfigObjectType<VirtualHostConfigType, VirtualHostConfig>
{
    private static final List<VirtualHostProperty<?>> VIRTUAL_HOST_PROPERTIES = new ArrayList<VirtualHostProperty<?>>();
    private static final VirtualHostConfigType INSTANCE = new VirtualHostConfigType();
public static interface VirtualHostProperty<S> extends ConfigProperty<VirtualHostConfigType, VirtualHostConfig, S>
    {
    }

    private abstract static class VirtualHostReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<VirtualHostConfigType, VirtualHostConfig, S> implements VirtualHostProperty<S>
    {
        public VirtualHostReadWriteProperty(String name)
        {
            super(name);
            VIRTUAL_HOST_PROPERTIES.add(this);
        }
    }

    private abstract static class VirtualHostReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<VirtualHostConfigType, VirtualHostConfig, S> implements VirtualHostProperty<S>
    {
        public VirtualHostReadOnlyProperty(String name)
        {
            super(name);
            VIRTUAL_HOST_PROPERTIES.add(this);
        }
    }


    public static final VirtualHostReadOnlyProperty<String> NAME_PROPERTY = new VirtualHostReadOnlyProperty<String>("name")
    {
        public String getValue(VirtualHostConfig object)
        {
            return object.getName();
        }
    };


    public static final VirtualHostReadOnlyProperty<BrokerConfig> BROKER_PROPERTY = new VirtualHostReadOnlyProperty<BrokerConfig>("broker")
    {
        public BrokerConfig getValue(VirtualHostConfig object)
        {
            return object.getBroker();
        }
    };

    public static final VirtualHostReadOnlyProperty<String> FEDERATION_TAG_PROPERTY = new VirtualHostReadOnlyProperty<String>("federationTag")
    {
        public String getValue(VirtualHostConfig object)
        {
            return object.getFederationTag();
        }
    };



    public Collection<? extends ConfigProperty<VirtualHostConfigType, VirtualHostConfig, ?>> getProperties()
    {
        return Collections.unmodifiableList(VIRTUAL_HOST_PROPERTIES);
    }


    private VirtualHostConfigType()
    {
    }

    public static VirtualHostConfigType getInstance()
    {
        return INSTANCE;
    }


}
