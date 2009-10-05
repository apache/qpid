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

public class ConnectionMessagesTest extends AbstractTestMessages
{
    public void testMessage1001_WithClientIDProtocolVersion()
    {
        String clientID = "client";
        String protocolVersion = "8-0";

        _logMessage = ConnectionMessages.CON_1001(clientID, protocolVersion, true , true);
        List<Object> log = performLog();

        String[] expected = {"Open :", "Client ID", clientID,
                             ": Protocol Version :", protocolVersion};

        validateLogMessage(log, "CON-1001", expected);
    }

    public void testMessage1001_WithClientIDNoProtocolVersion()
    {
        String clientID = "client";        

        _logMessage = ConnectionMessages.CON_1001(clientID, null,true, false);
        List<Object> log = performLog();

        String[] expected = {"Open :", "Client ID", clientID};

        validateLogMessage(log, "CON-1001", expected);
    }

    public void testMessage1001_WithNOClientIDProtocolVersion()
    {
        String protocolVersion = "8-0";

        _logMessage = ConnectionMessages.CON_1001(null, protocolVersion, false , true);
        List<Object> log = performLog();

        String[] expected = {"Open", ": Protocol Version :", protocolVersion};

        validateLogMessage(log, "CON-1001", expected);
    }

    public void testMessage1001_WithNoClientIDNoProtocolVersion()
    {
        _logMessage = ConnectionMessages.CON_1001(null, null,false, false);
        List<Object> log = performLog();

        String[] expected = {"Open"};

        validateLogMessage(log, "CON-1001", expected);
    }



    public void testMessage1002()
    {
        _logMessage = ConnectionMessages.CON_1002();
        List<Object> log = performLog();

        String[] expected = {"Close"};

        validateLogMessage(log, "CON-1002", expected);
    }

}
