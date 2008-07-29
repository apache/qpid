package org.apache.qpid.example.amqpexample.direct;

import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.Session;

/**
 *  This creates a queue a queue and binds it to the
 *  amq.direct exchange
 *
 */
public class DeclareQueue
{

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

        // declare and bind queue
        session.queueDeclare("message_queue", null, null);
        session.exchangeBind("message_queue", "amq.direct", "routing_key", null);

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
