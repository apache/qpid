package org.apache.qpid.interop.coordinator.testcases;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Message;

import junit.framework.Assert;

import org.apache.log4j.Logger;

import org.apache.qpid.interop.coordinator.CoordinatingTestCase;

/**
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Setup pub/sub test parameters and compare with test output. <td> {@link CoordinatingTestCase}
 * </table>
 */
public class CoordinatingTestCase3BasicPubSub extends CoordinatingTestCase
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(CoordinatingTestCase3BasicPubSub.class);

    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public CoordinatingTestCase3BasicPubSub(String name)
    {
        super(name);
    }

    /**
     * Performs the basic P2P test case, "Test Case 2" in the specification.
     */
    public void testBasicPubSub() throws Exception
    {
        log.debug("public void testBasicPubSub(): called");

        Map<String, Object> testConfig = new HashMap<String, Object>();
        testConfig.put("TEST_NAME", "TC3_BasicPubSub");
        testConfig.put("PUBSUB_KEY", "tc3route");
        testConfig.put("PUBSUB_NUM_MESSAGES", 10);
        testConfig.put("PUBSUB_NUM_RECEIVERS", 5);

        Message[] reports = sequenceTest(testConfig);

        // Compare sender and receiver reports.
        int messagesSent = reports[0].getIntProperty("MESSAGE_COUNT");
        int messagesReceived = reports[1].getIntProperty("MESSAGE_COUNT");

        Assert.assertEquals("The requested number of messages were not sent.", 10, messagesSent);
        Assert.assertEquals("Received messages did not match up to num sent * num receivers.", messagesSent * 5,
            messagesReceived);
    }

    /**
     * Should provide a translation from the junit method name of a test to its test case name as defined in the
     * interop testing specification. For example the method "testP2P" might map onto the interop test case name
     * "TC2_BasicP2P".
     *
     * @param methodName The name of the JUnit test method.
     * @return The name of the corresponding interop test case.
     */
    public String getTestCaseNameForTestMethod(String methodName)
    {
        return "TC3_BasicPubSub";
    }
}
