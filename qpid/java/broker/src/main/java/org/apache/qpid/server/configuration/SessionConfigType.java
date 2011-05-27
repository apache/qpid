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

public final class SessionConfigType extends ConfigObjectType<SessionConfigType, SessionConfig>
{
    private static final List<SessionProperty<?>> SESSION_PROPERTIES = new ArrayList<SessionProperty<?>>();

    public static interface SessionProperty<S> extends ConfigProperty<SessionConfigType, SessionConfig, S>
    {
    }

    private abstract static class SessionReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<SessionConfigType, SessionConfig, S> implements SessionProperty<S>
    {
        public SessionReadWriteProperty(String name)
        {
            super(name);
            SESSION_PROPERTIES.add(this);
        }
    }

    private abstract static class SessionReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<SessionConfigType, SessionConfig, S> implements SessionProperty<S>
    {
        public SessionReadOnlyProperty(String name)
        {
            super(name);
            SESSION_PROPERTIES.add(this);
        }
    }

    public static final SessionReadOnlyProperty<VirtualHostConfig> VIRTUAL_HOST_PROPERTY = new SessionReadOnlyProperty<VirtualHostConfig>("virtualHost")
    {
        public VirtualHostConfig getValue(SessionConfig object)
        {
            return object.getVirtualHost();
        }
    };

    public static final SessionReadOnlyProperty<String> NAME_PROPERTY = new SessionReadOnlyProperty<String>("name")
    {
        public String getValue(SessionConfig object)
        {
            return object.getSessionName();
        }
    };

    public static final SessionReadOnlyProperty<Integer> CHANNEL_ID_PROPERTY = new SessionReadOnlyProperty<Integer>("channelId")
    {
        public Integer getValue(SessionConfig object)
        {
            return object.getChannel();
        }
    };

    public static final SessionReadOnlyProperty<ConnectionConfig> CONNECTION_PROPERTY = new SessionReadOnlyProperty<ConnectionConfig>("connection")
    {
        public ConnectionConfig getValue(SessionConfig object)
        {
            return object.getConnectionConfig();
        }
    };

    public static final SessionReadOnlyProperty<Boolean> ATTACHED_PROPERTY = new SessionReadOnlyProperty<Boolean>("attached")
    {
        public Boolean getValue(SessionConfig object)
        {
            return object.isAttached();
        }
    };

    public static final SessionReadOnlyProperty<Long> DETACHED_LIFESPAN_PROPERTY = new SessionReadOnlyProperty<Long>("detachedLifespan")
    {
        public Long getValue(SessionConfig object)
        {
            return object.getDetachedLifespan();
        }
    };

    public static final SessionReadOnlyProperty<Long> EXPIRE_TIME_PROPERTY = new SessionReadOnlyProperty<Long>("expireTime")
    {
        public Long getValue(SessionConfig object)
        {
            return object.getExpiryTime();
        }
    };

    public static final SessionReadOnlyProperty<Long> MAX_CLIENT_RATE_PROPERTY = new SessionReadOnlyProperty<Long>("maxClientRate")
    {
        public Long getValue(SessionConfig object)
        {
            return object.getMaxClientRate();
        }
    };

    private static final SessionConfigType INSTANCE = new SessionConfigType();

    private SessionConfigType()
    {
    }

    public Collection<SessionProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(SESSION_PROPERTIES);
    }

    public static SessionConfigType getInstance()
    {
        return INSTANCE;
    }



}