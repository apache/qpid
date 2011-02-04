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

import org.apache.qpid.client.AMQConnection;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.List;

public class ChannelLoggingTest extends AbstractTestLogging
{
    private static final String CHANNEL_PREFIX = "CHN-";

    // No explicit startup configuration is required for this test
    // so no setUp() method

    /**
     * Description:
     * When a new Channel (JMS Session) is created this will be logged as a CHN-1001 Create message. The messages will contain the prefetch details about this new Channel.
     * Input:
     *
     * 1. Running Broker
     * 2. New JMS Session/Channel creation
     *
     * Output:
     * <date> CHN-1001 : Create
     * <date> CHN-1004 : Prefetch Size (bytes) {0,number} : Count {1,number}
     *
     * Validation Steps:
     * 1. The CHN ID is correct
     * 2. The prefetch value matches that defined by the requesting client.
     *
     * @throws Exception - if an error occurs
     */
    public void testChannelCreate() throws Exception
    {
        assertLoggingNotYetOccured(CHANNEL_PREFIX);

        Connection connection = getConnection();

        int PREFETCH = 12;

        // Test that calling session.close gives us the expected output
        ((AMQConnection)connection).createSession(false, Session.AUTO_ACKNOWLEDGE,PREFETCH);

        // Wait to ensure that the CHN-1001 message is logged
        waitForMessage("CHN-1001");

        List<String> results = findMatches("CHN-1001");

        // Validation
        assertEquals("CHN-1001 messages not logged", 1, results.size());

        String log = getLogMessage(results, 0);
        //  MESSAGE [con:0(guest@anonymous(3273383)/test)/ch:1] CHN-1001 : Create
        validateMessageID("CHN-1001", log);
        assertEquals("Incorrect Channel in actor:"+fromActor(log), isBroker010()? 0 : 1, getChannelID(fromActor(log)));

        if (isBroker08())
        {
            // Wait to ensure that the CHN-1004 message is logged
            waitForMessage("CHN-1004");

            results = findMatches("CHN-1004");

            // Validation
            assertEquals("CHN-1004 messages not logged", 1, results.size());
            log = getLogMessage(results, 0);
            //  MESSAGE [con:0(guest@anonymous(3273383)/test)/ch:1] CHN-1004 : Prefetch Size (bytes) {0,number} : Count {1,number}
            validateMessageID("CHN-1004", log);
            assertEquals("Incorrect Channel in actor:"+fromActor(log), isBroker010()? 0 : 1, getChannelID(fromActor(log)));
            assertTrue("Prefetch Count not correct",getMessageString(fromMessage(log)).endsWith("Count "+PREFETCH));
        }

        connection.close();
    }

    /**
     * Description:
     * The Java Broker implements consumer flow control for all ack modes except
     * No-Ack. When a client connects the session's flow is initially set to
     * Stopped. Verify this message appears
     *
     * Input:
     * 1. Running broker
     * 2. Create consumer
     * Output:
     *
     * <date> CHN-1002 : Flow Stopped
     *
     * Validation Steps:
     * 4. The CHN ID is correct
     *
     * @throws Exception - if an error occurs
     */

    public void testChannelStartsFlowStopped() throws Exception
    {
        assertLoggingNotYetOccured(CHANNEL_PREFIX);

        Connection connection = getConnection();

        // Create a session to fill up
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = (Queue) getInitialContext().lookup(QUEUE);
        MessageConsumer consumer = session.createConsumer(queue);

        connection.start();

        // Wait to ensure that the CHN-1002 message is logged
        waitForMessage("CHN-1002");

        List<String> results = findMatches(CHANNEL_PREFIX);

        assertTrue("No CHN messages logged", results.size() > 0);

        // The last channel message should be:
        //
        // INFO - MESSAGE [con:0(guest@anonymous(4205299)/test)/ch:1] [con:0(guest@anonymous(4205299)/test)/ch:1] CHN-1002 : Flow Stopped

        // Verify the last channel message is stopped
        validateChannelStart(results, false);
    }

    private void validateChannelStart(List<String> results, boolean flowStarted)
    {
        String log = getLogMessageFromEnd(results, 0);

        String flow = flowStarted ? "Started" : "Stopped";
        validateMessageID("CHN-1002", log);
        assertEquals("Message should be Flow " + flow, "Flow " + flow, getMessageString(fromMessage(log)));
    }

    /**
     * Description:
     * The Java Broker implements consumer flow control for all ack modes except
     * No-Ack. When the client first attempts to receive a message then the Flow
     * status of the Session is set to Started.
     *
     * Input:
     * 1. Running broker
     * 2. Create a consumer
     * 3. Attempt to receive a message
     * Output:
     *
     * <date> CHN-1002 : Flow Started
     *
     * Validation Steps:
     * 4. The CHN ID is correct
     *
     * @throws Exception - if an error occurs
     */

