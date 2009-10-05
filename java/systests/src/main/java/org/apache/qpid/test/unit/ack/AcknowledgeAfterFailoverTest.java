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

import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

/**
 *
 */
public class AcknowledgeAfterFailoverTest extends AcknowledgeTest
{

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        // This must be even for the test to run correctly.
        // Otherwise we will kill the standby broker
        // not the one we are connected to.
        // The test will still pass but it will not be exactly
        // as described.
        NUM_MESSAGES = 10;
    }

    protected void prepBroker(int count) throws Exception
    {
        if (count % 2 == 1)
        {
            failBroker(getFailingPort());
        }
        else
        {
            failBroker(getPort());
        }

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

        try
        {
            if (count % 2 == 1)
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

    @Override
    public void doAcknowlegement(Message msg) throws JMSException
    {
        //Acknowledge current message
        super.doAcknowlegement(msg);

        try
        {
            prepBroker(NUM_MESSAGES - msg.getIntProperty(INDEX) - 1);
        }
        catch (Exception e)
        {
            fail("Unable to prep new broker," + e.getMessage());
        }

    }

    /**
     * @param transacted
     * @param mode
     *
     * @throws Exception
     */
    protected void testDirtyAcking(boolean transacted, int mode) throws Exception
    {
        NUM_MESSAGES = 2;
        //Test Dirty Failover Fails
        init(transacted, mode);

        _connection.start();

        Message msg = _consumer.receive(1500);

        int count = 0;
        assertNotNull("Message " + count + " not correctly received.", msg);
        assertEquals("Incorrect message received", count, msg.getIntProperty(INDEX));
        count++;

        //Don't acknowledge just prep the next broker

        try
        {
            prepBroker(count);
        }
        catch (Exception e)
        {
            fail("Unable to prep new broker," + e.getMessage());
        }

        // Consume the next message
        msg = _consumer.receive(1500);
        assertNotNull("Message " + count + " not correctly received.", msg);
        assertEquals("Incorrect message received", count, msg.getIntProperty(INDEX));

        if (_consumerSession.getTransacted())
        {
            _consumerSession.commit();
        }
        else
        {
            try
            {
                msg.acknowledge();
                fail("Session is dirty we should get an IllegalStateException");
            }
            catch (IllegalStateException ise)
            {
                assertEquals("Incorrect Exception thrown", "has failed over", ise.getMessage());
            }
        }

        assertEquals("Wrong number of messages on queue", 0,
                     ((AMQSession) _consumerSession).getQueueDepth((AMQDestination) _queue));
    }

    public void testDirtyClientAck() throws Exception
    {
        testDirtyAcking(false, Session.CLIENT_ACKNOWLEDGE);
    }

    public void testDirtyTransacted() throws Exception
    {
        testDirtyAcking(true, Session.SESSION_TRANSACTED);
    }

}
