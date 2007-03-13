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
package org.apache.qpid.interop.testclient.testcases;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.qpid.interop.testclient.InteropClientTestCase;

/**
 * Implements test case 2, basic P2P. Sends/received a specified number of messages to a specified route on the
 * default direct exchange. Produces reports on the actual number of messages sent/received.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Supply the name of the test case that this implements.
 * <tr><td> Accept/Reject invites based on test parameters.
 * <tr><td> Adapt to assigned roles.
 * <tr><td> Send required number of test messages.
 * <tr><td> Generate test reports.
 * </table>
 */
public class TestCase2BasicP2P implements InteropClientTestCase
{
    /**
     * Should provide the name of the test case that this class implements. The exact names are defined in the
     * interop testing spec.
     *
     * @return The name of the test case that this implements.
     */
    public String getName()
    {
        return "TC2_BasicP2P";
    }

    /**
     * Determines whether the test invite that matched this test case is acceptable.
     *
     * @param inviteMessage The invitation to accept or reject.
     *
     * @return <tt>true</tt> to accept the invitation, <tt>false</tt> to reject it.
     *
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public boolean acceptInvite(Message inviteMessage) throws JMSException
    {
        // All invites are acceptable.
        return true;
    }

    /**
     * Assigns the role to be played by this test case. The test parameters are fully specified in the
     * assignment message. When this method return the test case will be ready to execute.
     *
     * @param role              The role to be played; sender or receiver.
     *
     * @param assignRoleMessage The role assingment message, contains the full test parameters.
     *
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public void assignRole(Roles role, Message assignRoleMessage) throws JMSException
    {
        // Take note of the role to be played.

        // Extract and retain the test parameters.

        // Create a new connection to pass the test messages on.

        // Check if the sender role is being assigned, and set up a message producer if so.
        {
        }
        // Otherwise the receiver role is being assigned, so set this up to listen for messages.
        {
        }
    }

    /**
     * Performs the test case actions.
     */
    public void start()
    {
        // Check that the sender role is being performed.
        {
        }
    }

    /**
     * Gets a report on the actions performed by the test case in its assigned role.
     *
     * @param session The session to create the report message in.
     *
     * @return The report message.
     *
     * @throws JMSException Any JMSExceptions resulting from creating the report are allowed to fall through.
     */
    public Message getReport(Session session) throws JMSException
    {
        // Close the test connection.

        // Generate a report message containing the count of the number of messages passed.

        return null;
    }

    /**
     * Counts incoming test messages.
     *
     * @param message The incoming test message.
     */
    public void onMessage(Message message)
    {
        // Increment the message count.
    }
}
