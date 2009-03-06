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
package org.apache.qpid.test.client;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.server.queue.AMQQueueFactory;

import javax.jms.JMSException;
import javax.jms.Connection;
import javax.jms.Session;
import java.util.Map;
import java.util.HashMap;

public class QueueBrowsingFlowToDiskTest extends QueueBrowserAutoAckTest
{
    @Override
    protected void sendMessages(Connection producerConnection, int messageSendCount) throws JMSException
    {
        try
        {
            setupFlowToDisk(producerConnection, messageSendCount , this.getName());
        }
        catch (AMQException e)
        {
            fail("Unable to setup Flow to disk:"+e.getMessage());
        }

        super.sendMessages(producerConnection,messageSendCount);
    }

    private void setupFlowToDisk(Connection producerConnection, int messages, String name)
            throws AMQException, JMSException
    {
        Map<String, Object> arguments = new HashMap<String, Object>();

        //Ensure we can call createQueue with a priority int value
        arguments.put(AMQQueueFactory.QPID_POLICY_TYPE.toString(), AMQQueueFactory.QPID_FLOW_TO_DISK);
        // each message in the QBAAT is around 9-10 bytes each so only give space for half
        arguments.put(AMQQueueFactory.QPID_MAX_SIZE.toString(), 5 * messages);

        //Create the FlowToDisk Queue
        ((AMQSession) producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE)).
                createQueue(new AMQShortString(name), false, false, false, arguments);

        // Get a JMS reference to the new queue
        _queue = _clientSession.createQueue(name);
    }

}
