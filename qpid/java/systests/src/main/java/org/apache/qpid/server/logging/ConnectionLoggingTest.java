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

import javax.jms.Connection;
import java.util.List;
import java.util.HashMap;
import java.util.TreeSet;

public class ConnectionLoggingTest extends AbstractTestLogging
{
    private static final String CONNECTION_PREFIX = "CON-";

    // No explicit startup configuration is required for this test
    // so no setUp() method

    /**
     * Description:
     * When a new connection is made to the broker this must be logged.
     *
     * Input:
     * 1. Running Broker
     * 2. Connecting client
     * Output:
     * <date> CON-1001 : Open : Client ID {0}[ : Protocol Version : {1}] <version>
     *
     * Validation Steps:
     * 1. The CON ID is correct
     * 2. This is the first CON message for that Connection
     *
     * @throws Exception - if an error occurs
     */
    public void testConnectionOpen() throws Exception
    {
        assertLoggingNotYetOccured(CONNECTION_PREFIX);

        Connection connection = getConnection();
        String clientid = connection.getClientID();

        // Wait until opened
        waitForMessage("CON-1001");
        
        // Close the conneciton
        connection.close();

        // Wait to ensure that the desired message is logged
        waitForMessage("CON-1002");

        List<String> results = waitAndFindMatches("CON-1001");

        // Validation
        // We should have at least three messages when running InVM but when running External
        // we will get 0-10 negotiation on con:0 whcih may close at some random point
        // MESSAGE [con:0(/127.0.0.1:46926)] CON-1001 : Open
        // MESSAGE [con:0(/127.0.0.1:46926)] CON-1001 : Open : Protocol Version : 0-10
        // MESSAGE [con:1(/127.0.0.1:46927)] CON-1001 : Open
        // MESSAGE [con:1(/127.0.0.1:46927)] CON-1001 : Open : Protocol Version : 0-9
        // MESSAGE [con:0(/127.0.0.1:46926)] CON-1002 : Close
        // MESSAGE [con:1(/127.0.0.1:46927)] CON-1001 : Open : Client ID : clientid : Protocol Version : 0-9

        //So check how many connections we have in the result set and extract the last one.
        // When running InVM we will have con:0 and externally con:1

        HashMap<Integer, List<String>> connectionData = splitResultsOnConnectionID(results);

        // Get the last Integer from keySet of the ConnectionData
        int connectionID = new TreeSet<Integer>(connectionData.keySet()).last();

        //Use just the data from the last connection for the test
        results = connectionData.get(connectionID);

        // If we are running inVM or with 0-10 we will get three open messagse
	    // if running externally with 0-8/0-9 we will also have open and close messages from the failed 0-10 negotiation 
	    assertTrue("CON messages not logged:" + results.size(), results.size() >= 3);

        String log = getLogMessage(results, 0);
        //  MESSAGE [con:1(/127.0.0.1:52540)] CON-1001 : Open
        //1 & 2
        validateMessageID("CON-1001",log);

        // validate the last three CON- messages.
        // This is because when running externally we may also have logged the failed
        // 0-10 negotiation messages if using 0-8/0-9 on the broker.

        // 3 - Assert the options are correct
        //  MESSAGE [con:1(/127.0.0.1:52540)] CON-1001 : Open : Client ID : clientid : Protocol Version : 0-9
        validateConnectionOpen(results, 0, true, true, clientid);

        //  MESSAGE [con:1(/127.0.0.1:52540)] CON-1001 : Open : Protocol Version : 0-9
        validateConnectionOpen(results, 1, true, false, null);

        validateConnectionOpen(results, 2, false, false, null);
    }
    
    private void validateConnectionOpen(List<String> results, int positionFromEnd,
                 boolean protocolVersionPresent, boolean clientIdOptionPresent, String clientIdValue)
    {
        String log = getLogMessageFromEnd(results, positionFromEnd);
        
        validateMessageID("CON-1001",log);
        
        assertEquals("unexpected Client ID option state", clientIdOptionPresent, fromMessage(log).contains("Client ID :"));
        
        if(clientIdOptionPresent && clientIdValue != null)
        {
            assertTrue("Client ID value is not present: " + clientIdValue, fromMessage(log).contains(clientIdValue));
        }
        
        assertEquals("unexpected Protocol Version option state", 
                protocolVersionPresent, fromMessage(log).contains("Protocol Version :"));
        //fixme there is no way currently to find out the negotiated protocol version
        // The delegate is the versioned class ((AMQConnection)connection)._delegate
    }

    /**
     * Description:
     * When a connected client closes the connection this will be logged as a CON-1002 message.
     * Input:
     *
     * 1. Running Broker
     * 2. Connected Client
     * Output:
     *
     * <date> CON-1002 : Close
     *
     * Validation Steps:
     * 3. The CON ID is correct
     * 4. This must be the last CON message for the Connection
     * 5. It must be preceded by a CON-1001 for this Connection
     */
    public void testConnectionClose() throws Exception
    {
        assertLoggingNotYetOccured(CONNECTION_PREFIX);

        Connection connection = getConnection();

        // Wait until opened
        waitForMessage("CON-1001");
        
        // Close the conneciton
        connection.close();

        // Wait to ensure that the desired message is logged
        waitForMessage("CON-1002");

        List<String> results = findMatches(CONNECTION_PREFIX);

        // Validation

        // We should have at least four messages
        assertTrue("CON messages not logged:" + results.size(), results.size() >= 4);

        // Validate Close message occurs
        String log = getLogMessageFromEnd(results, 0);
        validateMessageID("CON-1002",log);
        assertTrue("Message does not end with close:" + log, log.endsWith("Close"));

        // Extract connection ID to validate there is a CON-1001 messasge for it
        int closeConnectionID = getConnectionID(fromSubject(log));
        assertTrue("Could not find connection id in CLOSE", closeConnectionID != -1);

        //Previous log message should be the open
        log = getLogMessageFromEnd(results, 1);
        //  MESSAGE [con:1(/127.0.0.1:52540)] CON-1001 : Open : Client ID : clientid : Protocol Version : 0-9
        validateMessageID("CON-1001",log);

        // Extract connection ID to validate it matches the CON-1002 messasge
        int openConnectionID = getConnectionID(fromActor(log));
        assertTrue("Could not find connection id in OPEN", openConnectionID != -1);
        
        // Check connection ids match
        assertEquals("Connection IDs do not match", closeConnectionID, openConnectionID);
    }
}
