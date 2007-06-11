package org.apache.qpid.test.unit.message;

import junit.framework.TestCase;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.framing.AMQShortString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * @author Apache Software Foundation
 */
public class JMSDestinationTest extends TestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSDestinationTest.class);

    public String _connectionString = "vm://:1";

    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        super.tearDown();
        TransportConnection.killAllVMBrokers();
    }

    public void testJMSDestination() throws Exception
    {
        AMQConnection con = new AMQConnection("vm://:1", "guest", "guest", "consumer1", "test");
        AMQSession consumerSession = (AMQSession) con.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        Queue queue =
            new AMQQueue(con.getDefaultQueueExchangeName(), new AMQShortString("someQ"), new AMQShortString("someQ"), false,
                true);
        MessageConsumer consumer = consumerSession.createConsumer(queue);

        Connection con2 = new AMQConnection("vm://:1", "guest", "guest", "producer1", "test");
        Session producerSession = con2.createSession(false, Session.CLIENT_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(queue);

        TextMessage sentMsg = producerSession.createTextMessage("hello");
        assertNull(sentMsg.getJMSDestination());

        producer.send(sentMsg);

        assertEquals(sentMsg.getJMSDestination(), queue);

        con2.close();

        con.start();

        TextMessage rm = (TextMessage) consumer.receive();
        assertNotNull(rm);

        assertEquals(rm.getJMSDestination(), queue);
        con.close();
    }

}
