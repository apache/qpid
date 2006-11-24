/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.connection;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQUnresolvedAddressException;
import org.junit.Test;
import org.junit.Assert;

import javax.jms.Connection;

import junit.framework.JUnit4TestAdapter;

public class ConnectionTest
{
    @Test
    public void simpleConnection() throws Exception
    {
        Connection connection = new AMQConnection("localhost:5672", "guest", "guest",
                "fred", "/test");
        System.out.println("connection = " + connection);
    }

    @Test
    public void passwordFailureConnection() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:rubbishpassword@clientid/testpath?brokerlist='tcp://localhost:5672?retries='1''");
            Assert.fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQAuthenticationException))
            {
                Assert.fail("Correct exception not thrown");
            }
        }
    }

    @Test
    public void connectionFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:5673?retries='0''");
            Assert.fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQConnectionException))
            {
                Assert.fail("Correct exception not thrown");
            }
        }
    }

    @Test
    public void unresolvedHostFailure() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:guest@clientid/testpath?brokerlist='tcp://rubbishhost:5672?retries='0''");
            Assert.fail("Connection should not be established");
        }
        catch (AMQException amqe)
        {
            if (!(amqe instanceof AMQUnresolvedAddressException))
            {
                Assert.fail("Correct exception not thrown");
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
