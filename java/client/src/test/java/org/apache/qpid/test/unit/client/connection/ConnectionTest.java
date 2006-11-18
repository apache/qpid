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
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQUnresolvedAddressException;
import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import org.junit.Ignore;

import javax.jms.Connection;

import junit.framework.JUnit4TestAdapter;

public class ConnectionTest
{

    String _broker = "vm://:1";
    String _broker_NotRunning = "vm://:2";
    String _broker_BadDNS = "tcp://hg3sgaaw4lgihjs";

    @Before
    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            Assert.fail("Unable to create broker: " + e);
        }
    }

    @After
    public void stopVmBroker()
    {
        TransportConnection.killVMBroker(1);
    }

    @Test
    public void simpleConnection()
    {
        try
        {
            Connection connection = new AMQConnection(_broker, "guest", "guest",
                                                      "fred", "/test");
        }
        catch (Exception e)
        {
            Assert.fail("Connection to " + _broker + " should succeed. Reason: " + e);
        }
    }

    @Ignore("The inVM broker currently has no authentication .. Needs added QPID-70")
    @Test
    public void passwordFailureConnection() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:rubbishpassword@clientid/testpath?brokerlist='" + _broker + "?retries='1''");
            Assert.fail("Connection should not be established password is wrong.");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQAuthenticationException))
            {
                Assert.fail("Correct exception not thrown. Excpected 'AMQAuthenticationException' got: " + amqe);
            }
        }
    }

    @Test
    public void connectionFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='" + _broker_NotRunning + "?retries='0''");
            Assert.fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQConnectionException))
            {
                Assert.fail("Correct exception not thrown. Excpected 'AMQConnectionException' got: " + amqe);
            }
        }
    }

    @Test
    public void unresolvedHostFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='" + _broker_BadDNS + "?retries='0''");
            Assert.fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQUnresolvedAddressException))
            {
                Assert.fail("Correct exception not thrown. Excpected 'AMQUnresolvedAddressException' got: " + amqe);
            }
        }
    }

    /**
     * For Junit 3 compatibility.
     */
    public static junit.framework.Test suite()
    {
        return new JUnit4TestAdapter(ConnectionTest.class);
    }

}
