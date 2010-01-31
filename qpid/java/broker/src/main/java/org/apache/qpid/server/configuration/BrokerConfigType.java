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
import java.io.File;

public final class BrokerConfigType extends ConfigObjectType<BrokerConfigType, BrokerConfig>
{
    private static final List<BrokerProperty<?>> BROKER_PROPERTIES = new ArrayList<BrokerProperty<?>>();

    public static interface BrokerProperty<S> extends ConfigProperty<BrokerConfigType, BrokerConfig, S>
    {
    }

    private abstract static class BrokerReadWriteProperty<S>  extends ConfigProperty.ReadWriteConfigProperty<BrokerConfigType, BrokerConfig, S> implements BrokerProperty<S>
    {
        public BrokerReadWriteProperty(String name)
        {
            super(name);
            BROKER_PROPERTIES.add(this);
        }
    }

    private abstract static class BrokerReadOnlyProperty<S> extends ConfigProperty.ReadOnlyConfigProperty<BrokerConfigType, BrokerConfig, S> implements BrokerProperty<S>
    {
        public BrokerReadOnlyProperty(String name)
        {
            super(name);
            BROKER_PROPERTIES.add(this);
        }
    }

    public static final BrokerReadOnlyProperty<SystemConfig> SYSTEM_PROPERTY = new BrokerReadOnlyProperty<SystemConfig>("system")
    {
        public SystemConfig getValue(BrokerConfig object)
        {
            return object.getSystem();
        }
    };

    public static final BrokerReadOnlyProperty<Integer> PORT_PROPERTY = new BrokerReadOnlyProperty<Integer>("port")
    {
        public Integer getValue(BrokerConfig object)
        {
            return object.getPort();
        }
    };

    public static final BrokerReadOnlyProperty<Integer> WORKER_THREADS_PROPERTY = new BrokerReadOnlyProperty<Integer>("workerThreads")
    {
        public Integer getValue(BrokerConfig object)
        {
            return object.getWorkerThreads();
        }
    };

    public static final BrokerReadOnlyProperty<Integer> MAX_CONNECTIONS_PROPERTY = new BrokerReadOnlyProperty<Integer>("maxConnections")
    {
        public Integer getValue(BrokerConfig object)
        {
            return object.getMaxConnections();
        }
    };

    public static final BrokerReadOnlyProperty<Integer> CONNECTION_BACKLOG_LIMIT_PROPERTY = new BrokerReadOnlyProperty<Integer>("connectionBacklog")
    {
        public Integer getValue(BrokerConfig object)
        {
            return object.getConnectionBacklogLimit();
        }
    };

    public static final BrokerReadOnlyProperty<Long> STAGING_THRESHOLD_PROPERTY = new BrokerReadOnlyProperty<Long>("stagingThreshold")
    {
        public Long getValue(BrokerConfig object)
        {
            return object.getStagingThreshold();
        }
    };

    public static final BrokerReadOnlyProperty<Integer> MANAGEMENT_PUBLISH_INTERVAL_PROPERTY = new BrokerReadOnlyProperty<Integer>("mgmtPublishInterval")
    {
        public Integer getValue(BrokerConfig object)
        {
            return object.getManagementPublishInterval();
        }
    };

    public static final BrokerReadOnlyProperty<String> VERSION_PROPERTY = new BrokerReadOnlyProperty<String>("version")
    {
        public String getValue(BrokerConfig object)
        {
            return object.getVersion();
        }
    };

    public static final BrokerReadOnlyProperty<String> DATA_DIR_PROPERTY = new BrokerReadOnlyProperty<String>("dataDirectory")
    {
        public String getValue(BrokerConfig object)
        {
            return object.getDataDirectory();
        }
    };

    private static final BrokerConfigType INSTANCE = new BrokerConfigType();

    private BrokerConfigType()
    {
    }

    public Collection<BrokerProperty<?>> getProperties()
    {
        return Collections.unmodifiableList(BROKER_PROPERTIES);
    }

    public static BrokerConfigType getInstance()
    {
        return INSTANCE;
    }



}