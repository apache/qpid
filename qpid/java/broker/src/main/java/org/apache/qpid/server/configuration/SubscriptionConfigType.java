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

public final class SubscriptionConfigType extends ConfigObjectType<SubscriptionConfigType, SubscriptionConfig>
{
    private static final List<SubscriptionProperty<?>> SUBSCRIPTION_PROPERTIES = new ArrayList<SubscriptionProperty<?>>();

    public static interface SubscriptionProperty<S> extends ConfigProperty<SubscriptionConfigType, SubscriptionConfig, S>
    {
    }

    private abstract static class SubscriptionReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<SubscriptionConfigType, SubscriptionConfig, S> implements SubscriptionProperty<S>
    {
        public SubscriptionReadWriteProperty(String name)
        {
            super(name);
            SUBSCRIPTION_PROPERTIES.add(this);
        }
    }

    private abstract static class SubscriptionReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<SubscriptionConfigType, SubscriptionConfig, S> implements SubscriptionProperty<S>
    {
        public SubscriptionReadOnlyProperty(String name)
        {
            super(name);
            SUBSCRIPTION_PROPERTIES.add(this);
        }
    }

    public static final SubscriptionReadOnlyProperty<SessionConfig> SESSION_PROPERTY = new SubscriptionReadOnlyProperty<SessionConfig>("session")
    {
        public SessionConfig getValue(SubscriptionConfig object)
        {
            return object.getSessionConfig();
        }
    };

    public static final SubscriptionReadOnlyProperty<QueueConfig> QUEUE_PROPERTY = new SubscriptionReadOnlyProperty<QueueConfig>("queue")
    {
        public QueueConfig getValue(SubscriptionConfig object)
        {
            return object.getQueue();
        }
    };

    public static final SubscriptionReadOnlyProperty<String> NAME_PROPERTY = new SubscriptionReadOnlyProperty<String>("name")
    {
        public String getValue(SubscriptionConfig object)
        {
            return object.getName();
        }
    };

    public static final SubscriptionReadOnlyProperty<Map<String,Object>> ARGUMENTS = new SubscriptionReadOnlyProperty<Map<String,Object>>("arguments")
    {
        public Map<String,Object> getValue(SubscriptionConfig object)
        {
            return object.getArguments();
        }
    };

    public static final SubscriptionReadOnlyProperty<String> CREDIT_MODE_PROPERTY = new SubscriptionReadOnlyProperty<String>("creditMode")
    {
        public String getValue(SubscriptionConfig object)
        {
            return object.getCreditMode();
        }
    };

    public static final SubscriptionReadOnlyProperty<Boolean> BROWSING_PROPERTY = new SubscriptionReadOnlyProperty<Boolean>("browsing")
    {
        public Boolean getValue(SubscriptionConfig object)
        {
            return object.isBrowsing();
        }
    };

    public static final SubscriptionReadOnlyProperty<Boolean> EXCLUSIVE_PROPERTY = new SubscriptionReadOnlyProperty<Boolean>("exclusive")
    {
        public Boolean getValue(SubscriptionConfig object)
        {
            return object.isExclusive();
        }
    };

    public static final SubscriptionReadOnlyProperty<Boolean> EXPLICIT_ACK_PROPERTY = new SubscriptionReadOnlyProperty<Boolean>("explicitAck")
    {
        public Boolean getValue(SubscriptionConfig object)
        {
            return object.isExplicitAcknowledge();
        }
    };

    private static final SubscriptionConfigType INSTANCE = new SubscriptionConfigType();

    private SubscriptionConfigType()
    {
    }

    public Collection<SubscriptionProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(SUBSCRIPTION_PROPERTIES);
    }


    public static SubscriptionConfigType getInstance()
    {
        return INSTANCE;
    }



}