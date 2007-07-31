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
package org.apache.qpid.test.framework.distributedtesting;

import org.apache.log4j.Logger;

import org.apache.qpid.test.framework.sequencers.DistributedTestSequencer;
import org.apache.qpid.test.framework.FrameworkBaseCase;

/**
 * DistributedTestCase provides a base class implementation of the {@link org.apache.qpid.test.framework.sequencers.DistributedTestSequencer}, taking care of its
 * more mundane aspects, such as recording the test pariticipants.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Accept notification of test case participants.
 *     <td> {@link DistributedTestDecorator}
 * <tr><td> Accept JMS Connection to carry out the coordination over.
 * </table>
 */
public abstract class DistributedTestCase extends FrameworkBaseCase
{
    /** Used for debugging. */
    private final Logger log = Logger.getLogger(DistributedTestCase.class);

    /**
     * Creates a new test case with the specified name.
     *
     * @param name The test case name.
     */
    public DistributedTestCase(String name)
    {
        super(name);
    }

    /**
     * Gets the test sequencer for this distributed test, cast as a {@link DistributedTestSequencer}, provided that it
     * is one. If the test sequencer is not distributed, this returns null.
     */
    public DistributedTestSequencer getDistributedTestSequencer()
    {
        try
        {
            return (DistributedTestSequencer) testSequencer;
        }
        catch (ClassCastException e)
        {
            return null;
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
    public abstract String getTestCaseNameForTestMethod(String methodName);
}
