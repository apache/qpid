package org.apache.qpid.test.client.message;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.apache.qpid.test.utils.QpidTestCase;

public class SelectorTest extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(SelectorTest.class);

    public void testSelectorWithJMSMessageID() throws Exception
    {
        Connection conn = getConnection();
        conn.start();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

        Destination dest = session.createQueue("SelectorQueue");
        
        MessageProducer prod = session.createProducer(dest);
        MessageConsumer consumer = session.createConsumer(dest,"JMSMessageID IS NOT NULL");
        
        for (int i=0; i<2; i++)
        {
            Message msg = session.createTextMessage("Msg" + String.valueOf(i));
            prod.send(msg);
        }
        
        Message msg1 = consumer.receive(1000);
        Message msg2 = consumer.receive(1000);
        
        Assert.assertNotNull("Msg1 should not be null", msg1);
        Assert.assertNotNull("Msg2 should not be null", msg2);
        
        prod.setDisableMessageID(true);
        
        for (int i=0; i<2; i++)
        {
            Message msg = session.createTextMessage("Msg" + String.valueOf(i));
            prod.send(msg);
        }
        
        Message msg3 = consumer.receive(1000);        
        Assert.assertNull("Msg3 should be null", msg3);
        
        consumer = session.createConsumer(dest,"JMSMessageID IS NULL");
        
        Message msg4 = consumer.receive(1000);
        Message msg5 = consumer.receive(1000);
        
        Assert.assertNotNull("Msg4 should not be null", msg4);
        Assert.assertNotNull("Msg5 should not be null", msg5);
    }
}
