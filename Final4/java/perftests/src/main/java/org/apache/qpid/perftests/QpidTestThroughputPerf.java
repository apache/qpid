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
package org.apache.qpid.perftests;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.log4j.Logger;

import org.apache.qpid.test.framework.Assertion;
import org.apache.qpid.test.framework.Circuit;
import org.apache.qpid.test.framework.FrameworkBaseCase;
import org.apache.qpid.test.framework.MessagingTestConfigProperties;
import org.apache.qpid.test.framework.sequencers.CircuitFactory;

import uk.co.thebadgerset.junit.extensions.TestThreadAware;
import uk.co.thebadgerset.junit.extensions.TimingController;
import uk.co.thebadgerset.junit.extensions.TimingControllerAware;
import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

import java.util.LinkedList;

/**
 * QpidTestThroughputPerf runs a test over a {@link Circuit} controlled by the test parameters. It logs timings of
 * the time required to receive samples consisting of batches of messages. As the time samples is taken over a reasonable
 * sized message batch, it measures message throughput.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td>
 * </table>
 *
 * @todo Check that all of the messages were sent. Check that the receiving end got the same number of messages as
 *       the publishing end.
 */
public class QpidTestThroughputPerf extends FrameworkBaseCase implements TimingControllerAware, TestThreadAware
{
    /** Used for debugging. */
    private static final Logger log = Logger.getLogger(QpidTestThroughputPerf.class);

    /** Holds the timing controller, used to log test timings from self-timed tests. */
    private TimingController timingController;

    /** Thread local to hold the per-thread test setup fields. */
    ThreadLocal<PerThreadSetup> threadSetup = new ThreadLocal<PerThreadSetup>();

    /**
     * Creates a new test case with the specified name.
     *
     * @param name The test case name.
     */
    public QpidTestThroughputPerf(String name)
    {
        super(name);
    }

    /**
     * Performs the a basic P2P test case.
     *
     * @param numMessages The number of messages to send in the test.
     */
    public void testThroughput(int numMessages)
    {
        log.debug("public void testThroughput(): called");

        PerThreadSetup setup = threadSetup.get();
        assertNoFailures(setup.testCircuit.test(numMessages, new LinkedList<Assertion>()));
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
        log.debug("public String getTestCaseNameForTestMethod(String methodName = " + methodName + "): called");

        return "DEFAULT_CIRCUIT_TEST";
    }

    /**
     * Used by test runners that can supply a {@link uk.co.thebadgerset.junit.extensions.TimingController} to set the
     * controller on an aware test.
     *
     * @param controller The timing controller.
     */
    public void setTimingController(TimingController controller)
    {
        timingController = controller;
    }

    /**
     * Performs test fixture creation on a per thread basis. This will only be called once for each test thread.
     */
    public void threadSetUp()
    {
        // Get the test parameters, any overrides on the command line will have been applied.
        ParsedProperties testProps = TestContextProperties.getInstance(MessagingTestConfigProperties.defaults);

        // Customize the test parameters.
        testProps.setProperty("TEST_NAME", "DEFAULT_CIRCUIT_TEST");
        testProps.setProperty(MessagingTestConfigProperties.SEND_DESTINATION_NAME_ROOT_PROPNAME, "testqueue");

        // Get the test circuit factory to create test circuits and run the standard test procedure through.
        CircuitFactory circuitFactory = getCircuitFactory();

        // Create the test circuit. This projects the circuit onto the available test nodes and connects it up.
        Circuit testCircuit = circuitFactory.createCircuit(testProps);

        // Store the test configuration for the thread.
        PerThreadSetup setup = new PerThreadSetup();
        setup.testCircuit = testCircuit;
        threadSetup.set(setup);
    }

    /**
     * Called when a test thread is destroyed.
     */
    public void threadTearDown()
    { }

    /**
     * Holds the per-thread test configurations.
     */
    protected static class PerThreadSetup
    {
        /** Holds the test circuit to run tests on. */
        Circuit testCircuit;
    }

    /**
     * Compiles all the tests in this class into a suite.
     *
     * @return The test suite.
     */
    public static Test suite()
    {
        // Build a new test suite
        TestSuite suite = new TestSuite("Qpid Throughput Performance Tests");

        suite.addTest(new QpidTestThroughputPerf("testThroughput"));

        return suite;
    }
}
