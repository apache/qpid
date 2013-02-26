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

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Generates the command to start a broker by substituting the tokens
 * in the provided broker command.
 *
 * The command is returned as a list so that it can be easily used by a
 * {@link java.lang.ProcessBuilder}.
 */
public class BrokerCommandHelper
{
    private final List<String> _brokerCommandTemplateAsList;

    public BrokerCommandHelper(String brokerCommandTemplate)
    {
        _brokerCommandTemplateAsList = new LinkedList<String>(Arrays.asList(brokerCommandTemplate.split("\\s+")));
    }

    public String[] getBrokerCommand( int port, String storePath, String storeType, File logConfigFile)
    {
        String[] command = new String[_brokerCommandTemplateAsList.size()];
        int i=0;
        for (String commandPart : _brokerCommandTemplateAsList)
        {
            command[i] = commandPart
                    .replace("@PORT", "" + port)
                    .replace("@STORE_PATH", storePath)
                    .replace("@STORE_TYPE", storeType)
                    .replace("@LOG_CONFIG_FILE", '"' + logConfigFile.getAbsolutePath() + '"');
            i++;
        }
        return command;
    }

    private int getBrokerCommandLogOptionIndex(String logOption)
    {
        int logOptionIndex = _brokerCommandTemplateAsList.indexOf(logOption);
        if(logOptionIndex == -1)
        {
            throw new RuntimeException("Could not find option " + logOption + " in " + _brokerCommandTemplateAsList);
        }
        return logOptionIndex;
    }


    public void removeBrokerCommandLog4JFile()
    {
        String logOption = "-l";
        int logOptionIndex = getBrokerCommandLogOptionIndex(logOption);
        if (logOptionIndex + 1 >=  _brokerCommandTemplateAsList.size())
        {
            throw new RuntimeException("Could not find log config location");
        }
        _brokerCommandTemplateAsList.remove(logOptionIndex);
        _brokerCommandTemplateAsList.remove(logOptionIndex);
    }
}
