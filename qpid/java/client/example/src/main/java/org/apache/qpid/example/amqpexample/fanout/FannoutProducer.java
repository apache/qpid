package org.apache.qpid.example.amqpexample.fanout;

import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.Session;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;

public class FannoutProducer
{
    /**
     *  This sends 10 messages to the
     *  amq.fannout exchange
     */
    public static void main(String[] args)
    {
        // Create connection
        Connection con = Client.createConnection();
        try
        {
            con.connect("localhost", 5672, "test", "guest", "guest");
        }
        catch(Exception e)
        {
            System.out.print("Error connecting to broker");
            e.printStackTrace();
        }

        // Create session
        Session session = con.createSession(0);
        DeliveryProperties deliveryProps = new DeliveryProperties();
        deliveryProps.setRoutingKey("routing_key");

        for (int i=0; i<10; i++)
        {
            session.messageTransfer("amq.fanout", MessageAcceptMode.EXPLICIT, MessageAcquireMode.PRE_ACQUIRED,
                                    new Header(deliveryProps), "Message " + i);
        }

        session.messageTransfer("amq.fanout", MessageAcceptMode.EXPLICIT, MessageAcquireMode.PRE_ACQUIRED,
                                new Header(deliveryProps),
                                "That's all, folks!");

        // confirm completion
        session.sync();

        //cleanup
        session.sessionDetach(session.getName());
        try
        {
            con.close();
        }
        catch(Exception e)
        {
            System.out.print("Error closing broker connection");
            e.printStackTrace();
        }
    }

}
