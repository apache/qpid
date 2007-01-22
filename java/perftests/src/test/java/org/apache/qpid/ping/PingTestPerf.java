package org.apache.qpid.ping;

import java.util.Properties;

import javax.jms.*;

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;

import uk.co.thebadgerset.junit.extensions.AsymptoticTestCase;

/**
 *
 * PingTestPerf is a ping test, that has been written with the intention of being scaled up to run many times
 * simultaneously to simluate many clients/producers/connections.
 *
 * <p/>A single run of the test using the default JUnit test runner will result in the sending and timing of a single
 * full round trip ping. This test may be scaled up using a suitable JUnit test runner.
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
public class PingTestPerf extends AsymptoticTestCase //implements TimingControllerAware
{
    private static Logger _logger = Logger.getLogger(PingTestPerf.class);

    /** Holds the name of the property to get the test message size from. */
    private static final String MESSAGE_SIZE_PROPNAME = "messageSize";

    /** Holds the name of the property to get the ping queue name from. */
    private static final String PING_QUEUE_NAME_PROPNAME = "pingQueue";

    /** holds the queue count, if the test is being performed with multiple queues */
    private static final String PING_QUEUE_COUNT_PROPNAME = "queues";

    /** Holds the name of the property to get the test delivery mode from. */
    private static final String PERSISTENT_MODE_PROPNAME = "persistent";

    /** Holds the name of the property to get the test transactional mode from. */
    private static final String TRANSACTED_PROPNAME = "transacted";

    /** Holds the name of the property to get the test broker url from. */
    private static final String BROKER_PROPNAME = "broker";

    /** Holds the name of the property to get the test broker virtual path. */
    private static final String VIRTUAL_PATH_PROPNAME = "virtualPath";

    /** Holds the waiting timeout for response messages */
    private static final String TIMEOUT_PROPNAME = "timeout";

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
    private static final long TIMEOUT_DEFAULT = 3000;

    // Sets up the test parameters with defaults.
    static
    {
        setSystemPropertyIfNull(MESSAGE_SIZE_PROPNAME, Integer.toString(MESSAGE_SIZE_DEFAULT));
        setSystemPropertyIfNull(PING_QUEUE_NAME_PROPNAME, PING_QUEUE_NAME_DEFAULT);
        setSystemPropertyIfNull(PERSISTENT_MODE_PROPNAME, Boolean.toString(PERSISTENT_MODE_DEFAULT));
        setSystemPropertyIfNull(TRANSACTED_PROPNAME, Boolean.toString(TRANSACTED_DEFAULT));
        setSystemPropertyIfNull(BROKER_PROPNAME, BROKER_DEFAULT);
        setSystemPropertyIfNull(VIRTUAL_PATH_PROPNAME, VIRTUAL_PATH_DEFAULT);
        setSystemPropertyIfNull(TIMEOUT_PROPNAME, Long.toString(TIMEOUT_DEFAULT));
        setSystemPropertyIfNull(PING_QUEUE_COUNT_PROPNAME, Integer.toString(1));
    }

    /** Thread local to hold the per-thread test setup fields. */
    ThreadLocal<PerThreadSetup> threadSetup = new ThreadLocal<PerThreadSetup>();

    // Set up a property reader to extract the test parameters from. Once ContextualProperties is available in
    // the project dependencies, use it to get property overrides for configurable tests and to notify the test runner
    // of the test parameters to log with the results.
    private Properties testParameters = System.getProperties();
    //private Properties testParameters = new ContextualProperties(System.getProperties());

    public PingTestPerf(String name)
    {
        super(name);
    }

    /**
     * Compile all the tests into a test suite.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Ping Performance Tests");

        // Run performance tests in read committed mode.
        suite.addTest(new PingTestPerf("testPingOk"));

        return suite;
               //return new junit.framework.TestSuite(PingTestPerf.class);
    }

    private static void setSystemPropertyIfNull(String propName, String propValue)
    {
        if (System.getProperty(propName) == null)
        {
            System.setProperty(propName, propValue);
        }
    }

    public void testPingOk(int numPings) throws Exception
    {
        // Get the per thread test setup to run the test through.
        PerThreadSetup perThreadSetup = threadSetup.get();

        // Generate a sample message. This message is already time stamped and has its reply-to destination set.
        ObjectMessage msg =
            perThreadSetup._pingItselfClient.getTestMessage(null,
                                                            Integer.parseInt(testParameters.getProperty(
                                                                                 MESSAGE_SIZE_PROPNAME)),
                                                            Boolean.parseBoolean(testParameters.getProperty(
                                                                                     PERSISTENT_MODE_PROPNAME)));

        // start the test
        long timeout = Long.parseLong(testParameters.getProperty(TIMEOUT_PROPNAME));
        int numReplies = perThreadSetup._pingItselfClient.pingAndWaitForReply(msg, numPings, timeout);

        _logger.info("replies = " + numReplies);

        // Fail the test if the timeout was exceeded.
        if (numReplies != numPings)
        {
            Assert.fail("The ping timed out. Messages Sent = " + numPings + ", MessagesReceived = " + numReplies);
        }
    }

    protected void setUp() throws Exception
    {
        // Log4j will propagate the test name as a thread local in all log output.
        // Carefull when using this, it can cause memory leaks when not cleaned up properly.
        //NDC.push(getName());

        // Create the test setups on a per thread basis, only if they have not already been created.
        if (threadSetup.get() == null)
        {
            PerThreadSetup perThreadSetup = new PerThreadSetup();

            // Extract the test set up paramaeters.
            String brokerDetails = testParameters.getProperty(BROKER_PROPNAME);
            String username = "guest";
            String password = "guest";
            String virtualpath = testParameters.getProperty(VIRTUAL_PATH_PROPNAME);
            int queueCount = Integer.parseInt(testParameters.getProperty(PING_QUEUE_COUNT_PROPNAME));
            String queueName = testParameters.getProperty(PING_QUEUE_NAME_PROPNAME);
            boolean persistent = Boolean.parseBoolean(testParameters.getProperty(PERSISTENT_MODE_PROPNAME));
            boolean transacted = Boolean.parseBoolean(testParameters.getProperty(TRANSACTED_PROPNAME));
            String selector = null;
            boolean verbose = false;
            int messageSize = Integer.parseInt(testParameters.getProperty(MESSAGE_SIZE_PROPNAME));

            // Establish a client to ping a Queue and listen the reply back from same Queue
            if (queueCount > 1)
            {
                // test client with multiple queues
                perThreadSetup._pingItselfClient = new TestPingItself(brokerDetails, username, password, virtualpath,
                                                                      queueCount, selector, transacted, persistent,
                                                                      messageSize, verbose);
            }
            else
            {
                // Establish a client to ping a Queue and listen the reply back from same Queue
                perThreadSetup._pingItselfClient = new TestPingItself(brokerDetails, username, password, virtualpath,
                                                                      queueName, selector, transacted, persistent,
                                                                      messageSize, verbose);
            }

            // Start the client connection
            perThreadSetup._pingItselfClient.getConnection().start();

            // Attach the per-thread set to the thread.
            threadSetup.set(perThreadSetup);
        }
    }

    protected void tearDown() throws Exception
    {
        try
        {
            /*
            if ((_pingItselfClient != null) && (_pingItselfClient.getConnection() != null))
            {
                _pingItselfClient.getConnection().close();
            }
            */
        }
        finally
        {
            //NDC.pop();
        }
    }

    private static class PerThreadSetup
    {
        /** Holds the test ping client. */
        private TestPingItself _pingItselfClient;
    }
}
