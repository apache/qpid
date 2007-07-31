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

import org.apache.log4j.Logger;

import org.apache.qpid.interop.testclient.InteropClientTestCase;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

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
    private static final Logger log = Logger.getLogger(TestCase1DummyRun.class);

    public String getName()
    {
        log.debug("public String getName(): called");

        return "TC1_DummyRun";
    }

    public boolean acceptInvite(Message inviteMessage) throws JMSException
    {
        log.debug("public boolean acceptInvite(Message inviteMessage): called");

        // Test parameters don't matter, accept all invites.
        return true;
    }

    public void assignRole(Roles role, Message assignRoleMessage) throws JMSException
    {
        log.debug("public void assignRole(Roles role, Message assignRoleMessage): called");

        // Do nothing, both roles are the same.
    }

    public void start()
    {
        log.debug("public void start(): called");

        // Do nothing.
    }

    public void terminate() throws JMSException
    {
        //todo
    }

    public Message getReport(Session session) throws JMSException
    {
        log.debug("public Message getReport(Session session): called");

        // Generate a dummy report, the coordinator expects a report but doesn't care what it is.
        return session.createTextMessage("Dummy Run, Ok.");
    }

    public void onMessage(Message message)
    {
        log.debug("public void onMessage(Message message = " + message + "): called");

        // Ignore any messages.
    }
}
