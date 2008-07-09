package org.apache.qpid.example.amqpexample.pubsub;

import java.nio.ByteBuffer;

import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.MessageListener;
import org.apache.qpidity.nclient.util.MessagePartListenerAdapter;
import org.apache.qpidity.transport.MessageCreditUnit;
import org.apache.qpidity.transport.Option;


public class TopicListener implements MessageListener
{
    boolean finish = false;
    int count = 0;

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

        System.out.println("Message: " + data + " with routing_key " + m.getDeliveryProperties().getRoutingKey());

        if (data != null && data.equals("That's all, folks!"))
        {
            count++;
            if (count == 4){
                finish = true;
            }
        }
    }

    public void prepareQueue(Session session,String queueName,String bindingKey)
    {
        session.queueDeclare(queueName, null, null, Option.EXCLUSIVE, Option.AUTO_DELETE);
        session.exchangeBind(queueName, "amq.topic", bindingKey, null);
        session.exchangeBind(queueName, "amq.topic", "control", null);

        session.messageSubscribe(queueName,queueName,
                                 Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                 Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE,
                                 new MessagePartListenerAdapter(this),
                                 null, Option.NONE);
        // issue credits
        // XXX: need to be able to set to null
        session.messageFlow(queueName, MessageCreditUnit.BYTE, Session.MESSAGE_FLOW_MAX_BYTES);
        session.messageFlow(queueName, MessageCreditUnit.MESSAGE, 24);
    }

    public void cancelSubscription(Session session,String dest)
    {
        session.messageCancel(dest);
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

        // Create an instance of the listener
        TopicListener listener = new TopicListener();

        listener.prepareQueue(session,"usa", "usa.#");
        listener.prepareQueue(session,"europe", "europe.#");
        listener.prepareQueue(session,"news", "#.news");
        listener.prepareQueue(session,"weather", "#.weather");

        // confirm completion
        session.sync();
        // check to see if we have received all the messages
        while (!listener.isFinished()){}
        System.out.println("Shutting down listener for listener_destination");
        listener.cancelSubscription(session,"usa");
        listener.cancelSubscription(session,"europe");
        listener.cancelSubscription(session,"news");
        listener.cancelSubscription(session,"weather");

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
