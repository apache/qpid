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
package org.apache.qpid.test.framework;

import junit.framework.TestCase;

import org.apache.log4j.Logger;
import org.apache.log4j.NDC;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.test.framework.localcircuit.LocalCircuitImpl;
import org.apache.qpid.test.framework.sequencers.CircuitFactory;
import org.apache.qpid.util.ConversationFactory;

import uk.co.thebadgerset.junit.extensions.AsymptoticTestCase;
import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * FrameworkBaseCase provides a starting point for writing test cases against the test framework. Its main purpose is
 * to provide some convenience methods for testing.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Create and clean up in-vm brokers on every test case.
 * <tr><td> Produce lists of assertions from assertion creation calls.
 * <tr><td> Produce JUnit failures from assertion failures.
 * <tr><td> Convert failed assertions to error messages.
 * </table>
 */
public class FrameworkBaseCase extends AsymptoticTestCase
{
    /** Used for debugging purposes. */
    private static final Logger log = Logger.getLogger(FrameworkBaseCase.class);

    /** Holds the test sequencer to create and run test circuits with. */
    protected CircuitFactory circuitFactory = new DefaultCircuitFactory();

    /**
     * Creates a new test case with the specified name.
     *
     * @param name The test case name.
     */
    public FrameworkBaseCase(String name)
    {
        super(name);
    }

    /**
     * Returns the test case sequencer that provides test circuit, and test sequence implementations. The sequencer
     * that this base case returns by default is suitable for running a test circuit with both circuit ends colocated
     * on the same JVM.
     *
     * @return The test case sequencer.
     */
    protected CircuitFactory getCircuitFactory()
    {
        return circuitFactory;
    }

    /**
     * Overrides the default test circuit factory. Test decorators can use this to supply distributed test sequencers or
     * other test circuit factory specializations.
     *
     * @param circuitFactory The new test circuit factory.
     */
    public void setCircuitFactory(CircuitFactory circuitFactory)
    {
        this.circuitFactory = circuitFactory;
    }

    /**
     * Creates a list of assertions.
     *
     * @param asserts The assertions to compile in a list.
     *
     * @return A list of assertions.
     */
    protected List<Assertion> assertionList(Assertion... asserts)
    {
        List<Assertion> result = new ArrayList<Assertion>();

        for (Assertion assertion : asserts)
        {
            result.add(assertion);
        }

        return result;
    }

    /**
     * Generates a JUnit assertion exception (failure) if any assertions are passed into this method, also concatenating
     * all of the error messages in the assertions together to form an error message to diagnose the test failure with.
     *
     * @param asserts The list of failed assertions.
     */
    protected void assertNoFailures(List<Assertion> asserts)
    {
        log.debug("protected void assertNoFailures(List<Assertion> asserts = " + asserts + "): called");

        // Check if there are no assertion failures, and return without doing anything if so.
        if ((asserts == null) || asserts.isEmpty())
        {
            return;
        }

        // Compile all of the assertion failure messages together.
        String errorMessage = assertionsToString(asserts);

        // Fail with the error message from all of the assertions.
        fail(errorMessage);
    }

    /**
     * Converts a list of failed assertions into an error message.
     *
     * @param asserts The failed assertions.
     *
     * @return The error message.
     */
    protected String assertionsToString(List<Assertion> asserts)
    {
        String errorMessage = "";

        for (Assertion assertion : asserts)
        {
            errorMessage += assertion.toString() + "\n";
        }

        return errorMessage;
    }

    /**
     * Ensures that the in-vm broker is created and initialized.
     *
     * @throws Exception Any exceptions allowed to fall through and fail the test.
     */
    protected void setUp() throws Exception
    {
        NDC.push(getName());

        // Ensure that the in-vm broker is created.
        TransportConnection.createVMBroker(1);
    }

    /**
     * Ensures that the in-vm broker is cleaned up after each test run.
     */
    protected void tearDown()
    {
        try
        {
            // Ensure that the in-vm broker is cleaned up so that the next test starts afresh.
            TransportConnection.killVMBroker(1);
            ApplicationRegistry.remove(1);
        }
        finally
        {
            NDC.pop();
        }
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
        return methodName;
    }

    /**
     * DefaultCircuitFactory is a test sequencer that creates test circuits with publishing and receiving ends rooted
     * on the same JVM.
     */
    public class DefaultCircuitFactory implements CircuitFactory
    {
        /**
         * Holds a test coordinating conversation with the test clients. This should consist of assigning the test roles,
         * begining the test and gathering the test reports from the participants.
         *
         * @param testCircuit    The test circuit.
         * @param assertions     The list of assertions to apply to the test circuit.
         * @param testProperties The test case definition.
         */
        public void sequenceTest(Circuit testCircuit, List<Assertion> assertions, Properties testProperties)
        {
            assertNoFailures(testCircuit.test(1, assertions));
        }

        /**
         * Creates a test circuit for the test, configered by the test parameters specified.
         *
         * @param testProperties The test parameters.
         * @return A test circuit.
         */
        public Circuit createCircuit(ParsedProperties testProperties)
        {
            return LocalCircuitImpl.createCircuit(testProperties);
        }

        /**
         * Sets the sender test client to coordinate the test with.
         *
         * @param sender The contact details of the sending client in the test.
         */
        public void setSender(TestClientDetails sender)
        {
            throw new RuntimeException("Not implemented.");
        }

        /**
         * Sets the receiving test client to coordinate the test with.
         *
         * @param receiver The contact details of the sending client in the test.
         */
        public void setReceiver(TestClientDetails receiver)
        {
            throw new RuntimeException("Not implemented.");
        }

        /**
         * Supplies the sending test client.
         *
         * @return The sending test client.
         */
        public TestClientDetails getSender()
        {
            throw new RuntimeException("Not implemented.");
        }

        /**
         * Supplies the receiving test client.
         *
         * @return The receiving test client.
         */
        public List<TestClientDetails> getReceivers()
        {
            throw new RuntimeException("Not implemented.");
        }

        /**
         * Accepts the conversation factory over which to hold the test coordinating conversation.
         *
         * @param conversationFactory The conversation factory to coordinate the test over.
         */
        public void setConversationFactory(ConversationFactory conversationFactory)
        {
            throw new RuntimeException("Not implemented.");
        }
    }
}
