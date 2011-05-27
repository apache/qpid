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

public final class BridgeConfigType extends ConfigObjectType<BridgeConfigType, BridgeConfig>
{
    private static final List<BridgeProperty<?>> BRIDGE_PROPERTIES = new ArrayList<BridgeProperty<?>>();

    public static interface BridgeProperty<S> extends ConfigProperty<BridgeConfigType, BridgeConfig, S>
    {
    }

    private abstract static class BridgeReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<BridgeConfigType, BridgeConfig, S> implements BridgeProperty<S>
    {
        public BridgeReadWriteProperty(String name)
        {
            super(name);
            BRIDGE_PROPERTIES.add(this);
        }
    }

    private abstract static class BridgeReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<BridgeConfigType, BridgeConfig, S> implements BridgeProperty<S>
    {
        public BridgeReadOnlyProperty(String name)
        {
            super(name);
            BRIDGE_PROPERTIES.add(this);
        }
    }

    public static final BridgeReadOnlyProperty<LinkConfig> LINK_PROPERTY = new BridgeReadOnlyProperty<LinkConfig>("link")
    {
        public LinkConfig getValue(BridgeConfig object)
        {
            return object.getLink();
        }
    };

    public static final BridgeReadOnlyProperty<Integer> CHANNEL_ID_PROPERTY = new BridgeReadOnlyProperty<Integer>("channelId")
    {
        public Integer getValue(BridgeConfig object)
        {
            return object.getChannelId();
        }
    };

    public static final BridgeReadOnlyProperty<Boolean> DURABLE_PROPERTY = new BridgeReadOnlyProperty<Boolean>("durable")
    {
        public Boolean getValue(BridgeConfig object)
        {
            return object.isDurable();
        }
    };

    public static final BridgeReadOnlyProperty<String> SOURCE_PROPERTY = new BridgeReadOnlyProperty<String>("source")
    {
        public String getValue(BridgeConfig object)
        {
            return object.getSource();
        }
    };

    public static final BridgeReadOnlyProperty<String> DESTINATION_PROPERTY = new BridgeReadOnlyProperty<String>("destination")
    {
        public String getValue(BridgeConfig object)
        {
            return object.getDestination();
        }
    };

    public static final BridgeReadOnlyProperty<String> KEY_PROPERTY = new BridgeReadOnlyProperty<String>("key")
    {
        public String getValue(BridgeConfig object)
        {
            return object.getKey();
        }
    };

    public static final BridgeReadOnlyProperty<Boolean> QUEUE_BRIDGE_PROPERTY = new BridgeReadOnlyProperty<Boolean>("queueBridge")
    {
        public Boolean getValue(BridgeConfig object)
        {
            return object.isQueueBridge();
        }
    };

    public static final BridgeReadOnlyProperty<Boolean> LOCAL_SOURCE_PROPERTY = new BridgeReadOnlyProperty<Boolean>("localSource")
    {
        public Boolean getValue(BridgeConfig object)
        {
            return object.isLocalSource();
        }
    };

    public static final BridgeReadOnlyProperty<String> TAG_PROPERTY = new BridgeReadOnlyProperty<String>("tag")
    {
        public String getValue(BridgeConfig object)
        {
            return object.getTag();
        }
    };

    public static final BridgeReadOnlyProperty<String> EXCLUDES_PROPERTY = new BridgeReadOnlyProperty<String>("excludes")
    {
        public String getValue(BridgeConfig object)
        {
            return object.getExcludes();
        }
    };

    public static final BridgeReadOnlyProperty<Boolean> DYNAMIC_PROPERTY = new BridgeReadOnlyProperty<Boolean>("dynamic")
    {
        public Boolean getValue(BridgeConfig object)
        {
            return object.isDynamic();
        }
    };

    public static final BridgeReadOnlyProperty<Integer> ACK_BATCHING_PROPERTY = new BridgeReadOnlyProperty<Integer>("ackBatching")
    {
        public Integer getValue(BridgeConfig object)
        {
            return object.getAckBatching();
        }
    };


    private static final BridgeConfigType INSTANCE = new BridgeConfigType();

    private BridgeConfigType()
    {
    }

    public Collection<BridgeProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(BRIDGE_PROPERTIES);
    }

    public static BridgeConfigType getInstance()
    {
        return INSTANCE;
    }



}