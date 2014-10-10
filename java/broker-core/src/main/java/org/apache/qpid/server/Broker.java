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
package org.apache.qpid.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Properties;

import javax.security.auth.Subject;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.model.BrokerShutdownProvider;
import org.apache.qpid.server.model.SystemConfig;
import org.apache.qpid.server.plugin.PluggableFactoryLoader;
import org.apache.qpid.server.plugin.SystemConfigFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.DurableConfigurationStore;

public class Broker implements BrokerShutdownProvider
{
    private static final Logger LOGGER = Logger.getLogger(Broker.class);

    private volatile Thread _shutdownHookThread;
    private volatile IApplicationRegistry _applicationRegistry;
    private EventLogger _eventLogger;
    private boolean _configuringOwnLogging = false;
    private final TaskExecutor _taskExecutor = new TaskExecutorImpl();

    protected static class InitException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        InitException(String msg, Throwable cause)
        {
            super(msg, cause);
        }
    }

    public void shutdown()
    {
        try
        {
            removeShutdownHook();
        }
        finally
        {
            try
            {
                if (_applicationRegistry != null)
                {
                    _applicationRegistry.close();
                }
                _taskExecutor.stop();

            }
            finally
            {
                if (_configuringOwnLogging)
                {
                    LogManager.shutdown();
                }
            }
        }
    }

    public void startup() throws Exception
    {
        startup(new BrokerOptions());
    }

    public void startup(final BrokerOptions options) throws Exception
    {
        _eventLogger = new EventLogger(new SystemOutMessageLogger());

        Subject.doAs(SecurityManager.getSystemTaskSubject("Broker"), new PrivilegedExceptionAction<Object>()
        {
            @Override
            public Object run() throws Exception
            {
                startupImpl(options);
                addShutdownHook();

                return null;
            }
        });

    }

    private void startupImpl(final BrokerOptions options) throws Exception
    {
        String storeLocation = options.getConfigurationStoreLocation();
        String storeType = options.getConfigurationStoreType();

        PluggableFactoryLoader<SystemConfigFactory> configFactoryLoader = new PluggableFactoryLoader<>(SystemConfigFactory.class);
        SystemConfigFactory configFactory = configFactoryLoader.get(storeType);
        if(configFactory == null)
        {
            LOGGER.fatal("Unknown config store type '"+storeType+"', only the following types are supported: " + configFactoryLoader.getSupportedTypes());
            throw new IllegalArgumentException("Unknown config store type '"+storeType+"', only the following types are supported: " + configFactoryLoader.getSupportedTypes());
        }

        _eventLogger.message(BrokerMessages.CONFIG(storeLocation));

        //Allow skipping the logging configuration for people who are
        //embedding the broker and want to configure it themselves.
        if(!options.isSkipLoggingConfiguration())
        {
            configureLogging(new File(options.getLogConfigFileLocation()), options.getLogWatchFrequency());
        }

        LogRecorder logRecorder = new LogRecorder();

        _taskExecutor.start();
        SystemConfig systemConfig = configFactory.newInstance(_taskExecutor, _eventLogger, logRecorder, options, this);
        systemConfig.open();
        DurableConfigurationStore store = systemConfig.getConfigurationStore();

        _applicationRegistry = new ApplicationRegistry(store, systemConfig);
        try
        {
            _applicationRegistry.initialise(options);
        }
        catch(Exception e)
        {
            LOGGER.fatal("Exception during startup", e);
            try
            {
                _applicationRegistry.close();
            }
            catch(Exception ce)
            {
                LOGGER.debug("An error occurred when closing the registry following initialization failure", ce);
            }
            throw e;
        }

    }

    private void configureLogging(File logConfigFile, int logWatchTime) throws InitException, IOException
    {
        _configuringOwnLogging = true;
        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            _eventLogger.message(BrokerMessages.LOG_CONFIG(logConfigFile.getAbsolutePath()));

            if (logWatchTime > 0)
            {
                System.out.println("log file " + logConfigFile.getAbsolutePath() + " will be checked for changes every "
                        + logWatchTime + " seconds");
                // log4j expects the watch interval in milliseconds
                try
                {
                    LoggingManagementFacade.configureAndWatch(logConfigFile.getPath(), logWatchTime * 1000);
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
            else
            {
                try
                {
                    LoggingManagementFacade.configure(logConfigFile.getPath());
                }
                catch (Exception e)
                {
                    throw new InitException(e.getMessage(),e);
                }
            }
        }
        else
        {
            System.err.println("Logging configuration error: unable to read file " + logConfigFile.getAbsolutePath());
            System.err.println("Using the fallback internal fallback-log4j.properties configuration");

            InputStream propsFile = this.getClass().getResourceAsStream("/fallback-log4j.properties");
            if(propsFile == null)
            {
                throw new IOException("Unable to load the fallback internal fallback-log4j.properties configuration file");
            }
            else
            {
                try
                {
                    Properties fallbackProps = new Properties();
                    fallbackProps.load(propsFile);
                    PropertyConfigurator.configure(fallbackProps);
                }
                finally
                {
                    propsFile.close();
                }
            }
        }
    }


    private void addShutdownHook()
    {
        Thread shutdownHookThread = new Thread(new ShutdownService());
        shutdownHookThread.setName("QpidBrokerShutdownHook");

        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
        _shutdownHookThread = shutdownHookThread;

        LOGGER.debug("Added shutdown hook");
    }

    private void removeShutdownHook()
    {
        Thread shutdownThread = _shutdownHookThread;

        //if there is a shutdown thread and we aren't it, we should remove it
        if(shutdownThread != null && !(Thread.currentThread() == shutdownThread))
        {
            LOGGER.debug("Removing shutdown hook");

            _shutdownHookThread = null;

            boolean removed = false;
            try
            {
                removed = Runtime.getRuntime().removeShutdownHook(shutdownThread);
            }
            catch(IllegalStateException ise)
            {
                //ignore, means the JVM is already shutting down
            }

            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("Removed shutdown hook: " + removed);
            }
        }
        else
        {
            LOGGER.debug("Skipping shutdown hook removal as there either isn't one, or we are it.");
        }
    }

    public org.apache.qpid.server.model.Broker getBroker()
    {
        if (_applicationRegistry == null)
        {
            return null;
        }
        return _applicationRegistry.getBroker();
    }

    private class ShutdownService implements Runnable
    {
        public void run()
        {
            Subject.doAs(SecurityManager.getSystemTaskSubject("Shutdown"), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    LOGGER.debug("Shutdown hook running");
                    Broker.this.shutdown();
                    return null;
                }
            });
        }
    }

}
