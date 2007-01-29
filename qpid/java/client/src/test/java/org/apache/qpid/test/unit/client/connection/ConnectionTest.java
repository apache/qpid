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

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQUnresolvedAddressException;
import org.apache.qpid.AMQConnectionFailureException;

import javax.jms.Connection;

import junit.framework.TestCase;

public class ConnectionTest extends TestCase
{

    String _broker = "vm://:1";
    String _broker_NotRunning = "vm://:2";
    String _broker_BadDNS = "tcp://hg3sgaaw4lgihjs";


    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        TransportConnection.killVMBroker(1);
    }

    public void testSimpleConnection()
    {
        try
        {
            AMQConnection conn  = new AMQConnection(_broker, "guest", "guest", "fred", "test");
            conn.close();
        }
        catch (Exception e)
        {
            fail("Connection to " + _broker + " should succeed. Reason: " + e);
        }
    }

    // FIXME The inVM broker currently has no authentication .. Needs added QPID-70
    public void passwordFailureConnection() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:rubbishpassword@clientid/testpath?brokerlist='" + _broker + "?retries='1''");
            fail("Connection should not be established password is wrong.");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQAuthenticationException))
            {
                fail("Correct exception not thrown. Excpected 'AMQAuthenticationException' got: " + amqe);
            }
        }
    }

    public void testConnectionFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='" + _broker_NotRunning + "?retries='0''");
            fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQConnectionFailureException))
            {
                fail("Correct exception not thrown. Excpected 'AMQConnectionException' got: " + amqe);
            }
        }

    }

    public void testUnresolvedHostFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='" + _broker_BadDNS + "?retries='0''");
            fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQUnresolvedAddressException))
            {
                fail("Correct exception not thrown. Excpected 'AMQUnresolvedAddressException' got: " + amqe);
            }
        }
    }

    public void testClientIdCannotBeChanged() throws Exception
    {
        Connection connection = new AMQConnection(_broker, "guest", "guest",
                                                  "fred", "test");
        try
        {
            connection.setClientID("someClientId");
            fail("No IllegalStateException thrown when resetting clientid");
        }
        catch (javax.jms.IllegalStateException e)
        {
            // PASS
        }
    }

    public void testClientIdIsPopulatedAutomatically() throws Exception
    {
        Connection connection = new AMQConnection(_broker, "guest", "guest",
                                                  null, "test");
        assertNotNull(connection.getClientID());
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(ConnectionTest.class);
    }
}
