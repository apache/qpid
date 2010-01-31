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

import org.apache.qpid.server.exchange.ExchangeType;

import java.util.*;

public final class ConnectionConfigType extends ConfigObjectType<ConnectionConfigType, ConnectionConfig>
{
    private static final List<ConnectionProperty<?>> CONNECTION_PROPERTIES = new ArrayList<ConnectionProperty<?>>();

    public static interface ConnectionProperty<S> extends ConfigProperty<ConnectionConfigType, ConnectionConfig, S>
    {
    }

    private abstract static class ConnectionReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<ConnectionConfigType, ConnectionConfig, S> implements ConnectionProperty<S>
    {
        public ConnectionReadWriteProperty(String name)
        {
            super(name);
            CONNECTION_PROPERTIES.add(this);
        }
    }

    private abstract static class ConnectionReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<ConnectionConfigType, ConnectionConfig, S> implements ConnectionProperty<S>
    {
        public ConnectionReadOnlyProperty(String name)
        {
            super(name);
            CONNECTION_PROPERTIES.add(this);
        }
    }

    public static final ConnectionReadOnlyProperty<VirtualHostConfig> VIRTUAL_HOST_PROPERTY = new ConnectionReadOnlyProperty<VirtualHostConfig>("virtualHost")
    {
        public VirtualHostConfig getValue(ConnectionConfig object)
        {
            return object.getVirtualHost();
        }
    };

    public static final ConnectionReadOnlyProperty<String> ADDRESS_PROPERTY = new ConnectionReadOnlyProperty<String>("address")
    {
        public String getValue(ConnectionConfig object)
        {
            return object.getAddress();
        }
    };

    public static final ConnectionReadOnlyProperty<Boolean> INCOMING_PROPERTY = new ConnectionReadOnlyProperty<Boolean>("incoming")
    {
        public Boolean getValue(ConnectionConfig object)
        {
            return object.isIncoming();
        }
    };

    public static final ConnectionReadOnlyProperty<Boolean> SYSTEM_CONNECTION_PROPERTY = new ConnectionReadOnlyProperty<Boolean>("systemConnection")
    {
        public Boolean getValue(ConnectionConfig object)
        {
            return object.isSystemConnection();
        }
    };

    public static final ConnectionReadOnlyProperty<Boolean> FEDERATION_LINK_PROPERTY = new ConnectionReadOnlyProperty<Boolean>("federationLink")
    {
        public Boolean getValue(ConnectionConfig object)
        {
            return object.isFederationLink();
        }
    };

    public static final ConnectionReadOnlyProperty<String> AUTH_ID_PROPERTY = new ConnectionReadOnlyProperty<String>("authId")
    {
        public String getValue(ConnectionConfig object)
        {
            return object.getAuthId();
        }
    };

    public static final ConnectionReadOnlyProperty<String> REMOTE_PROCESS_NAME_PROPERTY = new ConnectionReadOnlyProperty<String>("remoteProcessName")
    {
        public String getValue(ConnectionConfig object)
        {
            return object.getRemoteProcessName();
        }
    };


    public static final ConnectionReadOnlyProperty<Integer> REMOTE_PID_PROPERTY = new ConnectionReadOnlyProperty<Integer>("remotePid")
    {
        public Integer getValue(ConnectionConfig object)
        {
            return object.getRemotePID();
        }
    };

    public static final ConnectionReadOnlyProperty<Integer> REMOTE_PARENT_PID_PROPERTY = new ConnectionReadOnlyProperty<Integer>("remoteParentPid")
    {
        public Integer getValue(ConnectionConfig object)
        {
            return object.getRemoteParentPID();
        }
    };

    private static final ConnectionConfigType INSTANCE = new ConnectionConfigType();

    private ConnectionConfigType()
    {
    }

    public Collection<ConnectionProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(CONNECTION_PROPERTIES);
    }

    public static ConnectionConfigType getInstance()
    {
        return INSTANCE;
    }



}