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
package org.apache.qpid.test.unit.basic;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.testutil.VMBrokerSetup;

public class SessionStartTest extends TestCase implements MessageListener
{
    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private int count;
    public String _connectionString = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        init(new AMQConnection(_connectionString, "guest", "guest", randomize("Client"), "test"));
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    private void init(AMQConnection connection) throws Exception
    {
        init(connection, new AMQQueue(randomize("SessionStartTest"), true));
    }

    private void init(AMQConnection connection, AMQDestination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        connection.start();

        _session = (AMQSession) connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        _session.createConsumer(destination).setMessageListener(this);
    }

    public synchronized void test() throws JMSException, InterruptedException
    {
        try
        {
            _session.createProducer(_destination).send(_session.createTextMessage("Message"));
            System.out.println("Message sent, waiting for response...");
            wait(1000);
            if (count > 0)
            {
                System.out.println("Got message");
            }
            else
            {
                throw new RuntimeException("Did not get message!");
            }
        }
        finally
        {
            _session.close();
            _connection.close();
        }
    }

    public synchronized void onMessage(Message message)
    {
        count++;
        notify();
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        SessionStartTest test = new SessionStartTest();
        test._connectionString = argv.length == 0 ? "localhost:5672" : argv[0];
        test.setUp();
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new VMBrokerSetup(new junit.framework.TestSuite(SessionStartTest.class));
    }
}
