package org.apache.qpid.test.unit.close;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.qpid.test.utils.QpidBrokerTestCase;

public class MessageConsumerCloseTest  extends QpidBrokerTestCase
{
    Exception _exception;

    public void testConsumerCloseAndSessionRollback() throws Exception
    {
        Connection connection = getConnection();
        final CountDownLatch receiveLatch = new CountDownLatch(1);
        final Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
        Destination destination = getTestQueue();
        MessageConsumer consumer = session.createConsumer(destination);
        sendMessage(session, destination, 2);
        connection.start();
        consumer.setMessageListener(new MessageListener()
        {
            @Override
            public void onMessage(Message message)
            {
                try
                {
                    receiveLatch.countDown();
                    session.rollback();
                }
                catch (JMSException e)
                {
                    _exception = e;
                }
            }
        });
        boolean messageReceived = receiveLatch.await(1l, TimeUnit.SECONDS);
        consumer.close();

        assertNull("Exception occured on rollback:" + _exception, _exception);
        assertTrue("Message is not received", messageReceived);

        consumer = session.createConsumer(destination);
        Message message1 = consumer.receive(1000l);
        assertNotNull("message1 is not received", message1);
        Message message2 = consumer.receive(1000l);
        assertNotNull("message2 is not received", message2);
    }
}
