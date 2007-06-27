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
package org.apache.qpid.interop.testclient;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * InteropClientTestCase provides an interface that classes implementing test cases from the interop testing spec
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification) should implement. Implementations
 * must be Java beans, that is, to provide a default constructor and to implement the {@link #getName} method.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Supply the name of the test case that this implements.
 * <tr><td> Accept/Reject invites based on test parameters.
 * <tr><td> Adapt to assigned roles.
 * <tr><td> Perform test case actions.
 * <tr><td> Generate test reports.
 * </table>
 */
public interface InteropClientTestCase extends MessageListener
{
    /** Defines the possible test case roles that an interop test case can take on. */
    public enum Roles
    {
        SENDER, RECEIVER;
    }

    /**
     * Should provide the name of the test case that this class implements. The exact names are defined in the
     * interop testing spec.
     *
     * @return The name of the test case that this implements.
     */
    public String getName();

    /**
     * Determines whether the test invite that matched this test case is acceptable.
     *
     * @param inviteMessage The invitation to accept or reject.
     *
     * @return <tt>true</tt> to accept the invitation, <tt>false</tt> to reject it.
     *
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public boolean acceptInvite(Message inviteMessage) throws JMSException;

    /**
     * Assigns the role to be played by this test case. The test parameters are fully specified in the
     * assignment message. When this method return the test case will be ready to execute.
     *
     * @param role              The role to be played; sender or receiver.
     * @param assignRoleMessage The role assingment message, contains the full test parameters.
     *
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public void assignRole(Roles role, Message assignRoleMessage) throws JMSException;

    /**
     * Performs the test case actions.
     * return from here when you have finished the test.. this will signal the controller that the test has ended. 
     * @throws JMSException Any JMSException resulting from reading the message are allowed to fall through.
     */
    public void start() throws JMSException;

    /**
     * Gives notice of termination of the test case actions.
     *
     * @throws JMSException Any JMSException resulting from allowed to fall through.
     */
    public void terminate() throws JMSException, InterruptedException;

    /**
     * Gets a report on the actions performed by the test case in its assigned role.
     *
     * @param session The session to create the report message in.
     *
     * @return The report message.
     *
     * @throws JMSException Any JMSExceptions resulting from creating the report are allowed to fall through.
     */
    public Message getReport(Session session) throws JMSException;
}
