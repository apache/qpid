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
 * Implements tet case 1, dummy run. This test case sends no test messages, it exists to confirm that the test harness
 * is interacting with the coordinator correctly.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Supply the name of the test case that this implements.
 * <tr><td> Accept/Reject invites based on test parameters.
 * <tr><td> Adapt to assigned roles.
 * <tr><td> Perform test case actions.
 * <tr><td> Generate test reports.
 * </table>
 */
public class TestCase1DummyRun implements InteropClientTestCase
{
    public String getName()
    {
        return "TC1_DummyRun";
    }

    public boolean acceptInvite(Message inviteMessage) throws JMSException
    {
        // Test parameters don't matter, accept all invites.
        return true;
    }

    public void assignRole(Roles role, Message assignRoleMessage) throws JMSException
    {
        // Do nothing, both roles are the same.
    }

    public void start()
    {
        // Do nothing.
    }

    public Message getReport(Session session) throws JMSException
    {
        // Generate a dummy report, the coordinator expects a report but doesn't care what it is.
        return session.createTextMessage("Dummy Run, Ok.");
    }

    public void onMessage(Message message)
    {
        // Ignore any messages.
    }
}
