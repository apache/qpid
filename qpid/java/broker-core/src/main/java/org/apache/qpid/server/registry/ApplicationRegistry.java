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
package org.apache.qpid.server.registry;

import org.apache.log4j.Logger;

import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.logging.CompositeStartupMessageLogger;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.Log4jMessageLogger;
import org.apache.qpid.server.logging.MessageLogger;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.store.BrokerStoreUpgraderAndRecoverer;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.util.SystemUtils;


/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public class ApplicationRegistry implements IApplicationRegistry
{
    private static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private final SystemConfig _systemConfig;

    private Broker _broker;

    private DurableConfigurationStore _store;

    public ApplicationRegistry(DurableConfigurationStore store, SystemConfig systemConfig)
    {
        _store = store;
        _systemConfig = systemConfig;
    }

    public void initialise(BrokerOptions brokerOptions) throws Exception
    {
        // Create the RootLogger to be used during broker operation
        boolean statusUpdatesEnabled = Boolean.parseBoolean(System.getProperty(BrokerProperties.PROPERTY_STATUS_UPDATES, "true"));
        MessageLogger messageLogger = new Log4jMessageLogger(statusUpdatesEnabled);
        final EventLogger eventLogger = _systemConfig.getEventLogger();
        eventLogger.setMessageLogger(messageLogger);

        //Create the composite (log4j+SystemOut MessageLogger to be used during startup
        MessageLogger[] messageLoggers = {new SystemOutMessageLogger(), messageLogger};

        CompositeStartupMessageLogger startupMessageLogger = new CompositeStartupMessageLogger(messageLoggers);
        EventLogger startupLogger = new EventLogger(startupMessageLogger);

        logStartupMessages(startupLogger);

        BrokerStoreUpgraderAndRecoverer upgrader = new BrokerStoreUpgraderAndRecoverer(_systemConfig);
        _broker = upgrader.perform(_store);

        _broker.setEventLogger(startupLogger);
        _broker.open();

        // starting the broker
        //_broker.setDesiredState(State.ACTIVE);

        startupLogger.message(BrokerMessages.READY());
        _broker.setEventLogger(eventLogger);

    }

    public void close()
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Shutting down ApplicationRegistry:" + this);
        }
        try
        {
            if (_broker != null)
            {
                _broker.close();
            }
        }
        finally
        {
            _systemConfig.close();
        }
        _store = null;
        _broker = null;
    }


    private void logStartupMessages(EventLogger eventLogger)
    {
        eventLogger.message(BrokerMessages.STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

        eventLogger.message(BrokerMessages.PLATFORM(System.getProperty("java.vendor"),
                                                 System.getProperty("java.runtime.version", System.getProperty("java.version")),
                                                 SystemUtils.getOSName(),
                                                 SystemUtils.getOSVersion(),
                                                 SystemUtils.getOSArch()));

        eventLogger.message(BrokerMessages.MAX_MEMORY(Runtime.getRuntime().maxMemory()));
    }

    @Override
    public Broker getBroker()
    {
        return _broker;
    }

}
