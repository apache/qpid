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
package org.apache.qpid.server.queue;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.AMQException;

import javax.jms.Session;
import javax.jms.Message;
import javax.jms.ConnectionFactory;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;

public class QueueDepthWithSelectorUsingFlowToDiskTest extends QueueDepthWithSelectorTest
{

    @Override
    public void init() throws NamingException, JMSException, AMQException
    {
        //Incresae the number of messages to send
        MSG_COUNT = 100;

        //Resize the array
        _messages = new Message[MSG_COUNT];


        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        // each message in the QueueDepthWithSelectorTest is 17 bytes each so only give space for half
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 8 * MSG_COUNT);

        //Create the FlowToDisk Queue
        Connection connection = ((ConnectionFactory) _context.lookup("connection")).createConnection();

        AMQSession session = ((AMQSession)connection.createSession(false, Session.AUTO_ACKNOWLEDGE));
        session.createQueue(new AMQShortString(getName()), false, false, false, arguments);

        // Get a JMS reference to the new queue
        _queue = session.createQueue(getName());
        connection.close();

        super.init();
    }

    public void testOnlyGettingHalf() throws Exception
    {
        //Send messages
        _logger.info("Starting to send messages");
        for (int msg = 0; msg < MSG_COUNT; msg++)
        {
            //Send a message that matches the selector
            _producer.send(nextMessage(msg));

            //Send one that doesn't
            _producer.send(_producerSession.createTextMessage("MessageReturnTest"));
        }

        
        _logger.info("Closing connection");
        //Close the connection.. .giving the broker time to clean up its state.
        _producerConnection.close();

        //Verify we get all the messages.
        _logger.info("Verifying messages");
        // Expecting there to be MSG_COUNT on the queue as we have sent
        // MSG_COUNT * (one that matches selector and one that doesn't)
        verifyAllMessagesRecevied(MSG_COUNT);

        //Close the connection.. .giving the broker time to clean up its state.
        _clientConnection.close();

        //Verify Broker state
        _logger.info("Verifying broker state");
        verifyBrokerState(MSG_COUNT);
    }





}