    public void testChannelStartConsumerFlowStarted() throws Exception
    {
        assertLoggingNotYetOccured(CHANNEL_PREFIX);

        Connection connection = getConnection();

        // Create a session to fill up
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Queue queue = (Queue) getInitialContext().lookup(QUEUE);
        MessageConsumer consumer = session.createConsumer(queue);

        connection.start();

        //Call receive to send the Flow On message
        consumer.receiveNoWait();

        //Wait for up to 2 seconds for message to appear
        // ignore response as we will use the findMatches afterwards just
        // incase it did take more than 2 seconds to log.
        _monitor.waitForMessage(CHANNEL_PREFIX, 2000);

        // Wait to ensure that the CHN-1002 message is logged
        waitForMessage("CHN-1002");

        List<String> results = findMatches(CHANNEL_PREFIX);

        assertTrue("No CHN messages logged", results.size() > 0);

        // The last two channel messages(before the close) should be:
        //
        // INFO [qpid.message] MESSAGE [con:1(guest@/127.0.0.1:49869/test)/ch:1] [con:1(guest@/127.0.0.1:49869/test)/ch:1] CHN-1002 : Flow Stopped
        // INFO [qpid.message] MESSAGE [con:1(guest@/127.0.0.1:49869/test)/ch:1] [con:1(guest@/127.0.0.1:49869/test)/ch:1] CHN-1002 : Flow Started

        // Verify the last channel msg is Started.
        validateChannelStart(results, true);
    }

    /**
     * Description:
     * When the client gracefully closes the Connection then a CHN-1003 Close
     * message will be issued. This must be the last message logged for this
     * Channel.
     * Input:
     * 1. Running Broker
     * 2. Connected Client
     * 3. Client then requests that the Connection is closed
     * Output:
     *
     * <date> CHN-1003 : Close
     *
     * Validation Steps:
     * 4. The MST ID is correct
     * 5. This must be the last message logged for this Channel.
     *
     * @throws Exception - if an error occurs
     */
    public void testChannelCloseViaConnectionClose() throws Exception
    {
        assertLoggingNotYetOccured(CHANNEL_PREFIX);

        Connection connection = getConnection();

        // Create a session
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        // Close the connection to verify the created session closing is logged.
        connection.close();

        // Wait to ensure that the CHN-1003 message is logged
        waitForMessage("CHN-1003");

        List<String> results = findMatches(CHANNEL_PREFIX);

        assertTrue("No CHN messages logged", results.size() > 0);

        // The last two channel messages should be:
        //
        // INFO - MESSAGE [con:0(guest@anonymous(4205299)/test)/ch:1] [con:0(guest@anonymous(4205299)/test)/ch:1] CHN-1002 : Flow On

        // Verify
        validateChannelClose(results);
    }

    /**
     * Description:
     * When the client gracefully closes the Connection then a CHN-1003 Close
     * message will be issued. This must be the last message logged for this
     * Channel.
     * Input:
     * 1. Running Broker
     * 2. Connected Client
     * 3. Client then requests that the Channel is closed
     * Output:
     *
     * <date> CHN-1003 : Close
     *
     * Validation Steps:
     * 4. The MST ID is correct
     * 5. This must be the last message logged for this Channel.
     *
     * @throws Exception - if an error occurs
     */
    public void testChannelCloseViaChannelClose() throws Exception
    {
        assertLoggingNotYetOccured(CHANNEL_PREFIX);

        Connection connection = getConnection();

        // Create a session and then close it
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        waitForMessage("CHN-1001");

        // Wait to ensure that the CHN-1003 message is logged
        session.close();
        waitForMessage("CHN-1003");

        List<String> results = findMatches(CHANNEL_PREFIX);

        assertTrue("No CHN messages logged", results.size() > 0);

        // Verify
        validateChannelClose(results);
    }

    private void validateChannelClose(List<String> results)
    {
        String open = getLogMessage(results, 0);
        String close = getLogMessageFromEnd(results, 0);

        validateMessageID("CHN-1001", open);
        validateMessageID("CHN-1003", close);
        assertEquals("Message should be Close", "Close", getMessageString(fromMessage(close)));
        assertEquals("Incorrect Channel ID closed", isBroker010()? 0 : 1, getChannelID(fromSubject(close)));
        assertEquals("Channel IDs should be the same", getChannelID(fromActor(open)), getChannelID(fromSubject(close)));
        assertEquals("Connection IDs should be the same", getConnectionID(fromActor(open)), getConnectionID(fromSubject(close)));
    }
}