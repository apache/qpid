package org.apache.qpid.example.jmsexample.requestResponse;

import org.apache.qpid.example.jmsexample.BaseExample;

import javax.jms.*;

/**
 * The example creates a MessageConsumer on the specified
 * Destination which is used to synchronously consume messages. If a
 * received message has a ReplyTo header then a new response message is sent
 * to that specified destination.
 *
 */
public class MessageMirror extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "MessageMirror";

    /* The destination type */
    private String _destinationType;

    /* The destination Name */
    private String _destinationName;

    /**
     * Create a MessageMirror client.
     * @param args Command line arguments.
     */
    public MessageMirror(String[] args)
    {
        super(CLASS, args);
        _destinationType = _argProcessor.getStringArgument("-destinationType");
        _destinationName =  _argProcessor.getStringArgument("-destinationName");
    }

    /**
     * Run the message mirror example.
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-destinationType", "Destination Type: queue/topic");
        _defaults.put("-destinationType", "queue");
         _options.put("-destinationName", "Destination Name");
        _defaults.put("-destinationName", "message_queue");
        MessageMirror messageMirror = new MessageMirror(args);
        messageMirror.runTest();
    }

    /**
     * Start the example.
     */
    private void runTest()
    {
        try
        {
            Destination destination;

            if (_destinationType.equals("queue"))
            {
                // Lookup the queue
                System.out.println(CLASS + ": Looking up queue with name: " + _destinationName);
                destination = (Queue) getInitialContext().lookup(_destinationName);
            }
            else
            {
                // Lookup the topic
                System.out.println(CLASS + ": Looking up topic with name: " + _destinationName);
                destination = (Topic) getInitialContext().lookup(_destinationName);
            }

            // Declare the connection
            Connection connection = getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                    System.exit(0);
                }
            });

            // Create a session on the connection
            // This session is a default choice of non-transacted and uses
            // the auto acknowledge feature of a session.
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create a MessageConsumer
            System.out.println(CLASS + ": Creating a MessageConsumer");
            MessageConsumer messageConsumer = session.createConsumer(destination);

            /**
             * Create a MessageProducer - note that although we create the
             */
            System.out.println(CLASS + ": Creating a MessageProducer");
            MessageProducer messageProducer;

            // Now the messageConsumer is set up we can start the connection
            System.out.println(CLASS + ": Starting connection so MessageConsumer can receive messages");
            connection.start();

            // Cycle round until all the messages are consumed.
            Message requestMessage;
            TextMessage responseMessage;
            boolean end = false;
            while (!end)
            {
                System.out.println(CLASS + ": Receiving the message");

                requestMessage = messageConsumer.receive();

                // Print out the details of the just received message
                System.out.println(CLASS + ": Message received:");
                System.out.println("\tID=" + requestMessage.getJMSMessageID());
                System.out.println("\tCorrelationID=" + requestMessage.getJMSCorrelationID());

                if (requestMessage instanceof TextMessage)
                {
                       if (((TextMessage) requestMessage).getText().equals("That's all, folks!"))
                    {
                        System.out.println("Received final message for " + destination);
                        end = true;
                    }
                    System.out.println("\tContents = " + ((TextMessage)requestMessage).getText());
                }

                // Now bounce the message if a ReplyTo header was set.
                if (requestMessage.getJMSReplyTo() != null)
                {
                     System.out.println("Activating response queue listener for: " + destination);
                    responseMessage = session.createTextMessage("Activating response queue listener for: " + destination);
                    String correlationID = requestMessage.getJMSCorrelationID();
                    if (correlationID != null)
                    {
                        responseMessage.setJMSCorrelationID(correlationID);
                    }
                    messageProducer = session.createProducer(requestMessage.getJMSReplyTo()) ;
                    messageProducer.send(responseMessage);
                }
                System.out.println();
            }

            // Close the connection to the server
            System.out.println(CLASS + ": Closing connection");
            connection.close();

            // Close the JNDI reference
            System.out.println(CLASS + ": Closing JNDI context");
            getInitialContext().close();
        }
        catch (Exception exp)
        {
            exp.printStackTrace();
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }
}
