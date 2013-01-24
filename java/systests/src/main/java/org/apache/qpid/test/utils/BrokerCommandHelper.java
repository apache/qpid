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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
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
    private static final Logger _logger = Logger.getLogger(BrokerCommandHelper.class);

    /** use a LinkedList so we can call remove(int) */
    private final LinkedList<String> _brokerCommandTemplateAsList;

    public BrokerCommandHelper(String brokerCommandTemplate)
    {
        _brokerCommandTemplateAsList = new LinkedList(Arrays.asList(brokerCommandTemplate.split("\\s+")));
    }

    public List<String> getBrokerCommand(
        int port, int sslPort, int managementPort,
        File configFile, File logConfigFile,
        String protocolIncludesList, String protocolExcludesList)
    {
        List<String> substitutedCommands = new ArrayList<String>();

        // Replace the tokens in two passes.
        // The first pass is for protocol include/exclude lists, each of which needs to be
        // split into separate entries in the final list.
        // The second pass is for the remaining options, which include file paths that
        // are quoted in case they contain whitespace.

        for (String commandPart : _brokerCommandTemplateAsList)
        {
            String substitutedCommandPart = commandPart
                    .replace("@EXCLUDES", protocolExcludesList)
                    .replace("@INCLUDES", protocolIncludesList);

            String[] splitCommandPart = StringUtils.split(substitutedCommandPart);
            substitutedCommands.addAll(Arrays.asList(splitCommandPart));
        }

        int i = 0;
        for (String commandPart : substitutedCommands)
        {
            String substitutedCommandPart = commandPart
                    .replace("@PORT", "" + port)
                    .replace("@SSL_PORT", "" + sslPort)
                    .replace("@MPORT", "" + managementPort)
                    .replace("@CONFIG_FILE", '"' + configFile.getPath() + '"')
                    .replace("@LOG_CONFIG_FILE", '"' + logConfigFile.getAbsolutePath() + '"');

            substitutedCommands.set(i, substitutedCommandPart);
            i++;
        }

        return substitutedCommands;
    }

    private int getBrokerCommandLogOptionIndex()
    {
        String logOption = "-l";

        int logOptionIndex = _brokerCommandTemplateAsList.indexOf(logOption);
        if(logOptionIndex == -1)
        {
            throw new RuntimeException("Could not find option " + logOption + " in " + _brokerCommandTemplateAsList);
        }
        return logOptionIndex;
    }

    public void removeBrokerCommandLog4JFile()
    {
        int logOptionIndex = getBrokerCommandLogOptionIndex();
        _brokerCommandTemplateAsList.remove(logOptionIndex); // removes the "-l" entry
        _brokerCommandTemplateAsList.remove(logOptionIndex); // removes log file name that followed the -l
        _logger.info("Removed broker command log4j config file");
    }
}
