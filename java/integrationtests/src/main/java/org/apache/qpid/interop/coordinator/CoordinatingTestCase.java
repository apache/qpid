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
package org.apache.qpid.interop.coordinator;

import java.util.Collection;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.qpid.util.ConversationHelper;

/**
 * An CoordinatingTestCase is a JUnit test case extension that knows how to coordinate test clients that take part in a
 * test case as defined in the interop testing specification
 * (http://cwiki.apache.org/confluence/display/qpid/Interop+Testing+Specification).
 *
 * <p/>The real logic of the test cases built on top of this, is embeded in the comparison of the sender and receiver
 * reports. An example test method might look like:
 *
 * <p/><pre>
 * public void testExample()
 * {
 *   Properties testConfig = new Properties();
 *   testConfig.add("TEST_CASE", "example");
 *   ...
 *
 *   Report[] reports = sequenceTest(testConfig);
 *
 *   // Compare sender and receiver reports.
 *   if (report[0] ... report[1] ...)
 *   {
 *     Assert.fail("Sender and receiver reports did not match up.");
 *   }
 * }
 *
 * </pre>
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Coordinate the test sequence amongst participants. <td> {@link ConversationHelper}
 * </table>
 */
public abstract class CoordinatingTestCase extends TestCase
{
    /**
     *
     * @param sender         The contact details of the sending client in the test.
     * @param receiver       The contact details of the sending client in the test.
     * @param allClients     The list of all possible test clients that may accept the invitation.
     * @param testProperties The test case definition.
     */
    public void TestCase(TestClientDetails sender, TestClientDetails receiver, Collection<TestClientDetails> allClients,
                         Properties testProperties)
    { }

    /**
     * Holds a test coordinating conversation with the test clients. This is the basic implementation of the inner
     * loop of Use Case 5. It consists of assign the test roles, begining the test and gathering the test reports
     * from the participants.
     *
     * @param sender                  The contact details of the sending client in the test.
     * @param receiver                The contact details of the receiving client in the test.
     * @param allParticipatingClients The list of all clients accepted the invitation.
     * @param testProperties          The test case definition.
     *
     * @return The test results from the senders and receivers.
     */
    protected Object[] sequenceTest(TestClientDetails sender, TestClientDetails receiver, Properties testProperties)
    {
        // Check if the sender and recevier did not accept the invite to this test.
        {
            // Automatically fail this combination of sender and receiver.
        }

        // Assign the sender role to the sending test client.

        // Assign the receiver role the receiving client.

        // Wait for the senders and receivers to confirm their roles.

        // Start the test.

        // Wait for the test sender to return its report.

        // As the receiver for its report.

        // Wait for the receiver to send its report.

        return null;
    }

    /*protected void setUp()
    { }*/

    /*protected void tearDown()
    { }*/
}
