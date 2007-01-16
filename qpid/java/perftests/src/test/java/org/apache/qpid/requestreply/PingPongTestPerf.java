package org.apache.qpid.requestreply;

import java.net.InetAddress;
import java.util.Properties;

import javax.jms.*;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.jms.Connection;
import org.apache.qpid.jms.MessageProducer;
import org.apache.qpid.jms.Session;

/**
 * PingPongTestPerf is a full round trip ping test, that has been written with the intention of being scaled up to run
 * many times simultaneously. A full round trip ping sends a message from a producer to a conumer, then the consumer
 * replies to the message on a temporary queue.
 *
 * <p/>A single run of the test using the default JUnit test runner will result in the sending and timing of a single
 * full round trip ping. This test may be scaled up using a suitable JUnit test runner. See {@link TKTestRunner} or
 * {@link PPTestRunner} for more information on how to do this.
 *
 * <p/>The setup/teardown cycle establishes a connection to a broker and sets up a queue to send ping messages to and a
 * temporary queue for replies. This setup is only established once for all the test repeats/threads that may be run,
 * except if the connection is lost in which case an attempt to re-establish the setup is made.
 *
 * <p/>The test cycle is: Connects to a queue, creates a temporary queue, creates messages containing a property that
 * is the name of the temporary queue, fires off a message on the original queue and waits for a response on the
 * temporary queue.
 *
 * <p/>Configurable test properties: message size, transacted or not, persistent or not. Broker connection details.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * </table>
 *
 * @author Rupert Smith
 */
public class PingPongTestPerf extends TestCase implements ExceptionListener //, TimingControllerAware
{
    private static Logger _logger = Logger.getLogger(PingPongTestPerf.class);

    /** Holds the name of the property to get the test message size from. */
    private static final String MESSAGE_SIZE_PROPNAME = "messageSize";

    /** Holds the name of the property to get the ping queue name from. */
    private static final String PING_QUEUE_NAME_PROPNAME = "pingQueue";

    /** Holds the name of the property to get the test delivery mode from. */
    private static final String PERSISTENT_MODE_PROPNAME = "persistent";

    /** Holds the name of the property to get the test transactional mode from. */
    private static final String TRANSACTED_PROPNAME = "transacted";

    /** Holds the name of the property to get the test broker url from. */
    private static final String BROKER_PROPNAME = "broker";

    /** Holds the name of the property to get the test broker virtual path. */
    private static final String VIRTUAL_PATH_PROPNAME = "virtualPath";

    /** Holds the size of message body to attach to the ping messages. */
    private static final int MESSAGE_SIZE_DEFAULT = 0;

    /** Holds the name of the queue to which pings are sent. */
    private static final String PING_QUEUE_NAME_DEFAULT = "ping";

    /** Holds the message delivery mode to use for the test. */
    private static final boolean PERSISTENT_MODE_DEFAULT = false;

    /** Holds the transactional mode to use for the test. */
    private static final boolean TRANSACTED_DEFAULT = false;

    /** Holds the default broker url for the test. */
    private static final String BROKER_DEFAULT = "tcp://localhost:5672";

    /** Holds the default virtual path for the test. */
    private static final String VIRTUAL_PATH_DEFAULT = "/test";

    /** Sets a default ping timeout. */
    private static final long TIMEOUT = 3000;

    // Sets up the test parameters with defaults.
    static
    {
        setSystemPropertyIfNull(MESSAGE_SIZE_PROPNAME, Integer.toString(MESSAGE_SIZE_DEFAULT));
        setSystemPropertyIfNull(PING_QUEUE_NAME_PROPNAME, PING_QUEUE_NAME_DEFAULT);
        setSystemPropertyIfNull(PERSISTENT_MODE_PROPNAME, Boolean.toString(PERSISTENT_MODE_DEFAULT));
        setSystemPropertyIfNull(TRANSACTED_PROPNAME, Boolean.toString(TRANSACTED_DEFAULT));
        setSystemPropertyIfNull(BROKER_PROPNAME, BROKER_DEFAULT);
        setSystemPropertyIfNull(VIRTUAL_PATH_PROPNAME, VIRTUAL_PATH_DEFAULT);
    }

    /** Holds the test ping-pong producer. */
    private PingPongProducer _testPingProducer;

    // Set up a property reader to extract the test parameters from. Once ContextualProperties is available in
    // the project dependencies, use it to get property overrides for configurable tests and to notify the test runner
    // of the test parameters to log with the results.
    private Properties testParameters = System.getProperties();
    //private Properties testParameters = new ContextualProperties(System.getProperties());

