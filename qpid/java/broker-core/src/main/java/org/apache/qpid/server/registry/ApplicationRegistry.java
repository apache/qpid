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

import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.common.QpidProperties;
import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.ConfiguredObjectRecoverer;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.DefaultRecovererProvider;
import org.apache.qpid.server.configuration.store.StoreConfigurationChangeListener;
import org.apache.qpid.server.logging.*;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.logging.messages.VirtualHostMessages;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.TaskPrincipal;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;
import org.apache.qpid.util.SystemUtils;

import javax.security.auth.Subject;


/**
 * An abstract application registry that provides access to configuration information and handles the
 * construction and caching of configurable objects.
 * <p/>
 * Subclasses should handle the construction of the "registered objects" such as the exchange registry.
 */
public class ApplicationRegistry implements IApplicationRegistry
{
    private static final Logger _logger = Logger.getLogger(ApplicationRegistry.class);

    private final EventLogger _eventLogger;

    private final VirtualHostRegistry _virtualHostRegistry;

    private volatile MessageLogger _messageLogger;

    private Broker _broker;

    private Timer _reportingTimer;
    private StatisticsCounter _messagesDelivered, _dataDelivered, _messagesReceived, _dataReceived;

    private LogRecorder _logRecorder;

    private ConfigurationEntryStore _store;
    private TaskExecutor _taskExecutor;

    public ApplicationRegistry(ConfigurationEntryStore store, EventLogger eventLogger)
    {
        _store = store;
        _eventLogger = eventLogger;
        _virtualHostRegistry = new VirtualHostRegistry(eventLogger);
        initialiseStatistics();
    }

    public void initialise(BrokerOptions brokerOptions) throws Exception
    {
        // Create the RootLogger to be used during broker operation
        boolean statusUpdatesEnabled = Boolean.parseBoolean(System.getProperty(BrokerProperties.PROPERTY_STATUS_UPDATES, "true"));
        _messageLogger = new Log4jMessageLogger(statusUpdatesEnabled);

        _logRecorder = new LogRecorder();

        //Create the composite (log4j+SystemOut MessageLogger to be used during startup
        MessageLogger[] messageLoggers = {new SystemOutMessageLogger(), _messageLogger};
        CompositeStartupMessageLogger startupMessageLogger = new CompositeStartupMessageLogger(messageLoggers);
        _eventLogger.setMessageLogger(startupMessageLogger);

        logStartupMessages();

        _taskExecutor = new TaskExecutor();
        _taskExecutor.start();

        StoreConfigurationChangeListener storeChangeListener = new StoreConfigurationChangeListener(_store);
        RecovererProvider provider = new DefaultRecovererProvider((StatisticsGatherer)this, _virtualHostRegistry, _logRecorder,
                                                                  _eventLogger, _taskExecutor, brokerOptions, storeChangeListener);
        ConfiguredObjectRecoverer<? extends ConfiguredObject> brokerRecoverer =  provider.getRecoverer(Broker.class.getSimpleName());
        _broker = (Broker) brokerRecoverer.create(provider, _store.getRootEntry());

        _virtualHostRegistry.setDefaultVirtualHostName((String)_broker.getAttribute(Broker.DEFAULT_VIRTUAL_HOST));

        initialiseStatisticsReporting();

        // starting the broker
        _broker.setDesiredState(State.INITIALISING, State.ACTIVE);

        _eventLogger.message(BrokerMessages.READY());
        _eventLogger.setMessageLogger(_messageLogger);

    }

    private void initialiseStatisticsReporting()
    {
        long report = ((Number)_broker.getAttribute(Broker.STATISTICS_REPORTING_PERIOD)).intValue() * 1000; // convert to ms
        final boolean reset = (Boolean)_broker.getAttribute(Broker.STATISTICS_REPORTING_RESET_ENABLED);

        /* add a timer task to report statistics if generation is enabled for broker or virtualhosts */
        if (report > 0L)
        {
            _reportingTimer = new Timer("Statistics-Reporting", true);
            StatisticsReportingTask task = new StatisticsReportingTask(reset, _messageLogger);
            _reportingTimer.scheduleAtFixedRate(task, report / 2, report);
        }
    }

    private class StatisticsReportingTask extends TimerTask
    {
        private final int DELIVERED = 0;
        private final int RECEIVED = 1;

        private final boolean _reset;
        private final MessageLogger _logger;
        private final Subject _subject;

        public StatisticsReportingTask(boolean reset, MessageLogger logger)
        {
            _reset = reset;
            _logger = logger;
            _subject = new Subject(false, SecurityManager.SYSTEM.getPrincipals(), Collections.emptySet(), Collections.emptySet());
            _subject.getPrincipals().add(new TaskPrincipal("Statistics"));
            _subject.setReadOnly();

        }

