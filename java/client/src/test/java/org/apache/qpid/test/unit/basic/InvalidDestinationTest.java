package org.apache.qpid.test.unit.basic;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.client.transport.TransportConnection;

import junit.framework.TestCase;

import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.QueueSession;
import javax.jms.Queue;
import javax.jms.QueueSender;
import javax.jms.TextMessage;
import javax.jms.InvalidDestinationException;

public class InvalidDestinationTest extends TestCase
{
    private AMQConnection _connection;
    private AMQDestination _destination;
    private AMQSession _session;
    private MessageConsumer _consumer;

    private static final String VM_BROKER = "vm://:1";


    protected void setUp() throws Exception
    {
        super.setUp();
        createVMBroker();
        _connection = new AMQConnection(VM_BROKER, "guest", "guest", "ReceiveTestClient", "test");
    }

    public void createVMBroker()
    {
        try
        {
            TransportConnection.createVMBroker(1);
        }
        catch (AMQVMBrokerCreationException e)
        {
            fail("Unable to create broker: " + e);
        }
    }

    protected void tearDown() throws Exception
    {
        _connection.close();
        TransportConnection.killVMBroker(1);
        super.tearDown();
    }



    public void testInvalidDestination() throws Exception
    {
        Queue invalidDestination = new AMQQueue("amq.direct","unknownQ");
        AMQQueue validDestination = new AMQQueue("amq.direct","knownQ");
        QueueSession queueSession = _connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

        // This is the only easy way to create and bind a queue from the API :-(
        MessageConsumer consumer = queueSession.createConsumer(validDestination);

        QueueSender sender = queueSession.createSender(invalidDestination);
        TextMessage msg = queueSession.createTextMessage("Hello");
        try
        {
            sender.send(msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }
        sender.close();

        sender = queueSession.createSender(null);
        invalidDestination = new AMQQueue("amq.direct","unknownQ");

        try
        {
            sender.send(invalidDestination,msg);
            fail("Expected InvalidDestinationException");
        }
        catch (InvalidDestinationException ex)
        {
            // pass
        }
        sender.send(validDestination,msg);
        sender.close();
        validDestination = new AMQQueue("amq.direct","knownQ");
        sender = queueSession.createSender(validDestination);
        sender.send(msg);




    }


    public static junit.framework.Test suite()
    {

        return new junit.framework.TestSuite(InvalidDestinationTest.class);
    }
}
