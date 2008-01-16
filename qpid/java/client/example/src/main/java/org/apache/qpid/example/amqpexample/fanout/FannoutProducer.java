package org.apache.qpid.example.amqpexample.fanout;

import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.transport.DeliveryProperties;

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
            session.messageTransfer("amq.fanout", Session.TRANSFER_CONFIRM_MODE_REQUIRED, Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
            session.header(deliveryProps);
            session.data("Message " + i);
            session.endData();
        }

        session.messageTransfer("amq.fanout", Session.TRANSFER_CONFIRM_MODE_REQUIRED, Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        session.header(deliveryProps);
        session.data("That's all, folks!");
        session.endData();

        // confirm completion
        session.sync();

        //cleanup
        session.sessionClose();
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
