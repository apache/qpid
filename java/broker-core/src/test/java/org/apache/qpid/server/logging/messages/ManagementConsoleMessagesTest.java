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

/**
 * Test MNG Log Messages
 */
public class ManagementConsoleMessagesTest extends AbstractTestMessages
{
    public void testManagementStartup()
    {
        _logMessage = ManagementConsoleMessages.STARTUP("My");
        List<Object> log = performLog();

        String[] expected = {"My Management Startup"};

        validateLogMessage(log, "MNG-1001", expected);
    }

    public void testManagementListening()
    {
        String transport = "JMX";
        Integer port = 8889;

        _logMessage = ManagementConsoleMessages.LISTENING(transport, port);
        List<Object> log = performLog();

        String[] expected = {"Starting :", transport, ": Listening on port", String.valueOf(port)};

        validateLogMessage(log, "MNG-1002", expected);
    }

    public void testManagementShuttingDown()
    {
        String transport = "JMX";
        Integer port = 8889;

        _logMessage = ManagementConsoleMessages.SHUTTING_DOWN(transport, port);
        List<Object> log = performLog();

        String[] expected = {"Shutting down :", transport, ": port", String.valueOf(port)};

        validateLogMessage(log, "MNG-1003", expected);
    }

    public void testManagementReady()
    {
        _logMessage = ManagementConsoleMessages.READY("My");
        List<Object> log = performLog();

        String[] expected = {"My Management Ready"};

        validateLogMessage(log, "MNG-1004", expected);
    }

    public void testManagementStopped()
    {
        _logMessage = ManagementConsoleMessages.STOPPED("My");
        List<Object> log = performLog();

        String[] expected = {"My Management Stopped"};

        validateLogMessage(log, "MNG-1005", expected);
    }


}
