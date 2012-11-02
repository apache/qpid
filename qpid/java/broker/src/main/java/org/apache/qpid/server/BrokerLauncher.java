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
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.configuration.ConfigurationEntryStore;
import org.apache.qpid.server.configuration.store.XMLConfigurationEntryStore;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.BrokerActor;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.log4j.LoggingManagementFacade;
import org.apache.qpid.server.logging.messages.BrokerMessages;
import org.apache.qpid.server.registry.ApplicationRegistry;

public class BrokerLauncher
{
    private static final Logger LOGGER = Logger.getLogger(BrokerLauncher.class);

    private volatile Thread _shutdownHookThread;

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
                ApplicationRegistry.remove();
            }
            finally
            {
                clearAMQShortStringCache();
            }
        }
    }

    public void startup() throws Exception
    {
        startup(new BrokerOptions());
    }

    public void startup(final BrokerOptions options) throws Exception
    {
        CurrentActor.set(new BrokerActor(new SystemOutMessageLogger()));
        try
        {
            startupImpl(options);
            addShutdownHook();
        }
        finally
        {
            try
            {
                CurrentActor.remove();
            }
            finally
            {
                clearAMQShortStringCache();
            }
        }
    }

    private void startupImpl(final BrokerOptions options) throws Exception
    {
        final String qpidHome = options.getQpidHome();
        final File configFile = getConfigFile(options.getConfigFile(),
                                    BrokerOptions.DEFAULT_CONFIG_FILE, qpidHome, true);

        CurrentActor.get().message(BrokerMessages.CONFIG(configFile.getAbsolutePath()));

        File logConfigFile = getConfigFile(options.getLogConfigFile(),
                                    BrokerOptions.DEFAULT_LOG_CONFIG_FILE, qpidHome, false);

        configureLogging(logConfigFile, options.getLogWatchFrequency());

        ConfigurationEntryStore store = new XMLConfigurationEntryStore(configFile, options);

        ApplicationRegistry applicationRegistry = new ApplicationRegistry(store);

        ApplicationRegistry.initialise(applicationRegistry);

    }


    private File getConfigFile(final String fileName,
                               final String defaultFileName,
                               final String qpidHome, boolean throwOnFileNotFound) throws InitException
    {
        File configFile = null;
        if (fileName != null)
        {
            configFile = new File(fileName);
        }
        else
        {
            configFile = new File(qpidHome, defaultFileName);
        }

        if (!configFile.exists() && throwOnFileNotFound)
        {
            String error = "File " + configFile + " could not be found. Check the file exists and is readable.";

            if (qpidHome == null)
            {
                error = error + "\nNote: " + BrokerOptions.QPID_HOME + " is not set.";
            }

            throw new InitException(error, null);
        }

        return configFile;
    }

    public static void parsePortList(Set<Integer> output, List<?> ports) throws InitException
    {
        if(ports != null)
        {
            for(Object o : ports)
            {
                try
                {
                    output.add(Integer.parseInt(String.valueOf(o)));
                }
                catch (NumberFormatException e)
                {
                    throw new InitException("Invalid port: " + o, e);
                }
            }
        }
    }

    private void configureLogging(File logConfigFile, int logWatchTime) throws InitException, IOException
    {
        if (logConfigFile.exists() && logConfigFile.canRead())
        {
            CurrentActor.get().message(BrokerMessages.LOG_CONFIG(logConfigFile.getAbsolutePath()));

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
            LOGGER.debug("Skipping shutdown hook removal as there either isnt one, or we are it.");
        }
    }
    /**
     * Workaround that prevents AMQShortStrings cache from being left in the thread local. This is important
     * when embedding the Broker in containers where the starting thread may not belong to Qpid.
     * The long term solution here is to stop our use of AMQShortString outside the AMQP transport layer.
     */
    private void clearAMQShortStringCache()
    {
        AMQShortString.clearLocalCache();
    }

    private class ShutdownService implements Runnable
    {
        public void run()
        {
            LOGGER.debug("Shutdown hook running");
            BrokerLauncher.this.shutdown();
        }
    }

}
