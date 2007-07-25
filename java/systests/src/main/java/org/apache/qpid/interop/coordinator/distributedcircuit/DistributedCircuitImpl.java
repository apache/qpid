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
package org.apache.qpid.interop.coordinator.distributedcircuit;

import org.apache.qpid.test.framework.Assertion;
import org.apache.qpid.test.framework.Circuit;
import org.apache.qpid.test.framework.Publisher;
import org.apache.qpid.test.framework.Receiver;

import java.util.List;

/**
 * DistributedCircuitImpl is a distributed implementation of the test {@link Circuit}. Many publishers and receivers
 * accross multiple machines may be combined to form a single test circuit. The test circuit extracts reports from
 * all of its publishers and receivers, and applies its assertions to these reports.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Supply the publishing and receiving ends of a test messaging circuit.
 * <tr><td> Start the circuit running.
 * <tr><td> Close the circuit down.
 * <tr><td> Take a reading of the circuits state.
 * <tr><td> Apply assertions against the circuits state.
 * <tr><td> Send test messages over the circuit.
 * <tr><td> Perform the default test procedue on the circuit.
 * </table>
 */
public class DistributedCircuitImpl implements Circuit
{
    /**
     * Gets the interface on the publishing end of the circuit.
     *
     * @return The publishing end of the circuit.
     */
    public Publisher getPublisher()
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Gets the interface on the receiving end of the circuit.
     *
     * @return The receiving end of the circuit.
     */
    public Receiver getReceiver()
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Connects and starts the circuit. After this method is called the circuit is ready to send messages.
     */
    public void start()
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Checks the test circuit. The effect of this is to gather the circuits state, for both ends of the circuit,
     * into a report, against which assertions may be checked.
     */
    public void check()
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Closes the circuit. All associated resources are closed.
     */
    public void close()
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Applied a list of assertions against the test circuit. The {@link #check()} method should be called before doing
     * this, to ensure that the circuit has gathered its state into a report to assert against.
     *
     * @param assertions The list of assertions to apply.
     * @return Any assertions that failed.
     */
    public List<Assertion> applyAssertions(List<Assertion> assertions)
    {
        throw new RuntimeException("Not Implemented.");
    }

    /**
     * Runs the default test procedure against the circuit, and checks that all of the specified assertions hold.
     *
     * @param numMessages The number of messages to send using the default test procedure.
     * @param assertions  The list of assertions to apply.
     * @return Any assertions that failed.
     */
    public List<Assertion> test(int numMessages, List<Assertion> assertions)
    {
        throw new RuntimeException("Not Implemented.");
    }
}
