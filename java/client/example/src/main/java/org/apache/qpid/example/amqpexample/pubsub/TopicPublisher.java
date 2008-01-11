package org.apache.qpid.example.amqpexample.pubsub;

import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.transport.DeliveryProperties;

public class TopicPublisher
{
    public void publishMessages(Session session, String routing_key)
    {
      // Set the routing key once, we'll use the same routing key for all
      // messages.

      DeliveryProperties deliveryProps =  new DeliveryProperties();
      deliveryProps.setRoutingKey(routing_key);

      for (int i=0; i<5; i++) {
        session.messageTransfer("amq.topic", Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        session.header(deliveryProps);
        session.data("Message " + i);
        session.endData();
      }

    }

    public void noMoreMessages(Session session)
    {
        session.messageTransfer("amq.topic", Session.TRANSFER_CONFIRM_MODE_REQUIRED, Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        session.header(new DeliveryProperties().setRoutingKey("control"));
        session.data("That's all, folks!");
        session.endData();
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
        TopicPublisher publisher = new TopicPublisher();

        publisher.publishMessages(session, "usa.news");
        publisher.publishMessages(session, "usa.weather");
        publisher.publishMessages(session, "europe.news");
        publisher.publishMessages(session, "europe.weather");

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