    /** Holds the connection to the broker. */
    private Connection _connection = null;

    /** Holds the current session to the broker. */
    private Session _session;

    /** Holds the destination to send the ping messages to. */
    private Queue _pingQueue;

    /** Holds the destination to send replies to. */
    private Queue _replyQueue;

    /** Holds a message producer, set up on the ping destination, to send messages through. */
    private MessageProducer _producer;

    /** Holds a message consumer, set up on the ping destination, to receive pings through. */
    private MessageConsumer _pingConsumer;

    /** Holds a message consumer, set up on the pong destination, to receive replies through. */
    private MessageConsumer _pongConsumer;

    /** Holds a failure flag, which gets set if the connection to the broker goes down. */
    private boolean _failure;

    public PingPongTestPerf(String name)
    {
        super(name);
    }

    private static void setSystemPropertyIfNull(String propName, String propValue)
    {
        if (System.getProperty(propName) == null)
        {
            System.setProperty(propName, propValue);
        }
    }

    public void testPingPongOk() throws Exception
    {
        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg =
            PingPongProducer.getTestMessage(_session, _replyQueue,
                                            Integer.parseInt(testParameters.getProperty(MESSAGE_SIZE_PROPNAME)),
                                            System.currentTimeMillis(),
                                            Boolean.parseBoolean(testParameters.getProperty(PERSISTENT_MODE_PROPNAME)));

        // Use the test timing controller to reset the test timer now and obtain the current time.
        // This can be used to remove the message creation time from the test.
        //TestTimingController timingUtils = getTimingController();
        //long startTime = timingUtils.restart();

        // Send the message and wait for a reply.
        Message reply = _testPingProducer.pingAndWaitForReply(msg, TIMEOUT);

        // Fail the test if the timeout was exceeded.
        if (reply == null)
        {
            Assert.fail("The ping timed out for message id: " + msg.getJMSMessageID());
        }
    }

    /**
     * This is a callback method that is registered to receive any JMSExceptions that occurr on the connection to
     * the broker. It sets a failure flag to indicate that there is an error condition.
     *
     * @param e The JMSException that triggered this callback method.
     *
     * @see javax.jms.ExceptionListener#onException(javax.jms.JMSException)
     */
    public void onException(JMSException e)
    {
        // Set the failure flag.
        _failure = true;

        _logger.debug("There was a JMSException: " + e.getMessage(), e);
    }

    protected void setUp() throws Exception
    {
        // Log4j will propagate the test name as a thread local in all log output.
        NDC.push(getName());

        // Ensure that the connection, session and ping queue are established, if they have not already been.
        if (_connection == null)
        {
            // Create a client id that identifies the client machine.
            String clientID = InetAddress.getLocalHost().getHostName() + System.currentTimeMillis();

            // Connect to the broker.
            _connection = new AMQConnection(testParameters.getProperty(BROKER_PROPNAME), "guest", "guest", clientID,
                                            testParameters.getProperty(VIRTUAL_PATH_PROPNAME));
            _connection.setExceptionListener(this);

            // Create a transactional or non-transactional session, based on the test properties, if a session has not
            // already been created.
            if (Boolean.parseBoolean(testParameters.getProperty(TRANSACTED_PROPNAME)))
            {
                _session = (Session) _connection.createSession(true, Session.SESSION_TRANSACTED);
            }
            else
            {
                _session = (Session) _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            }

            // Create a queue to send the pings on.
            _pingQueue = new AMQQueue(testParameters.getProperty(PING_QUEUE_NAME_PROPNAME));
            _producer = (MessageProducer) _session.createProducer(_pingQueue);

            // Create a temporary queue to reply with the pongs on.
            _replyQueue = _session.createTemporaryQueue();

            // Create the ping and pong consumers on their respective destinations.
            _pingConsumer = _session.createConsumer(_pingQueue);
            _pongConsumer = _session.createConsumer(_replyQueue);

            // Establish a bounce back client on the ping queue to bounce back the pings.
            new org.apache.qpid.requestreply.PingPongClient(_session, _pingConsumer, false);

            // Establish a ping-pong client on the ping queue to send pings and wait for replies.
            _testPingProducer = new org.apache.qpid.requestreply.PingPongProducer(_session, _replyQueue, _producer,
                                                                                  _pongConsumer);

            _connection.start();
        }
    }

    protected void tearDown() throws Exception
    {
        try
        {
            _connection.close();
        }
        finally
        {
            NDC.pop();
        }
    }
}
