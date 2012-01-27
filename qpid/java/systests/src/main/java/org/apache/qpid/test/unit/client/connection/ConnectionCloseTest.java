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
package org.apache.qpid.test.unit.client.connection;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * ConnectionCloseTest
 *
 */

public class ConnectionCloseTest extends QpidBrokerTestCase
{

    /**
     * This test is added due to QPID-3453 to test connection closing when AMQ
     * session is not closed but underlying transport session is in detached
     * state and transport connection is closed
     */
    public void testConnectionCloseOnOnForcibleBrokerStop() throws Exception
    {
        Connection connection = getConnection();
        connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        stopBroker();

        // we need to close connection explicitly in order to verify that
        // closing of connection having transport session in DETACHED state and
        // transport connection in CLOSED state does not throw an exception
        try
        {
            connection.close();
        }
        catch (JMSException e)
        {
            // session closing should not fail
            fail("Cannot close connection:" + e.getMessage());
        }
    }

}
