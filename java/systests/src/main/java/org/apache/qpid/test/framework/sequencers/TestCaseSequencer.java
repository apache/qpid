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
package org.apache.qpid.test.framework.sequencers;

import org.apache.qpid.test.framework.Assertion;
import org.apache.qpid.test.framework.Circuit;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;

import javax.jms.JMSException;
import javax.jms.Message;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A TestCaseSequence is responsibile for creating test circuits appropriate to the context that a test case is
 * running in, and providing an implementation of a standard test procedure over a test circuit.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Provide a standard test procedure over a test circuit.
 * <tr><td> Construct test circuits appropriate to a tests context.
 * </table>
 */
public interface TestCaseSequencer
{
    /**
     * Holds a test coordinating conversation with the test clients. This should consist of assigning the test roles,
     * begining the test, gathering the test reports from the participants, and checking for assertion failures against
     * the test reports.
     *
     * @param testCircuit    The test circuit.
     * @param assertions     The list of assertions to apply to the test circuit.
     * @param testProperties The test case definition.
     */
    public void sequenceTest(Circuit testCircuit, List<Assertion> assertions, Properties testProperties);

    /**
     * Creates a test circuit for the test, configered by the test parameters specified.
     *
     * @param testProperties The test parameters.
     *
     * @return A test circuit.
     */
    public Circuit createCircuit(ParsedProperties testProperties);
}
