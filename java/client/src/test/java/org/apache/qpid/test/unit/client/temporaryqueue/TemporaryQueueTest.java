package org.apache.qpid.test.unit.client.temporaryqueue;

import junit.framework.TestCase;
import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.AMQException;
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.*;

public class TemporaryQueueTest extends TestCase
{

    String _broker = "vm://:1";


    protected void setUp() throws Exception
    {
        super.setUp();
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
    {
        TransportConnection.killAllVMBrokers();
    }

    protected Connection createConnection() throws AMQException, URLSyntaxException
    {
        return new AMQConnection(_broker, "guest", "guest",
                                                      "fred", "/test");
    }

    public void testTempoaryQueue() throws Exception
    {
        Connection conn = createConnection();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        TemporaryQueue queue = session.createTemporaryQueue();
        assertNotNull(queue);
        MessageProducer producer = session.createProducer(queue);
        MessageConsumer consumer = session.createConsumer(queue);
        conn.start();
        producer.send(session.createTextMessage("hello"));
        TextMessage tm = (TextMessage) consumer.receive(2000);
        assertNotNull(tm);
        assertEquals("hello",tm.getText());

        try
        {
            queue.delete();
            fail("Expected JMSException : should not be able to delete while there are active consumers");
        }
        catch(JMSException je)
        {
            ; //pass
        }

        consumer.close();

        try
        {
            queue.delete();
        }
        catch(JMSException je)
        {
            fail("Unexpected Exception: " + je.getMessage());
        }

        conn.close();
    }


    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TemporaryQueueTest.class);
    }
}
