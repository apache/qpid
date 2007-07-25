/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.interop.coordinator.testcases;

import org.apache.log4j.Logger;

import org.apache.qpid.interop.coordinator.sequencers.TestCaseSequencer;
import org.apache.qpid.test.framework.Circuit;
import org.apache.qpid.test.framework.FrameworkBaseCase;
import org.apache.qpid.test.framework.MessagingTestConfigProperties;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

/**
 * CircuitTestCase runs a test over a {@link Circuit} controlled by the test parameters.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td>
 * </table>
 *
 * @todo When working with test context properties, add overrides to defaults to the singleton instance, but when taking
 *       a starting point to add specific test case parameters to, take a copy. Use the copy with test case specifics
 *       to control the test.
 */
public class CircuitTestCase extends FrameworkBaseCase
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(CircuitTestCase.class);

    /**
     * Creates a new test case with the specified name.
     *
     * @param name The test case name.
     */
    public CircuitTestCase(String name)
    {
        super(name);
    }

    /**
     * Performs the a basic P2P test case.
     *
     * @throws Exception Any exceptions are allowed to fall through and fail the test.
     */
    public void testBasicP2P() throws Exception
    {
        log.debug("public void testBasicP2P(): called");

        // Get the test parameters, any overrides on the command line will have been applied.
        ParsedProperties testProps = TestContextProperties.getInstance(MessagingTestConfigProperties.defaults);

        // Customize the test parameters.
        testProps.setProperty("TEST_NAME", "DEFAULT_CIRCUIT_TEST");
        testProps.setProperty(MessagingTestConfigProperties.SEND_DESTINATION_NAME_ROOT_PROPNAME, "testqueue");

        // Get the test sequencer to create test circuits and run the standard test procedure through.
        TestCaseSequencer sequencer = getTestSequencer();

        // Send the test messages, and check that there were no errors doing so.
        Circuit testCircuit = sequencer.createCircuit(testProps);
        sequencer.sequenceTest(testCircuit, assertionList(testCircuit.getPublisher().noExceptionsAssertion()), testProps);

        // Check that all of the message were sent.
        // Check that the receiving end got the same number of messages as the publishing end.
    }

    /**
     * Should provide a translation from the junit method name of a test to its test case name as known to the test
     * clients that will run the test. The purpose of this is to convert the JUnit method name into the correct test
     * case name to place into the test invite. For example the method "testP2P" might map onto the interop test case
     * name "TC2_BasicP2P".
     *
     * @param methodName The name of the JUnit test method.
     *
     * @return The name of the corresponding interop test case.
     */
    public String getTestCaseNameForTestMethod(String methodName)
    {
        return "DEFAULT_CIRCUIT_TEST";
    }
}
