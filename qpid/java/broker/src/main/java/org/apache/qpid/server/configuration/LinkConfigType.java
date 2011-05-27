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

public final class LinkConfigType extends ConfigObjectType<LinkConfigType, LinkConfig>
{
    private static final List<LinkProperty<?>> LINK_PROPERTIES = new ArrayList<LinkProperty<?>>();

    public static interface LinkProperty<S> extends ConfigProperty<LinkConfigType, LinkConfig, S>
    {
    }

    private abstract static class LinkReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<LinkConfigType, LinkConfig, S> implements LinkProperty<S>
    {
        public LinkReadWriteProperty(String name)
        {
            super(name);
            LINK_PROPERTIES.add(this);
        }
    }

    private abstract static class LinkReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<LinkConfigType, LinkConfig, S> implements LinkProperty<S>
    {
        public LinkReadOnlyProperty(String name)
        {
            super(name);
            LINK_PROPERTIES.add(this);
        }
    }

    public static final LinkReadOnlyProperty<VirtualHostConfig> VIRTUAL_HOST_PROPERTY = new LinkReadOnlyProperty<VirtualHostConfig>("virtualHost")
    {
        public VirtualHostConfig getValue(LinkConfig object)
        {
            return object.getVirtualHost();
        }
    };

    public static final LinkReadOnlyProperty<String> TRANSPORT_PROPERTY = new LinkReadOnlyProperty<String>("transport")
    {
        public String getValue(LinkConfig object)
        {
            return object.getTransport();
        }
    };

    public static final LinkReadOnlyProperty<String> HOST_PROPERTY = new LinkReadOnlyProperty<String>("host")
    {
        public String getValue(LinkConfig object)
        {
            return object.getHost();
        }
    };

    public static final LinkReadOnlyProperty<Integer> PORT_PROPERTY = new LinkReadOnlyProperty<Integer>("host")
    {
        public Integer getValue(LinkConfig object)
        {
            return object.getPort();
        }
    };

    public static final LinkReadOnlyProperty<String> REMOTE_VHOST_PROPERTY = new LinkReadOnlyProperty<String>("remoteVhost")
    {
        public String getValue(LinkConfig object)
        {
            return object.getRemoteVhost();
        }
    };

    public static final LinkReadOnlyProperty<String> AUTH_MECHANISM_PROPERTY = new LinkReadOnlyProperty<String>("authMechanism")
    {
        public String getValue(LinkConfig object)
        {
            return object.getAuthMechanism();
        }
    };

    public static final LinkReadOnlyProperty<String> USERNAME_PROPERTY = new LinkReadOnlyProperty<String>("username")
    {
        public String getValue(LinkConfig object)
        {
            return object.getUsername();
        }
    };

    public static final LinkReadOnlyProperty<String> PASSWORD_PROPERTY = new LinkReadOnlyProperty<String>("password")
    {
        public String getValue(LinkConfig object)
        {
            return object.getPassword();
        }
    };

    private static final LinkConfigType INSTANCE = new LinkConfigType();

    private LinkConfigType()
    {
    }

    public Collection<LinkProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(LINK_PROPERTIES);
    }

    public static LinkConfigType getInstance()
    {
        return INSTANCE;
    }



}