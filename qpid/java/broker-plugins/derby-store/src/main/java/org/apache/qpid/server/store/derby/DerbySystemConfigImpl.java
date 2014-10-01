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
package org.apache.qpid.server.store.derby;

import org.apache.qpid.server.BrokerOptions;
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

@ManagedObject(category = false, type = DerbySystemConfigImpl.SYSTEM_CONFIG_TYPE)
public class DerbySystemConfigImpl extends AbstractSystemConfig<DerbySystemConfigImpl> implements DerbySystemConfig<DerbySystemConfigImpl>
{
    public static final String SYSTEM_CONFIG_TYPE = "DERBY";

    @ManagedAttributeField
    private String _storePath;
    @ManagedAttributeField
    private Long _storeUnderfullSize;
    @ManagedAttributeField
    private Long _storeOverfullSize;

    @SystemConfigFactoryConstructor
    public DerbySystemConfigImpl(final TaskExecutor taskExecutor,
                                 final EventLogger eventLogger,
                                 final LogRecorder logRecorder,
                                 final BrokerOptions brokerOptions,
                                 final BrokerShutdownProvider brokerShutdownProvider)
    {
        super(taskExecutor, eventLogger, logRecorder, brokerOptions, brokerShutdownProvider);
    }

    @Override
    protected DurableConfigurationStore createStoreObject()
    {
        return new DerbyConfigurationStore(Broker.class);
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public Long getStoreUnderfullSize()
    {
        return _storeUnderfullSize;
    }

    @Override
    public Long getStoreOverfullSize()
    {
        return _storeOverfullSize;
    }
}
