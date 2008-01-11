package org.apache.qpid.example.amqpexample.fannout;

import java.nio.ByteBuffer;

import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.MessageListener;
import org.apache.qpidity.nclient.util.MessagePartListenerAdapter;

/**
 * This listens to messages on a queue and terminates
 * when it sees the final message
 *
 */
public class Listener implements MessageListener
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

    /**
     *  This sends 10 messages to the
     *  amq.direct exchange using the
     *  routing key as "routing_key"
     *
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

        // Create an instance of the listener
        Listener listener = new Listener();

        // create a subscription
        session.messageSubscribe("message_queue",
                                 "listener_destination",
                                 Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                 Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE,
                                 new MessagePartListenerAdapter(listener), null);


        // issue credits
        session.messageFlow("listener_destination", Session.MESSAGE_FLOW_UNIT_BYTE, Session.MESSAGE_FLOW_MAX_BYTES);
        session.messageFlow("listener_destination", Session.MESSAGE_FLOW_UNIT_MESSAGE, 11);

        // confirm completion
        session.sync();

        // check to see if we have received all the messages
        while (!listener.isFinished()){}
        System.out.println("Shutting down listener for listener_destination");
        session.messageCancel("listener_destination");

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
