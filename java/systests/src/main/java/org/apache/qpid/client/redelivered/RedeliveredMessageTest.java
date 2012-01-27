package org.apache.qpid.client.redelivered;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

public class RedeliveredMessageTest extends QpidBrokerTestCase
{
    private Connection _connection;

    public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection();
    }

    public void testRedeliveredFlagOnSessionClose() throws Exception
    {
        Session session = _connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Destination destination = session.createQueue(getTestQueueName());
        MessageConsumer consumer = session.createConsumer(destination);

        final int numberOfMessages = 3;
        sendMessage(session, destination, numberOfMessages);

        _connection.start();

        for(int i = 0; i < numberOfMessages; i++)
        {
            final Message m = consumer.receive(1000l);
            assertNotNull("Message is not recieved at " + i, m);
            assertFalse("Redelivered should be not set", m.getJMSRedelivered());
        }

        session.close();
        session = _connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        destination = session.createQueue(getTestQueueName());
        consumer = session.createConsumer(destination);

        for(int i = 0; i < numberOfMessages; i++)
        {
            final Message m = consumer.receive(1000l);
            assertNotNull("Message is not recieved at " + i, m);
            assertTrue("Redelivered should be set", m.getJMSRedelivered());
        }
    }
}
