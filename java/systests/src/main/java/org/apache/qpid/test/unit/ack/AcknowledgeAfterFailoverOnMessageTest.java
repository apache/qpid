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
package org.apache.qpid.test.unit.ack;

import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQDestination;

import javax.jms.Connection;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.JMSException;

public class AcknowledgeAfterFailoverOnMessageTest  extends AcknowledgeOnMessageTest
{

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        NUM_MESSAGES = 10;
    }

    protected void prepBroker(int count) throws Exception
    {
        //Stop the connection whilst we repopulate the broker, or the no_ack
        // test will drain the msgs before we can check we put the right number
        // back on again.
//        _connection.stop();

        Connection connection = getConnection();
        Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        // ensure destination is created.
        session.createConsumer(_queue).close();

        sendMessage(session, _queue, count, NUM_MESSAGES - count, 0);

        if (_consumerSession.getAcknowledgeMode() != AMQSession.NO_ACKNOWLEDGE)
        {
            assertEquals("Wrong number of messages on queue", count,
                         ((AMQSession) session).getQueueDepth((AMQDestination) _queue));
        }

        connection.close();

//        _connection.start();
    }

    @Override
    public void doAcknowlegement(Message msg) throws JMSException
    {
        //Acknowledge current message
        super.doAcknowlegement(msg);

        int msgCount = msg.getIntProperty(INDEX);

        if (msgCount % 2 == 0)
        {
            failBroker(getFailingPort());
        }
        else
        {
            failBroker(getPort());
        }

        try
        {
            prepBroker(NUM_MESSAGES - msgCount - 1);
        }
        catch (Exception e)
        {
            fail("Unable to prep new broker," + e.getMessage());
        }

        try
        {

            if (msgCount % 2 == 0)
            {
                startBroker(getFailingPort());
            }
            else
            {
                startBroker(getPort());
            }
        }
        catch (Exception e)
        {
            fail("Unable to start failover broker," + e.getMessage());
        }

    }
}
