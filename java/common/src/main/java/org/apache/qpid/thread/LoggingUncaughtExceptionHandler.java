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
package org.apache.qpid.thread;

import java.lang.Thread.UncaughtExceptionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * An {@link UncaughtExceptionHandler} that writes the exception to the application log via
 * the SLF4J framework.  Once registered with {@link Thread#setUncaughtExceptionHandler(UncaughtExceptionHandler)}
 * it will be invoked by the JVM when a thread has been <i>abruptly</i> terminated due to an uncaught exception.
 * Owing to the contract of {@link Runnable#run()}, the only possible exception types which can cause such a termination
 * are instances of {@link RuntimeException} and {@link Error}.  These exceptions are catastrophic and the client must
 * restart the JVM.
 * <p>
 * The implementation also invokes {@link ThreadGroup#uncaughtException(Thread, Throwable)}.  This
 * is done to retain compatibility with any monitoring solutions (for example, log scraping of
 * standard error) that existing users of older Qpid client libraries may have in place.
 *
 */
public class LoggingUncaughtExceptionHandler implements UncaughtExceptionHandler
{
    private static final Logger _logger = LoggerFactory.getLogger(LoggingUncaughtExceptionHandler.class);

    @Override
    public void uncaughtException(Thread t, Throwable e)
    {
        try
        {
            _logger.error("Uncaught exception in thread \"{}\"", t.getName(), e);
        }
        finally
        {
            // Invoke the thread group's handler too for compatibility with any
            // existing clients who are already scraping stderr for such conditions.
            t.getThreadGroup().uncaughtException(t, e);
        }
    }
}