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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class BrokerCommandHelperTest extends QpidTestCase
{
    private BrokerCommandHelper _brokerCommandHelper = new BrokerCommandHelper("qpid -p @PORT -s @SSL_PORT -m @MPORT @INCLUDES @EXCLUDES -c @CONFIG_FILE -l @LOG_CONFIG_FILE");

    private File logConfigFile = mock(File.class);
    private File configFile = mock(File.class);

    @Override
    public void setUp()
    {
        when(logConfigFile.getAbsolutePath()).thenReturn("logConfigFile");
        when(configFile.getPath()).thenReturn("configFile");
    }

    public void testGetBrokerCommand()
    {
        List<String> brokerCommand = _brokerCommandHelper.getBrokerCommand(
            1, 2, 3, configFile, logConfigFile, "includes", "excludes");

        assertEquals(Arrays.asList("qpid", "-p", "1", "-s", "2", "-m", "3",
                                   "includes", "excludes",
                                   "-c", "\"configFile\"", "-l", "\"logConfigFile\""),
                     brokerCommand);
    }

    public void testGetBrokerCommandForMultipleProtocolIncludesAndExcludes()
    {
        List<String> brokerCommand = _brokerCommandHelper.getBrokerCommand(
            1, 2, 3, configFile, logConfigFile, "includes1 includes2", "excludes1 excludes2");

        assertEquals("The space-separated protocol include/exclude lists should be converted into separate entries in the returned list so that ProcessBuilder treats them as separate arguments",
                     Arrays.asList("qpid", "-p", "1", "-s", "2", "-m", "3",
                                   "includes1", "includes2",  "excludes1", "excludes2",
                                   "-c", "\"configFile\"", "-l", "\"logConfigFile\""),
                     brokerCommand);
    }

    public void testRemoveBrokerCommandLog4JFile()
    {
        _brokerCommandHelper.removeBrokerCommandLog4JFile();

        List<String> brokerCommand = _brokerCommandHelper.getBrokerCommand(
            1, 2, 3, configFile, logConfigFile, "includes", "excludes");

        assertEquals("The broker command list should not contain a log4j config option",
                     Arrays.asList("qpid", "-p", "1", "-s", "2", "-m", "3",
                                   "includes", "excludes",
                                   "-c", "\"configFile\""),
                     brokerCommand);
    }
}
