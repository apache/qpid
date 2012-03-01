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
package org.apache.qpid.server.signal;

import org.apache.log4j.Logger;

import org.apache.qpid.test.utils.QpidTestCase;

import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SignalHandlerTaskTest extends QpidTestCase
{
    private static final Logger LOGGER = Logger.getLogger(SignalHandlerTaskTest.class);
    private static final String SUN_MISC_SIGNAL_CLASS = "sun.misc.Signal";
    private static final String SUN_MISC_SIGNAL_HANDLER_CLASS = "sun.misc.SignalHandler";

    protected void setUp() throws Exception
    {
        super.setUp();
    }

    public void testSignalHandlerTask() throws Exception
    {
        final boolean expectedResult = classifyExpectedRegistrationResult();
        final int pid = getPID();
        final CountDownLatch latch = new CountDownLatch(1);

        SignalHandlerTask hupReparseTask = new SignalHandlerTask()
        {
            public void handle()
            {
                latch.countDown();
                LOGGER.info("Signal handled, latch decremented");
            }
        };

        assertEquals("Unexpected result trying to register Signal handler",
                expectedResult, hupReparseTask.register("HUP"));
        LOGGER.info("Signal handler was registered");

        assertEquals("unexpected count for the latch", 1, latch.getCount());

        if(expectedResult)
        {
            //registration succeeded as expected, so now
            //send SIGHUP and verify the handler was run
            String cmd = "/bin/kill -SIGHUP " + pid;
            
            LOGGER.info("Sending SIGHUP");
            Runtime.getRuntime().exec(cmd);

            assertTrue("HUP Signal was not handled in the allowed timeframe",
                    latch.await(5, TimeUnit.SECONDS));
        }
    }

    public void testGetPlatformDescription() throws Exception
    {
       assertNotNull(SignalHandlerTask.getPlatformDescription());
    }

    private boolean classifyExpectedRegistrationResult()
    {
        String os = System.getProperty("os.name");
        if(String.valueOf(os).toLowerCase().contains("windows"))
        {
            //Windows does not support SIGHUP so registration will fail
            LOGGER.info("Running on windows, so we expect SIGHUP handler registration to fail");
            return false;
        }

        //otherwise, if the signal handler classes are present we would expect
        //registration to succeed
        boolean classesPresent = true;
        try
        {
            Class.forName(SUN_MISC_SIGNAL_CLASS);
            Class.forName(SUN_MISC_SIGNAL_HANDLER_CLASS);
            LOGGER.info("Signal handling classes were present so we expect SIGHUP handler registration to succeed");
        }
        catch (ClassNotFoundException cnfe)
        {
            classesPresent = false;
        }

        return classesPresent;
    }

    private int getPID()
    {
        String processName = ManagementFactory.getRuntimeMXBean().getName();

        int pid = Integer.parseInt(processName.substring(0,processName.indexOf('@')));
        LOGGER.info("PID was determined to be " + pid);

        return pid;
    }

}