        public void run()
        {
            Subject.doAs(_subject, new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    reportStatistics();
                    return null;
                }
            });
        }

        protected void reportStatistics()
        {
            try
            {
                _eventLogger.message(BrokerMessages.STATS_DATA(DELIVERED, _dataDelivered.getPeak() / 1024.0, _dataDelivered.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_MSGS(DELIVERED, _messagesDelivered.getPeak(), _messagesDelivered.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_DATA(RECEIVED, _dataReceived.getPeak() / 1024.0, _dataReceived.getTotal()));
                _eventLogger.message(BrokerMessages.STATS_MSGS(RECEIVED,
                                                               _messagesReceived.getPeak(),
                                                               _messagesReceived.getTotal()));
                Collection<VirtualHost> hosts = _virtualHostRegistry.getVirtualHosts();

                if (hosts.size() > 1)
                {
                    for (VirtualHost vhost : hosts)
                    {
                        String name = vhost.getName();
                        StatisticsCounter dataDelivered = vhost.getDataDeliveryStatistics();
                        StatisticsCounter messagesDelivered = vhost.getMessageDeliveryStatistics();
                        StatisticsCounter dataReceived = vhost.getDataReceiptStatistics();
                        StatisticsCounter messagesReceived = vhost.getMessageReceiptStatistics();
                        EventLogger logger = vhost.getEventLogger();
                        logger.message(VirtualHostMessages.STATS_DATA(name, DELIVERED, dataDelivered.getPeak() / 1024.0, dataDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name, DELIVERED, messagesDelivered.getPeak(), messagesDelivered.getTotal()));
                        logger.message(VirtualHostMessages.STATS_DATA(name, RECEIVED, dataReceived.getPeak() / 1024.0, dataReceived.getTotal()));
                        logger.message(VirtualHostMessages.STATS_MSGS(name, RECEIVED, messagesReceived.getPeak(), messagesReceived.getTotal()));
                    }
                }

                if (_reset)
                {
                    resetStatistics();
                }
            }
            catch(Exception e)
            {
                ApplicationRegistry._logger.warn("Unexpected exception occurred while reporting the statistics", e);
            }
        }
    }

    /**
     * Close non-null Closeable items and log any errors
     * @param close
     */
    private void close(Closeable close)
    {
        try
        {
            if (close != null)
            {
                close.close();
            }
        }
        catch (Throwable e)
        {
            _logger.error("Error thrown whilst closing " + close.getClass().getSimpleName(), e);
        }
    }

    public void close()
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("Shutting down ApplicationRegistry:" + this);
        }

        try
        {
            //Stop Statistics Reporting
            if (_reportingTimer != null)
            {
                _reportingTimer.cancel();
            }

            if (_broker != null)
            {
                _broker.setDesiredState(_broker.getState(), State.STOPPED);
            }

            //Shutdown virtualhosts
            close(_virtualHostRegistry);

            if (_taskExecutor != null)
            {
                _taskExecutor.stop();
            }

            _eventLogger.message(BrokerMessages.STOPPED());

            _logRecorder.closeLogRecorder();

        }
        finally
        {
            if (_taskExecutor != null)
            {
                _taskExecutor.stopImmediately();
            }
        }
        _store = null;
        _broker = null;
    }

    public void registerMessageDelivered(long messageSize)
    {
        _messagesDelivered.registerEvent(1L);
        _dataDelivered.registerEvent(messageSize);
    }

    public void registerMessageReceived(long messageSize, long timestamp)
    {
        _messagesReceived.registerEvent(1L, timestamp);
        _dataReceived.registerEvent(messageSize, timestamp);
    }

    public StatisticsCounter getMessageReceiptStatistics()
    {
        return _messagesReceived;
    }

    public StatisticsCounter getDataReceiptStatistics()
    {
        return _dataReceived;
    }

    public StatisticsCounter getMessageDeliveryStatistics()
    {
        return _messagesDelivered;
    }

    public StatisticsCounter getDataDeliveryStatistics()
    {
        return _dataDelivered;
    }

    public void resetStatistics()
    {
        _messagesDelivered.reset();
        _dataDelivered.reset();
        _messagesReceived.reset();
        _dataReceived.reset();

        for (VirtualHost vhost : _virtualHostRegistry.getVirtualHosts())
        {
            vhost.resetStatistics();
        }
    }

    public void initialiseStatistics()
    {
        _messagesDelivered = new StatisticsCounter("messages-delivered");
        _dataDelivered = new StatisticsCounter("bytes-delivered");
        _messagesReceived = new StatisticsCounter("messages-received");
        _dataReceived = new StatisticsCounter("bytes-received");
    }

    private void logStartupMessages()
    {
        _eventLogger.message(BrokerMessages.STARTUP(QpidProperties.getReleaseVersion(), QpidProperties.getBuildVersion()));

        _eventLogger.message(BrokerMessages.PLATFORM(System.getProperty("java.vendor"),
                                                 System.getProperty("java.runtime.version", System.getProperty("java.version")),
                                                 SystemUtils.getOSName(),
                                                 SystemUtils.getOSVersion(),
                                                 SystemUtils.getOSArch()));

        _eventLogger.message(BrokerMessages.MAX_MEMORY(Runtime.getRuntime().maxMemory()));
    }

    @Override
    public Broker getBroker()
    {
        return _broker;
    }

}
