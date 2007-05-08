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
 * <tr><td> Exercises the interop testing framework without actually sending any test messages.
 *     <td> {@link org.apache.qpid.interop.coordinator.CoordinatingTestCase}
 * </table>
 */
public class CoordinatingTestCase1DummyRun extends CoordinatingTestCase
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(CoordinatingTestCase1DummyRun.class);

    /**
     * Creates a new coordinating test case with the specified name.
     *
     * @param name The test case name.
     */
    public CoordinatingTestCase1DummyRun(String name)
    {
        super(name);
    }

    /**
     * Performs the basic P2P test case, "Test Case 2" in the specification.
     */
    public void testDummyRun() throws Exception
    {
        log.debug("public void testDummyRun(): called");

        Map<String, Object> testConfig = new HashMap<String, Object>();
        testConfig.put("TEST_NAME", "TC1_DummyRun");

        Message[] reports = sequenceTest(testConfig);

        // Compare sender and receiver reports.
        Assert.assertEquals("Expected to get 2 dummy reports.", 2, reports.length);
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
        return "TC1_DummyRun";
    }
}
