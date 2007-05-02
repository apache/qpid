package org.apache.qpid.interop.coordinator.testcases;

import java.util.Properties;

import javax.jms.Message;

import junit.framework.Assert;

import org.apache.qpid.interop.coordinator.CoordinatingTestCase;

/**
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Setup p2p test parameters and compare with test output. <td> {@link CoordinatingTestCase}
 * </table>
 */
public class CoordinatingTestCase2BasicP2P extends CoordinatingTestCase
{
    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public CoordinatingTestCase2BasicP2P(String name)
    {
        super(name);
    }

    /**
     * Performs the basic P2P test case, "Test Case 2" in the specification.
     */
    public void testBasicP2P() throws Exception
    {
        Properties testConfig = new Properties();
        testConfig.setProperty("TEST_CASE", "TC2_BasicP2P");
        testConfig.setProperty("P2P_QUEUE_AND_KEY_NAME", "tc2queue");
        testConfig.setProperty("P2P_NUM_MESSAGES", "50");

        Message[] reports = sequenceTest(testConfig);

        // Compare sender and receiver reports.
        int messagesSent = reports[0].getIntProperty("MESSAGE_COUNT");
        int messagesReceived = reports[1].getIntProperty("MESSAGE_COUNT");

        Assert.assertEquals("The requested number of messages were not sent.", 50, messagesSent);
        Assert.assertEquals("Sender and receiver messages sent did not match up.", messagesSent, messagesReceived);
    }
}
