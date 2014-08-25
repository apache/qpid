/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */
package org.apache.qpid.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Session;


/**
 * Class to check that session creation on a connection has no accidental limit
 */
public class SessionCreateTest extends QpidBrokerTestCase
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionCreateTest.class);

    private Connection _clientConnection;
    protected int maxSessions = 65555;

    public void testSessionCreationLimit() throws Exception
    {
        // Create Client
        _clientConnection = getConnection("guest", "guest");

        _clientConnection.start();

        for (int i=0; i < maxSessions; i++)
        {
            Session sess = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            assertNotNull(sess);
            sess.close();
            LOGGER.debug("created session: " + i);
        }

        _clientConnection.close();

    }

}