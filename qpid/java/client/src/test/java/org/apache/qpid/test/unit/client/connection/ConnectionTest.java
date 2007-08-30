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

import org.apache.qpid.AMQConnectionFailureException;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUnresolvedAddressException;
import org.apache.qpid.client.AMQAuthenticationException;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.jms.Session;

import junit.framework.TestCase;

import javax.jms.Connection;
import javax.jms.QueueSession;
import javax.jms.TopicSession;

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
            AMQConnection conn = new AMQConnection(_broker, "guest", "guest", "fred", "test");
            conn.close();
        }
        catch (Exception e)
        {
            fail("Connection to " + _broker + " should succeed. Reason: " + e);
        }
    }


    public void testDefaultExchanges()
    {
        try
        {
            AMQConnection conn = new AMQConnection("amqp://guest:guest@clientid/test?brokerlist='"
                                                   + _broker
                                                   + "?retries='1''&defaultQueueExchange='test.direct'"
                                                   + "&defaultTopicExchange='test.topic'"
                                                   + "&temporaryQueueExchange='tmp.direct'"
                                                   + "&temporaryTopicExchange='tmp.topic'");

            QueueSession queueSession = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

            AMQQueue queue = (AMQQueue) queueSession.createQueue("MyQueue");

            assertEquals(queue.getExchangeName().toString(), "test.direct");

            AMQQueue tempQueue = (AMQQueue) queueSession.createTemporaryQueue();

            assertEquals(tempQueue.getExchangeName().toString(), "tmp.direct");


            queueSession.close();


            TopicSession topicSession = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

            AMQTopic topic = (AMQTopic) topicSession.createTopic("silly.topic");

            assertEquals(topic.getExchangeName().toString(), "test.topic");

            AMQTopic tempTopic = (AMQTopic) topicSession.createTemporaryTopic();

            assertEquals(tempTopic.getExchangeName().toString(), "tmp.topic");

            topicSession.close();


            conn.close();
        }
        catch (Exception e)
        {
            fail("Connection to " + _broker + " should succeed. Reason: " + e);
        }
    }

     //fixme AMQAuthenticationException is not propogaged
    public void PasswordFailureConnection() throws Exception
    {
        try
        {
            new AMQConnection("amqp://guest:rubbishpassword@clientid/test?brokerlist='" + _broker + "?retries='1''");
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
