package org.apache.qpid.example.jmsexample.direct;

import org.redhat.mrg.messaging.examples.BaseExample;

import javax.jms.*;

/**
 * Message producer example, sends message to a queue.
 */
public class Producer extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "Producer";

    /* The queue name  */
    private String _queueName;

    /**
     * Create a Producer client.
     * @param args Command line arguments.
     */
    public Producer (String[] args)
    {
         super(CLASS, args);
        _queueName = _argProcessor.getStringArgument("-queueName");
    }

    /**
     * Run the message producer example.
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-queueName", "Queue name");
         _defaults.put("-queueName", "message_queue");
        Producer producer = new Producer(args);
        producer.runTest();
    }

    private void runTest()
    {
        try
        {
            // lookup the queue
            Queue   destination = (Queue) getInitialContext().lookup(_queueName);

            // Declare the connection
            Connection connection = getConnection();

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a Message producer
            System.out.println(CLASS + ": Creating a Message PRoducer");
            MessageProducer messageProducer = session.createProducer(destination);

            // Create a Message
            TextMessage message;
            System.out.println(CLASS + ": Creating a TestMessage to send to the destination");
            message = session.createTextMessage();

            // Set a  property for illustrative purposes
            //message.setDoubleProperty("Amount", 10.1);

            // Loop to publish the requested number of messages.
            for (int i = 1; i < getNumberMessages() + 1; i++)
            {
                // NOTE: We have NOT HAD TO START THE CONNECTION TO BEGIN SENDING  messages,
                // this is different to the consumer end as a CONSUMERS CONNECTIONS MUST BE STARTED BEFORE RECEIVING.
                message.setText("Message " + i);
                System.out.println(CLASS + ": Sending message: " + i);
                messageProducer.send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);             
            }

            // And send a final message to indicate termination.
	        message.setText("That's all, folks!");
            messageProducer.send(message, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

            // Close the connection to the broker
            System.out.println(CLASS + ": Closing connection");
            connection.close();

            // Close the JNDI reference
            System.out.println(CLASS + ": Closing JNDI context");
            getInitialContext().close();
        }
        catch (Exception exp)
        {
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }
}
