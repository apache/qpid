 package org.apache.qpidity.nclient;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQTopic;
import org.apache.qpid.framing.AMQShortString;

public class JMSTestCase
{

    public static void main(String[] args)
    {

        try
        {
            javax.jms.Connection con = new AMQConnection("qpid:password=guest;username=guest;client_id=clientid;virtualhost=test@tcp:127.0.0.1:5672");
            con.start();

            javax.jms.Session ssn = con.createSession(false, 1);

            javax.jms.Destination dest = new AMQQueue(new AMQShortString("direct"),"test");
            javax.jms.MessageConsumer cons = ssn.createConsumer(dest);

            javax.jms.TextMessage m = (javax.jms.TextMessage)cons.receive();

            if (m != null)
            {
               System.out.println("Message"  + m);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /* javax.jms.TextMessage msg = ssn.createTextMessage();
    msg.setText("This is a test message");
    msg.setBooleanProperty("targetMessage", false);
    prod.send(msg);

    msg.setBooleanProperty("targetMessage", true);
    prod.send(msg);

    javax.jms.TextMessage m = (javax.jms.TextMessage)cons.receiveNoWait();

    if (m == null)
    {
       System.out.println("message is null");
    }
    else
    {
       System.out.println("message is not null"  + m);
    }*/
}
