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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.messages.BrokerMessages;

public class SystemContextImpl extends AbstractConfiguredObject<SystemContextImpl> implements SystemContext<SystemContextImpl>
{
    private static final UUID SYSTEM_ID = new UUID(0l, 0l);
    private final EventLogger _eventLogger;
    private final LogRecorder _logRecorder;
    private final BrokerOptions _brokerOptions;

    @ManagedAttributeField
    private String _storePath;

    @ManagedAttributeField
    private String _storeType;

    public SystemContextImpl(final TaskExecutor taskExecutor,
                             final EventLogger eventLogger,
                             final LogRecorder logRecorder,
                             final BrokerOptions brokerOptions)
    {
        super(parentsMap(),
              createAttributes(brokerOptions),
              taskExecutor, BrokerModel.getInstance());
        _eventLogger = eventLogger;
        getTaskExecutor().start();
        _logRecorder = logRecorder;
        _brokerOptions = brokerOptions;
        open();
    }

    public static Map<String, Object> createAttributes(final BrokerOptions brokerOptions)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, SYSTEM_ID);
        attributes.put(NAME, "System");
        attributes.put("storePath", brokerOptions.getConfigurationStoreLocation());
        attributes.put("storeTye", brokerOptions.getConfigurationStoreType());
        attributes.put(ConfiguredObject.CONTEXT, brokerOptions.getConfigProperties());
        return attributes;
    }

    @Override
    protected boolean setState(final State currentState, final State desiredState)
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
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public String getStoreType()
    {
        return _storeType;
    }

    @Override
    public void close()
    {
        try
        {


            if (getTaskExecutor() != null)
            {
                getTaskExecutor().stop();
            }

            _eventLogger.message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

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
}
