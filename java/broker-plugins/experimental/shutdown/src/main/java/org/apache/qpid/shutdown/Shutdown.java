/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.qpid.shutdown;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

/**
 * Implementation of the JMX broker shutdown plugin.
 */
public class Shutdown implements ShutdownMBean
{
    private static final Logger _logger = Logger.getLogger(Shutdown.class);

    private static final String FORMAT = "yyyyy/MM/dd hh:mm:ss";
    private static final int THREAD_COUNT = 1;
    private static final ScheduledExecutorService EXECUTOR = new ScheduledThreadPoolExecutor(THREAD_COUNT);

    private final Runnable _shutdown = new SystemExiter();

    /** @see ShutdownMBean#shutdown() */
    public void shutdown()
    {
        _logger.info("Shutting down at user's request");
        shutdownBroker(0);
    }

    /** @see ShutdownMBean#shutdown(long) */
    public void shutdown(long delay)
    {
        _logger.info("Scheduled broker shutdown after " + delay + "ms");
        shutdownBroker(delay);
    }

    /** @see ShutdownMBean#shutdownAt(String) */
    public void shutdownAt(String when)
    {
        Date date;
        DateFormat df = new SimpleDateFormat(FORMAT);
        try
        {
            date = df.parse(when);
        }
        catch (ParseException e)
        {
            _logger.error("Invalid date \"" + when + "\": expecting " + FORMAT, e);
            return;
        }
        _logger.info("Scheduled broker shutdown at " + when);
        long now = System.currentTimeMillis();
        long time = date.getTime();
        if (time > now)
        {
            shutdownBroker(time - now);
        }
        else
        {
            shutdownBroker(0);
        }
    }

    /**
     * Submits the {@link SystemExiter} job to shutdown the broker.
     */
    private void shutdownBroker(long delay)
    {
        EXECUTOR.schedule(_shutdown, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Shutting down the system in another thread to avoid JMX exceptions being thrown.
     */
    class SystemExiter implements Runnable
    {
        public void run()
        {
            System.exit(0);
        }
    }
}
