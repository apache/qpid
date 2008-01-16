package org.apache.qpid.example.amqpexample.direct;

import java.nio.ByteBuffer;

import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.MessageListener;
import org.apache.qpidity.transport.DeliveryProperties;

public class DirectProducer implements MessageListener
{
    boolean finish = false;

    public void onMessage(Message m)
    {
        String data = null;

        try
        {
            ByteBuffer buf = m.readData();
            byte[] b = new byte[buf.remaining()];
            buf.get(b);
            data = new String(b);
        }
        catch(Exception e)
        {
            System.out.print("Error reading message");
            e.printStackTrace();
        }

        System.out.println("Message: " + data);


        if (data != null && data.equals("That's all, folks!"))
        {
            finish = true;
        }
    }

    public boolean isFinished()
    {
        return finish;
    }

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
            session.messageTransfer("amq.direct", Session.TRANSFER_CONFIRM_MODE_REQUIRED, Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
            session.header(deliveryProps);
            session.data("Message " + i);
            session.endData();
        }

        session.messageTransfer("amq.direct", Session.TRANSFER_CONFIRM_MODE_REQUIRED, Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
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
