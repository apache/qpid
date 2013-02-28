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
    private BrokerCommandHelper _brokerCommandHelper = new BrokerCommandHelper("qpid -p @PORT -sp @STORE_PATH -st @STORE_TYPE -l @LOG_CONFIG_FILE");

    private File logConfigFile = mock(File.class);

    @Override
    public void setUp()
    {
        when(logConfigFile.getAbsolutePath()).thenReturn("log Config File");
    }

    public void testGetBrokerCommand()
    {
        String[] brokerCommand = _brokerCommandHelper.getBrokerCommand(1, "configFile", "json", logConfigFile);

        String[] expected = { "qpid", "-p", "1", "-sp", "configFile", "-st", "json", "-l", "\"log Config File\"" };
        assertEquals("Unexpected broker command", 9, brokerCommand.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals("Unexpected command part value at " + i,expected[i], brokerCommand[i] );
        }
    }

    public void testRemoveBrokerCommandLog4JFile()
    {
        _brokerCommandHelper.removeBrokerCommandLog4JFile();
        String[] brokerCommand = _brokerCommandHelper.getBrokerCommand(1, "configFile", "json", logConfigFile);

        String[] expected = { "qpid", "-p", "1", "-sp", "configFile", "-st", "json" };
        assertEquals("Unexpected broker command", 7, brokerCommand.length);
        for (int i = 0; i < expected.length; i++)
        {
            assertEquals("Unexpected command part value at " + i,expected[i], brokerCommand[i] );
        }
    }

}
