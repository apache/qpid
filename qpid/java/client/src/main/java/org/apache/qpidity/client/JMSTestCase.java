package org.apache.qpidity.client;

import org.apache.qpidity.jms.ConnectionFactoryImpl;
import org.apache.qpidity.jms.TopicImpl;

public class JMSTestCase
{
    public static void main(String[] args)
    {
        try
        {
            javax.jms.Connection con = (new ConnectionFactoryImpl("localhost",5672, "test", "guest","guest")).createConnection();
            con.start();
            
            javax.jms.Session ssn = con.createSession(false, 1);
            
            javax.jms.Destination dest = new TopicImpl("myTopic");
            javax.jms.MessageProducer prod = ssn.createProducer(dest);
            javax.jms.MessageConsumer cons = ssn.createConsumer(dest); 
            
            javax.jms.BytesMessage msg = ssn.createBytesMessage();
            msg.writeInt(123);
            prod.send(msg);
            
            javax.jms.Message m = cons.receive();
            System.out.println(m);
            
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }
}
