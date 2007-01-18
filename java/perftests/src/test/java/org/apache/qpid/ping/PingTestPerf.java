package org.apache.qpid.ping;

import java.net.InetAddress;
import java.util.Properties;

import javax.jms.*;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

/**
 *
 * PingTestPerf is a ping test, that has been written with the intention of being scaled up to run many times
 * simultaneously to simluate many clients/producers/connections.
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
public class PingTestPerf extends TestCase //implements TimingControllerAware
{
    private static Logger _logger = Logger.getLogger(PingTestPerf.class);

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
    private TestPingProducer _testPingProducer;

    /** Holds the test ping client. */
    private TestPingClient _testPingClient;

    // Set up a property reader to extract the test parameters from. Once ContextualProperties is available in
    // the project dependencies, use it to get property overrides for configurable tests and to notify the test runner
    // of the test parameters to log with the results.
    private Properties testParameters = System.getProperties();
    //private Properties testParameters = new ContextualProperties(System.getProperties());

    public PingTestPerf(String name)
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

    public void testPingOk() throws Exception
    {
        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg =
            _testPingProducer.getTestMessage(null, Integer.parseInt(testParameters.getProperty(MESSAGE_SIZE_PROPNAME)),
                                             Boolean.parseBoolean(testParameters.getProperty(PERSISTENT_MODE_PROPNAME)));

        // Use the test timing controller to reset the test timer now and obtain the current time.
        // This can be used to remove the message creation time from the test.
        //TestTimingController timingUtils = getTimingController();
        //long startTime = timingUtils.restart();

        // Send the message.
        _testPingProducer.ping(msg);

        // Fail the test if the timeout was exceeded.
        /*if (reply == null)
        {
            Assert.fail("The ping timed out for message id: " + msg.getJMSMessageID());
        }*/
    }

    protected void setUp() throws Exception
    {
        // Log4j will propagate the test name as a thread local in all log output.
        NDC.push(getName());

        // Ensure that the connection, session and ping queue are established, if they have not already been.
        if (_testPingProducer == null)
        {
            // Extract the test set up paramaeters.
            String brokerDetails = testParameters.getProperty(BROKER_PROPNAME);
            String username = "guest";
            String password = "guest";
            String virtualpath = testParameters.getProperty(VIRTUAL_PATH_PROPNAME);
            String queueName = testParameters.getProperty(PING_QUEUE_NAME_PROPNAME);
            boolean persistent = Boolean.parseBoolean(testParameters.getProperty(PERSISTENT_MODE_PROPNAME));
            boolean transacted = Boolean.parseBoolean(testParameters.getProperty(TRANSACTED_PROPNAME));
            String selector = null;
            boolean verbose = false;
            int messageSize = Integer.parseInt(testParameters.getProperty(MESSAGE_SIZE_PROPNAME));

            // Establish a bounce back client on the ping queue to bounce back the pings.
            _testPingClient = new TestPingClient(brokerDetails, username, password, queueName, virtualpath, transacted,
                                                 selector, verbose);

            // Establish a ping-pong client on the ping queue to send the pings with.
            _testPingProducer = new TestPingProducer(brokerDetails, username, password, virtualpath, queueName, transacted,
                                                     persistent, messageSize, verbose);

            // Start the connections for client and producer running.
            _testPingClient.getConnection().start();
            _testPingProducer.getConnection().start();
        }
    }

    protected void tearDown() throws Exception
    {
        try
        {
            if ((_testPingClient != null) && (_testPingClient.getConnection() != null))
            {
                _testPingClient.getConnection().close();
            }

            if ((_testPingProducer != null) && (_testPingProducer.getConnection() != null))
            {
                _testPingProducer.getConnection().close();
            }
        }
        finally
        {
            NDC.pop();
        }
    }
}
