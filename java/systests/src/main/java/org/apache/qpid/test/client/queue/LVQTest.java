package org.apache.qpid.test.client.queue;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.test.client.destination.AddressBasedDestinationTest;
import org.apache.qpid.test.utils.QpidBrokerTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LVQTest extends QpidBrokerTestCase
{
    private static final Logger _logger = LoggerFactory.getLogger(LVQTest.class);
    private Connection _connection;
    
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _connection = getConnection() ;
        _connection.start();
    }
    
    @Override
    public void tearDown() throws Exception
    {
        _connection.close();
        super.tearDown();
    }
    
    public void testLVQQueue() throws Exception
    {
        String addr = "ADDR:my-lvq-queue; {create: always, " +
        "node: {x-bindings: [{exchange : 'amq.direct', key : test}], " +
        "x-declare:{'qpid.last_value_queue':1}}}";
                
        Session ssn = _connection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        
        Destination dest = ssn.createQueue(addr);
        MessageConsumer consumer = ssn.createConsumer(dest);
        MessageProducer prod = ssn.createProducer(ssn.createQueue("ADDR:amq.direct/test"));
        
        for (int i=0; i<40; i++)
        {
            Message msg = ssn.createTextMessage(String.valueOf(i));
            msg.setStringProperty("qpid.LVQ_key", String.valueOf(i%10));
            prod.send(msg);
        }
         
        for (int i=0; i<10; i++)
        {
            TextMessage msg = (TextMessage)consumer.receive(500);
            assertEquals("The last value is not reflected","3" + i,msg.getText());
        }
        
        assertNull("There should not be anymore messages",consumer.receive(500));
    }
    
}
