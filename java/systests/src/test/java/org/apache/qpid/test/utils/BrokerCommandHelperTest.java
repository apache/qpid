/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.test.utils;

import static org.mockito.Mockito.*;

import java.io.File;

public class BrokerCommandHelperTest extends QpidTestCase
{
    private static final String PATH_TO_QPID_EXECUTABLE = "/path  / to (/qpid";
    private static final String ARGUMENT_WITH_SPACES = " blah / blah /blah";
    private static final String ARGUMENT_PORT = "-p";
    private static final String ARGUMENT_PORT_VALUE = "@PORT";
    private static final String ARGUMENT_STORE_PATH = "-sp";
    private static final String ARGUMENT_STORE_PATH_VALUE = "@STORE_PATH";
    private static final String ARGUMENT_STORE_TYPE = "-st";
    private static final String ARGUMENT_STORE_TYPE_VALUE = "@STORE_TYPE";
    private static final String ARGUMENT_LOG = "-l";
    private static final String ARGUMENT_LOG_VALUE = "@LOG_CONFIG_FILE";

    private BrokerCommandHelper _brokerCommandHelper;

    private File _logConfigFile = mock(File.class);

    @Override
    public void setUp()
    {
        when(_logConfigFile.getAbsolutePath()).thenReturn("log Config File");
        _brokerCommandHelper = new BrokerCommandHelper("\"" + PATH_TO_QPID_EXECUTABLE + "\" " + ARGUMENT_PORT + "     "
                + ARGUMENT_PORT_VALUE + " " + ARGUMENT_STORE_PATH + " " + ARGUMENT_STORE_PATH_VALUE + " " + ARGUMENT_STORE_TYPE
                + " " + ARGUMENT_STORE_TYPE_VALUE + " " + ARGUMENT_LOG + " " + ARGUMENT_LOG_VALUE + "     '" + ARGUMENT_WITH_SPACES
                + "'");
    }

    public void testGetBrokerCommand()
    {
        String[] brokerCommand = _brokerCommandHelper.getBrokerCommand(1, "path to config file", "json", _logConfigFile);

        String[] expected = { PATH_TO_QPID_EXECUTABLE, ARGUMENT_PORT, "1", ARGUMENT_STORE_PATH,  "path to config file",
                ARGUMENT_STORE_TYPE, "json", ARGUMENT_LOG, "\"log Config File\"", ARGUMENT_WITH_SPACES };
        assertEquals("Unexpected broker command", expected.length, brokerCommand.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals("Unexpected command part value at " + i,expected[i], brokerCommand[i] );
        }
    }

    public void testRemoveBrokerCommandLog4JFile()
    {
        _brokerCommandHelper.removeBrokerCommandLog4JFile();
        String[] brokerCommand = _brokerCommandHelper.getBrokerCommand(1, "configFile", "json", _logConfigFile);

        String[] expected = { PATH_TO_QPID_EXECUTABLE, ARGUMENT_PORT, "1", ARGUMENT_STORE_PATH, "configFile",
                ARGUMENT_STORE_TYPE, "json", ARGUMENT_WITH_SPACES };

        assertEquals("Unexpected broker command", expected.length, brokerCommand.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals("Unexpected command part value at " + i,expected[i], brokerCommand[i] );
        }
    }

}
