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
package org.apache.qpid.server.logging.messages;

import java.util.List;

public class ManagementConsoleMessagesTest extends AbstractTestMessages
{
    public void testMessage1001()
    {
        _logMessage = ManagementConsoleMessages.MNG_1001();
        List<Object> log = performLog();

        String[] expected = {"Startup"};

        validateLogMessage(log, "MNG-1001", expected);
    }

    public void testMessage1002()
    {
        String transport = "JMX";
        Integer port = 8889;

        _logMessage = ManagementConsoleMessages.MNG_1002(transport, port);
        List<Object> log = performLog();

        String[] expected = {"Starting :", transport, ": Listening on port", String.valueOf(port)};

        validateLogMessage(log, "MNG-1002", expected);
    }

    public void testMessage1003()
    {
        String transport = "JMX";
        Integer port = 8889;

        _logMessage = ManagementConsoleMessages.MNG_1003(transport, port);
        List<Object> log = performLog();

        String[] expected = {"Shuting down :", transport, ": port", String.valueOf(port)};

        validateLogMessage(log, "MNG-1003", expected);
    }

    public void testMessage1004()
    {
        _logMessage = ManagementConsoleMessages.MNG_1004();
        List<Object> log = performLog();

        String[] expected = {"Ready"};

        validateLogMessage(log, "MNG-1004", expected);
    }

    public void testMessage1005()
    {
        _logMessage = ManagementConsoleMessages.MNG_1005();
        List<Object> log = performLog();

        String[] expected = {"Stopped"};

        validateLogMessage(log, "MNG-1005", expected);
    }

    public void testMessage1006()
    {
        String path = "/path/to/the/keystore/files.jks";

        _logMessage = ManagementConsoleMessages.MNG_1006(path);
        List<Object> log = performLog();

        String[] expected = {"Using SSL Keystore :", path};

        validateLogMessage(log, "MNG-1006", expected);
    }

}
