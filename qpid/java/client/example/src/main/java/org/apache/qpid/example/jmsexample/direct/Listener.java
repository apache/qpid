package org.apache.qpid.example.jmsexample.direct;

import org.redhat.mrg.messaging.examples.BaseExample;

import javax.jms.*;

/**
 * The example creates a MessageConsumer on the specified
 * Queue and uses a MessageListener with this MessageConsumer
 * in order to enable asynchronous delivery.
 */
public class Listener extends BaseExample implements MessageListener
{
    /* Used in log output. */
    private static final String CLASS = "Listener";

    /* The queue name  */
    private String _queueName;

    /**
     * An object to synchronize on.
     */
    private final static Object _lock = new Object();

    /**
     * A boolean to indicate a clean finish.
     */
    private static boolean _finished = false;

    /**
     * A boolean to indicate an unsuccesful finish.
     */
    private static boolean _failed = false;

    /**
     * Create an Listener client.
     *
     * @param args Command line arguments.
     */
    public Listener(String[] args)
    {
        super(CLASS, args);
        _queueName = _argProcessor.getStringArgument("-queueName");
    }

    /**
     * Run the message consumer example.
     *
     * @param args Command line arguments.
     */
    public static void main(String[] args)
    {
        _options.put("-queueName", "Queue name");
        _defaults.put("-queueName", "message_queue");
        Listener listener = new Listener(args);
        listener.runTest();
    }

    /**
     * Start the example.
     */
    private void runTest()
    {
        try
        {
            // lookup the queue
            Queue destination = (Queue) getInitialContext().lookup(_queueName);

            // Declare the connection
            Connection connection = getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(
                    CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
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

            // Set a message listener on the messageConsumer
            messageConsumer.setMessageListener(this);

            // Now the messageConsumer is set up we can start the connection
            System.out.println(CLASS + ": Starting connection so MessageConsumer can receive messages");
            connection.start();

            // Wait for the messageConsumer to have received all the messages it needs
            synchronized (_lock)
            {
                while (!_finished && !_failed)
                {
                    _lock.wait();
                }
            }

            // If the MessageListener abruptly failed (probably due to receiving a non-text message)
            if (_failed)
            {
                System.out.println(CLASS + ": This sample failed as it received unexpected messages");
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
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }

    /**
     * This method is required by the <CODE>MessageListener</CODE> interface. It
     * will be invoked  when messages are available.
     * After receiving the finish message (That's all, folks!) it releases a lock so that the
     * main program may continue.
     *
     * @param message The message.
     */
    public void onMessage(Message message)
    {
        try
        {
            if (message instanceof TextMessage)
            {
                System.out.println(" - contents = " + ((TextMessage) message).getText());
                if (((TextMessage) message).getText().equals("That's all, folks!"))
                {
                    System.out.println("Shutting down listener for " + _queueName);
                    synchronized (_lock)
                    {
                        _finished = true;
                        _lock.notifyAll();
                    }
                }
            }
            else
            {
                System.out.println(" [not text message]");
            }
        }
        catch (JMSException exp)
        {
            System.out.println(CLASS + ": Caught an exception handling a received message");
            exp.printStackTrace();
            synchronized (_lock)
            {
                _failed = true;
                _lock.notifyAll();
            }
        }
    }
}
