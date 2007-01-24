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
package org.apache.qpid.test.unit.client.forwardall;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;

import javax.jms.Message;
import javax.jms.MessageListener;

/**
 * Declare a private temporary response queue,
 * send a message to amq.direct with a well known routing key with the
 * private response queue as the reply-to destination
 * consume responses.
 */
public class Client implements MessageListener
{
    private final AMQConnection _connection;
    private final AMQSession _session;
    private final int _expected;
    private int _count;

    Client(String broker, int expected) throws Exception
    {
        this(connect(broker), expected);
    }

    Client(AMQConnection connection, int expected) throws Exception
    {
        _connection = connection;
        _expected = expected;
        _session = (AMQSession) _connection.createSession(false, AMQSession.NO_ACKNOWLEDGE);
        AMQQueue response = new AMQQueue("ResponseQueue", true);
        _session.createConsumer(response).setMessageListener(this);
        _connection.start();
        AMQQueue service = new SpecialQueue("ServiceQueue");
        Message request = _session.createTextMessage("Request!");
        request.setJMSReplyTo(response);
        _session.createProducer(service).send(request);
    }

    void shutdownWhenComplete() throws Exception
    {
        waitUntilComplete();
        _connection.close();
    }

    public void onMessage(Message response)
    {
        System.out.println("Received " + (++_count) + " of " + _expected  + " responses.");
        if(_count == _expected)
        {
            synchronized(this)
            {
                notifyAll();
            }
        }
    }

    synchronized void waitUntilComplete() throws InterruptedException
    {
        while(_count < _expected)
        {
            wait();
        }
    }

    static AMQConnection connect(String broker) throws Exception
    {
        return new AMQConnection(broker, "guest", "guest", "Client" + System.currentTimeMillis(), "test");
    }

    public static void main(String[] argv) throws Exception
    {
        final String connectionString;
        final int expected;
        if (argv.length == 0) {
            connectionString = "localhost:5672";
            expected = 100;
        }
        else
        {
            connectionString = argv[0];
            expected = Integer.parseInt(argv[1]);
        }

        new Client(connect(connectionString), expected).shutdownWhenComplete();
    }
}
