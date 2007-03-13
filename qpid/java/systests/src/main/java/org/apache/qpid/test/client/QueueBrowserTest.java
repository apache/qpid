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
package org.apache.qpid.test.client;

import org.apache.qpid.test.VMTestCase;
import org.apache.log4j.Logger;

import javax.jms.Queue;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.MessageConsumer;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import javax.jms.JMSException;
import javax.jms.QueueReceiver;
import javax.jms.Message;
import java.util.Enumeration;

public class QueueBrowserTest extends VMTestCase
{
    private static final Logger _logger = Logger.getLogger(QueueBrowserTest.class);

    private static final int MSG_COUNT = 10;

    private Connection _clientConnection;
    private Session _clientSession;
    private Queue _queue;

    public void setUp() throws Exception
    {

        super.setUp();

        _queue = (Queue) _context.lookup("queue");

        //Create Client
        _clientConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        _clientConnection.start();

        _clientSession = _clientConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        //Ensure _queue is created
        _clientSession.createConsumer(_queue).close();

        //Create Producer put some messages on the queue
        Connection producerConnection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        producerConnection.start();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        MessageProducer producer = producerSession.createProducer(_queue);

        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            producer.send(producerSession.createTextMessage("Message " + msg));
        }

        producerConnection.close();

    }

    /*
    * Test Messages Remain on Queue
    * Create a queu and send messages to it. Browse them and then receive them all to verify they were still there
    *
    */

    public void queueBrowserMsgsRemainOnQueueTest() throws JMSException
    {

        // create QueueBrowser
        _logger.info("Creating Queue Browser");

        QueueBrowser queueBrowser = _clientSession.createBrowser(_queue);

        // check for messages
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Checking for " + MSG_COUNT + " messages with QueueBrowser");
        }

        int msgCount = 0;
        Enumeration msgs = queueBrowser.getEnumeration();

        while (msgs.hasMoreElements())
        {
            msgs.nextElement();
            msgCount++;
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Found " + msgCount + " messages total in browser");
        }

        // check to see if all messages found
//        assertEquals("browser did not find all messages", MSG_COUNT, msgCount);
        if (msgCount != MSG_COUNT)
        {
            _logger.warn(msgCount + "/" + MSG_COUNT + " messages received.");
        }

        //Close browser
        queueBrowser.close();

        // VERIFY

        // continue and try to receive all messages
        MessageConsumer consumer = _clientSession.createConsumer(_queue);

        _logger.info("Verify messages are still on the queue");

        Message tempMsg;

        for (msgCount = 0; msgCount < MSG_COUNT; msgCount++)
        {
            tempMsg = (TextMessage) consumer.receive(RECEIVE_TIMEOUT);
            if (tempMsg == null)
            {
                fail("Message " + msgCount + " not retrieved from queue");
            }
        }

        _logger.info("All messages recevied from queue");
    }


}
