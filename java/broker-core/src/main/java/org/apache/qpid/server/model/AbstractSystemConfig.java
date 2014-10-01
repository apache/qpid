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
package org.apache.qpid.server.model;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.store.ManagementModeStoreHandler;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordConverter;
import org.apache.qpid.server.store.DurableConfigurationStore;

public abstract class AbstractSystemConfig<X extends SystemConfig<X>>
        extends AbstractConfiguredObject<X> implements SystemConfig<X>
{
    private static final UUID SYSTEM_ID = new UUID(0l, 0l);
    private final EventLogger _eventLogger;
    private final LogRecorder _logRecorder;
    private final BrokerOptions _brokerOptions;
    private final BrokerShutdownProvider _brokerShutdownProvider;

    private DurableConfigurationStore _configurationStore;

    public AbstractSystemConfig(final TaskExecutor taskExecutor,
                                final EventLogger eventLogger,
                                final LogRecorder logRecorder,
                                final BrokerOptions brokerOptions,
                                final BrokerShutdownProvider brokerShutdownProvider)
    {
        super(parentsMap(),
              updateAttributes(brokerOptions.convertToSystemAttributes()),
              taskExecutor, BrokerModel.getInstance());
        _eventLogger = eventLogger;
        getTaskExecutor().start();
        _logRecorder = logRecorder;
        _brokerOptions = brokerOptions;
        _brokerShutdownProvider = brokerShutdownProvider;
    }

    private static Map<String, Object> updateAttributes(Map<String, Object> attributes)
    {
        attributes = new HashMap<>(attributes);
        attributes.put(ConfiguredObject.NAME, "System");
        attributes.put(ID, SYSTEM_ID);
        return attributes;
    }

    @Override
    protected void setState(final State desiredState)
    {
        throw new IllegalArgumentException("Cannot change the state of the SystemContext object");
    }

    @Override
    public State getState()
    {
        return State.ACTIVE;
    }

    @Override
    public EventLogger getEventLogger()
    {
        return _eventLogger;
    }

    public LogRecorder getLogRecorder()
    {
        return _logRecorder;
    }

    @Override
    public BrokerOptions getBrokerOptions()
    {
        return _brokerOptions;
    }

    @Override
    protected void onClose()
    {
        try
        {

            if (getTaskExecutor() != null)
            {
                getTaskExecutor().stop();
            }

            _eventLogger.message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

            _configurationStore.closeConfigurationStore();

        }
        finally
        {
            if (getTaskExecutor() != null)
            {
                getTaskExecutor().stopImmediately();
            }
        }

    }

    @Override
    public Broker getBroker()
    {
        Collection<Broker> children = getChildren(Broker.class);
        if(children == null || children.isEmpty())
        {
            return null;
        }
        else if(children.size() != 1)
        {
            throw new IllegalConfigurationException("More than one broker has been registered in a single context");
        }
        return children.iterator().next();
    }

    @Override
    protected void onOpen()
    {
        super.onOpen();
        _configurationStore = createStoreObject();

        if (_brokerOptions.isManagementMode())
        {
            _configurationStore = new ManagementModeStoreHandler(_configurationStore, _brokerOptions);
        }

        try
        {
            _configurationStore.openConfigurationStore(this,
                                          false,
                                          convertToConfigurationRecords(_brokerOptions.getInitialConfigurationLocation(),
                                                                        this));
            _configurationStore.upgradeStoreStructure();
        }
        catch (IOException e)
        {
            throw new IllegalArgumentException(e);
        }

    }

    abstract protected DurableConfigurationStore createStoreObject();

    @Override
    public DurableConfigurationStore getConfigurationStore()
    {
        return _configurationStore;
    }

    private ConfiguredObjectRecord[] convertToConfigurationRecords(final String initialConfigurationLocation,
                                                                   final SystemConfig systemConfig) throws IOException
    {
        ConfiguredObjectRecordConverter converter = new ConfiguredObjectRecordConverter(BrokerModel.getInstance());

        Reader reader;

        try
        {
            URL url = new URL(initialConfigurationLocation);
            reader = new InputStreamReader(url.openStream());
        }
        catch (MalformedURLException e)
        {
            reader = new FileReader(initialConfigurationLocation);
        }

        try
        {
            Collection<ConfiguredObjectRecord> records =
                    converter.readFromJson(org.apache.qpid.server.model.Broker.class,
                                           systemConfig, reader);
            return records.toArray(new ConfiguredObjectRecord[records.size()]);
        }
        finally
        {
            reader.close();
        }


    }

    @Override
    public BrokerShutdownProvider getBrokerShutdownProvider()
    {
        return _brokerShutdownProvider;
    }
}
