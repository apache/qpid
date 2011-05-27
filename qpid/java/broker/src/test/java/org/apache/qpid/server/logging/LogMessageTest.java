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
package org.apache.qpid.server.logging;

import junit.framework.TestCase;
import org.apache.qpid.server.logging.messages.BrokerMessages;

import java.util.Locale;
import java.util.ResourceBundle;

public class LogMessageTest extends TestCase
{

    /**
     * Test that the US local has a loadable bundle.
     * No longer have a specific en_US bundle so cannot verify that that version
     * is loaded. Can only verify that we get a ResourceBundle loaded.
     */
    public void testBundle()
    {
        Locale usLocal = Locale.US;
        Locale.setDefault(usLocal);
        ResourceBundle _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Broker_logmessages",
                                                            usLocal);

        assertNotNull("Unable to load ResourceBundle", _messages);
    }

    /**
     * Test that loading an undefined locale will result in loading of the
     * default US locale.
     */
    public void testUndefinedLocale()
    {
        Locale japanese = Locale.JAPANESE;

        Locale.setDefault(japanese);
        try
        {
            ResourceBundle _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.Broker_logmessages",
                                                                japanese);

            assertNotNull("Unable to load ResourceBundle", _messages);

            // If we attempt to load an undefined locale it should default to the Root locale.
            assertEquals("Loaded bundle has incorrect locale.", Locale.ROOT, _messages.getLocale());
        }
        catch (Throwable t)
        {
            fail(t.getMessage());
        }
    }

    /**
     * test Simultaneous log message generation.
     * QPID-2137 highlighted that log message generation was not thread-safe.
     * Test to ensure that simultaneous logging is possible and does not throw an exception.
     * @throws InterruptedException if there is a problem joining logging threads.
     */
    public void testSimultaneousLogging() throws InterruptedException
    {
        int LOGGERS = 10;
        int LOG_COUNT = 10;
        LogGenerator[] logGenerators = new LogGenerator[LOGGERS];
        Thread[] threads = new Thread[LOGGERS];

        //Create Loggers
        for (int i = 0; i < LOGGERS; i++)
        {
            logGenerators[i] = new LogGenerator(LOG_COUNT);
            threads[i] = new Thread(logGenerators[i]);
        }

        //Run Loggers
        for (int i = 0; i < LOGGERS; i++)
        {
            threads[i].start();
        }

        //End Loggers
        for (int i = 0; i < LOGGERS; i++)
        {
            threads[i].join();
            Exception e = logGenerators[i].getThrowException();
            // If we have an exception something went wrong.
            // Check and see if it was QPID-2137
            if (e != null)
            {
                // Just log out if we find the usual exception causing QPID-2137
                if (e instanceof StringIndexOutOfBoundsException)
                {
                    System.err.println("Detected QPID-2137");
                }
                fail("Exception thrown during log generation:" + e);
            }
        }
    }

    /**
     * Inner class used by testSimultaneousLogging.
     *
     * This class creates a given number of LogMessages using the BrokerMessages package.
     * CONFIG and LISTENING messages are both created per count.
     *
     * This class is run multiple times simultaneously so that we increase the chance of
     * reproducing QPID-2137. This is reproduced when the pattern string used in the MessageFormat
     * class is changed whilst formatting is taking place.
     *
     */
    class LogGenerator implements Runnable
    {
        private Exception _exception = null;
        private int _count;

        /**
         * @param count The number of Log Messages to generate
         */
        LogGenerator(int count)
        {
            _count = count;
        }

        public void run()
        {
            try
            {
                // try and generate _count iterations of Config & Listening messages.
                for (int i = 0; i < _count; i++)
                {
                    BrokerMessages.CONFIG("Config");
                    BrokerMessages.LISTENING("TCP", 1234);
                }
            }
            catch (Exception e)
            {
                // if something goes wrong recorded it for later analysis.
                _exception = e;
            }
        }

        /**
         * Return any exception that was thrown during the log generation.
         * @return Exception
         */
        public Exception getThrowException()
        {
            return _exception;
        }
    }

}