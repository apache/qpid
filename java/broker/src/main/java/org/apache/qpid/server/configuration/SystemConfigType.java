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

public final class SystemConfigType extends ConfigObjectType<SystemConfigType, SystemConfig>
{
    private static final List<SystemProperty<?>> SYSTEM_PROPERTIES = new ArrayList<SystemProperty<?>>();

    public static interface SystemProperty<S> extends ConfigProperty<SystemConfigType, SystemConfig, S>
    {
    }

    private abstract static class SystemReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<SystemConfigType, SystemConfig, S> implements SystemProperty<S>
    {
        public SystemReadWriteProperty(String name)
        {
            super(name);
            SYSTEM_PROPERTIES.add(this);
        }
    }

    private abstract static class SystemReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<SystemConfigType, SystemConfig, S> implements SystemProperty<S>
    {
        public SystemReadOnlyProperty(String name)
        {
            super(name);
            SYSTEM_PROPERTIES.add(this);
        }
    }

    public static final SystemReadOnlyProperty<String> NAME_PROPERTY = new SystemReadOnlyProperty<String>("name")
    {
        public String getValue(SystemConfig object)
        {
            return object.getName();
        }
    };

    public static final SystemReadOnlyProperty<UUID> ID_PROPERTY = new SystemReadOnlyProperty<UUID>("id")
    {
        public UUID getValue(SystemConfig object)
        {
            return object.getId();
        }
    };

    public static final SystemReadOnlyProperty<String> OS_NAME_PROPERTY = new SystemReadOnlyProperty<String>("osName")
    {
        public String getValue(SystemConfig object)
        {
            return object.getOperatingSystemName();
        }
    };

    public static final SystemReadOnlyProperty<String> NODE_NAME_PROPERTY = new SystemReadOnlyProperty<String>("nodeName")
    {
        public String getValue(SystemConfig object)
        {
            return object.getNodeName();
        }
    };

    public static final SystemReadOnlyProperty<String> RELEASE_PROPERTY = new SystemReadOnlyProperty<String>("release")
    {
        public String getValue(SystemConfig object)
        {
            return object.getOSRelease();
        }
    };

    public static final SystemReadOnlyProperty<String> VERSION_PROPERTY = new SystemReadOnlyProperty<String>("version")
    {
        public String getValue(SystemConfig object)
        {
            return object.getOSVersion();
        }
    };

    public static final SystemReadOnlyProperty<String> MACHINE_PROPERTY = new SystemReadOnlyProperty<String>("machine")
    {
        public String getValue(SystemConfig object)
        {
            return object.getOSArchitecture();
        }
    };

    private static final SystemConfigType INSTANCE = new SystemConfigType();

    private SystemConfigType()
    {
    }

    public Collection<SystemProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(SYSTEM_PROPERTIES);
    }



    public static SystemConfigType getInstance()
    {
        return INSTANCE;
    }



}
