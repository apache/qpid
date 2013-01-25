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

/**
 * Generates the command to start a broker by substituting the tokens
 * in the provided broker command.
 *
 * The command is returned as a list so that it can be easily used by a
 * {@link java.lang.ProcessBuilder}.
 */
public class BrokerCommandHelper
{
    private String _brokerCommandTemplate;

    public BrokerCommandHelper(String brokerCommandTemplate)
    {
        _brokerCommandTemplate = brokerCommandTemplate;
    }

    public String getBrokerCommand( int port, String storePath, String storeType, File logConfigFile)
    {
        return _brokerCommandTemplate
                    .replace("@PORT", "" + port)
                    .replace("@STORE_PATH", storePath)
                    .replace("@STORE_TYPE", storeType)
                    .replace("@LOG_CONFIG_FILE", '"' + logConfigFile.getAbsolutePath() + '"');
    }

    public void removeBrokerCommandLog4JFile()
    {
        int logArgumentPosition = _brokerCommandTemplate.indexOf("-l");
        _brokerCommandTemplate = _brokerCommandTemplate.substring(0, logArgumentPosition - 1);
    }
}
