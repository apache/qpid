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

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.message.JMSTextMessage;
import org.apache.qpid.test.VMBrokerSetup;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.jms.*;

import junit.framework.TestCase;
import junit.framework.Assert;

public class FieldTableInTextMessageTest extends TestCase implements MessageListener
{
    private final static Logger _logger = org.apache.log4j.Logger.getLogger(FieldTableInTextMessageTest.class);

    private AMQConnection _connection;
    private Destination _destination;
    private AMQSession _session;
    private Message original_message = null;
    private Message received_message = null;
    public String _connectionString = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        try
        {
            init(new AMQConnection(_connectionString, "guest", "guest", randomize("Client"), "/test_path"));
        }
        catch (Exception e)
        {
            fail("Unable to initialilse connection: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    private void init(AMQConnection connection) throws Exception
    {
        Destination destination = new AMQQueue(randomize("TextMessageTest"), true);
        init(connection, destination);
    }

    private void init(AMQConnection connection, Destination destination) throws Exception
    {
        _connection = connection;
        _destination = destination;
        _session = (AMQSession) connection.createSession(false, AMQSession.AUTO_ACKNOWLEDGE);

        //set up a slow consumer
        _session.createConsumer(destination).setMessageListener(this);
        connection.start();
    }

    public void test() throws Exception
    {
        send();
        waitFor();
        check();
        System.out.println("Completed without failure");
        _connection.close();
    }

    void send() throws JMSException
    {
        //create a publisher
        MessageProducer producer = _session.createProducer(_destination);
        Message message = _session.createTextMessage("Message Body");
        message.setBooleanProperty("boolan", true);
        message.setByteProperty("byte", Byte.MAX_VALUE);
        message.setDoubleProperty("double", Double.MAX_VALUE);
        message.setFloatProperty("float", Float.MAX_VALUE);
        message.setIntProperty("int", Integer.MAX_VALUE);
        message.setLongProperty("long", Long.MAX_VALUE);
        message.setShortProperty("short", Short.MAX_VALUE);
        message.setStringProperty("String", "String");


        original_message = message;
        _logger.info("Sending Message:" + message);
        producer.send(message);

    }

    void waitFor() throws InterruptedException
    {
        synchronized(received_message)
        {
            received_message.wait();
        }
    }

    void check() throws JMSException
    {
        _logger.info("Received Message:" + received_message);
        assertEqual(original_message, received_message);
    }

    private static void assertEqual(Message expected, Message actual)
    {
        _logger.info("Expected:" + expected);
        _logger.info("Actual:" + actual);
    }
   
    public void onMessage(Message message)
    {
        synchronized(received_message)
        {
            received_message = message;
            received_message.notify();
        }
    }

    private static String randomize(String in)
    {
        return in + System.currentTimeMillis();
    }

    public static void main(String[] argv) throws Exception
    {
        FieldTableInTextMessageTest test = new FieldTableInTextMessageTest();
        test._connectionString = argv.length == 0 ? "vm://:1" : argv[0];
        test.setUp();
        test.test();
    }

    public static junit.framework.Test suite()
    {
        return new VMBrokerSetup(new junit.framework.TestSuite(FieldTableInTextMessageTest.class));
    }
}
