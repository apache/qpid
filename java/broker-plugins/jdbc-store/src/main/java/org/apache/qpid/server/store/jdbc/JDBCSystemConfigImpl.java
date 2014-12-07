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
package org.apache.qpid.server.store.jdbc;

import java.util.Map;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.AbstractSystemConfig;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerShutdownProvider;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.model.SystemConfigFactoryConstructor;
import org.apache.qpid.server.store.DurableConfigurationStore;

@ManagedObject( category = false, type = JDBCSystemConfigImpl.SYSTEM_CONFIG_TYPE)
public class JDBCSystemConfigImpl extends AbstractSystemConfig<JDBCSystemConfigImpl> implements JDBCSystemConfig<JDBCSystemConfigImpl>
{
    public static final String SYSTEM_CONFIG_TYPE = "JDBC";

    @ManagedAttributeField
    private String _connectionUrl;
    @ManagedAttributeField
    private String _connectionPoolType;
    @ManagedAttributeField
    private String _username;
    @ManagedAttributeField
    private String _password;

    @SystemConfigFactoryConstructor
    public JDBCSystemConfigImpl(final TaskExecutor taskExecutor,
                                final EventLogger eventLogger,
                                final LogRecorder logRecorder,
                                final Map<String,Object> attributes,
                                final BrokerShutdownProvider brokerShutdownProvider)
    {
        super(taskExecutor, eventLogger, logRecorder, attributes, brokerShutdownProvider);
    }

    @Override
    protected DurableConfigurationStore createStoreObject()
    {
        return new GenericJDBCConfigurationStore(Broker.class);
    }

    @Override
    public String getConnectionUrl()
    {
        return _connectionUrl;
    }

    @Override
    public String getConnectionPoolType()
    {
        return _connectionPoolType;
    }

    @Override
    public String getUsername()
    {
        return _username;
    }

    @Override
    public String getPassword()
    {
        return _password;
    }
}
