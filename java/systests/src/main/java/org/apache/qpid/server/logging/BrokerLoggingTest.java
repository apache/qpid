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

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.LogMonitor;

import java.io.File;
import java.util.List;

public class BrokerLoggingTest extends AbstractTestLogging
{
    LogMonitor _monitor;

    public void setUp() throws Exception
    {
        // set QPID_WORK to be [QPID_WORK|io.tmpdir]/<testName>
        setSystemProperty("QPID_WORK",
                          System.getProperty("QPID_WORK",
                                             System.getProperty("java.io.tmpdir"))
                          + File.separator + getName());

//        makeVirtualHostPersistent("test");

        _monitor = new LogMonitor(_outputFile);

        //We explicitly do not call super.setUp as starting up the broker is
        //part of the test case.
    }

    /**
     * Description:
     * On startup the broker must report the active configuration file. The
     * logging system must output this so that we can know what configuration
     * is being used for this broker instance.
     *
     * Input:
     * The value of -c specified on the command line.
     * Output:
     * <date> MESSAGE BRK-1006 : Using configuration : <config file>
     * Constraints:
     * This MUST BE the first BRK log message.
     *
     * Validation Steps:
     * 1. This is first BRK log message.
     * 2. The BRK ID is correct
     * 3. The config file is the full path to the file specified on
     * the commandline.
     *
     * @throws Exception caused by broker startup
     */
    public void testBrokerStartupConfiguration() throws Exception
    {
        // This logging model only applies to the Java broker
        if (isJavaBroker() && isExternalBroker())
        {
            startBroker();

            String configFilePath = _configFile.toString();

            List<String> results = _monitor.findMatches("BRK-");

            // Validation

            assertTrue("BRKer message not logged", results.size() > 0);

            String log = getLog(results.get(0));

            //1
            validateMessageID("BRK-1006",log);

            //2
            results = _monitor.findMatches("BRK-1006");
            assertEquals("More than one configuration message found.",
                         1, results.size());

            //3
            assertTrue("Config file details not correctly logged",
                       log.endsWith(configFilePath));

        }
    }

}
