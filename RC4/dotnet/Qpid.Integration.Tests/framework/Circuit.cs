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
using System.Collections.Generic;//.IList;

namespace Apache.Qpid.Integration.Tests.framework
{
    /// <summary>
    /// A Circuit is the basic test unit against which test cases are to be written. A circuit consists of two 'ends', an
    /// instigating 'publisher' end and a more passive 'receivers' end.
    ///
    /// <p/>Once created, the life-cycle of a circuit may be controlled by <see cref="#start()"/>ing it, or <see cref="#close()"/>ing it.
    /// Once started, the circuit is ready to send messages over. Once closed the circuit can no longer be used.
    ///
    /// <p/>The state of the circuit may be taken with the <see cref="#check()"/> method, and asserted against by the
    /// <see cref="#applyAssertions(System.Collections.Generic.IList)"/> method.
    ///
    /// <p/>There is a default test procedure which may be performed against the circuit. The outline of this procedure is:
    ///
    /// <p/><pre>
    /// Start the circuit.
    /// Send test messages.
    /// Request a status report.
    /// Assert conditions on the publishing end of the circuit.
    /// Assert conditions on the receiving end of the circuit.
    /// Close the circuit.
    /// Pass with no failed assertions or fail with a list of failed assertions.
    /// </pre>
    ///
    /// <p/><table id="crc"><caption>CRC Card</caption>
    /// <tr><th> Responsibilities
    /// <tr><td> Supply the publishing and receiving ends of a test messaging circuit.
    /// <tr><td> Start the circuit running.
    /// <tr><td> Close the circuit down.
    /// <tr><td> Take a reading of the circuits state.
    /// <tr><td> Apply assertions against the circuits state.
    /// <tr><td> Send test messages over the circuit.
    /// <tr><td> Perform the default test procedue on the circuit.
    /// </table>
    /// </summary>
    public interface Circuit
    {
        /// <summary> Gets the interface on the publishing end of the circuit. </summary>
        ///
        /// <return> The publishing end of the circuit. </return>
        Publisher GetPublisher();

        /// <summary> Gets the interface on the receiving end of the circuit. </summary>
        ///
        /// <return> The receiving end of the circuit. </return>
        Receiver GetReceiver();

        /// <summary> Connects and starts the circuit. After this method is called the circuit is ready to send messages. </summary>
        void Start();

        /// <summary>
        /// Checks the test circuit. The effect of this is to gather the circuits state, for both ends of the circuit,
        /// into a report, against which assertions may be checked.
        /// </summary>
        void Check();

        /// <summary> Closes the circuit. All associated resources are closed. </summary>
        void Close();

        /// <summary>
        /// Applied a list of assertions against the test circuit. The <see cref="#check()"/> method should be called before doing
        /// this, to ensure that the circuit has gathered its state into a report to assert against.
        /// </summary>
        ///
        /// <param name="assertions"> The list of assertions to apply to the circuit. </param>
        ///
        /// <return> Any assertions that failed. </return>
        IList<Assertion> ApplyAssertions(IList<Assertion> assertions);

        /// <summary>
        /// Runs the default test procedure against the circuit, and checks that all of the specified assertions hold.
        /// </summary>
        ///
        /// <param name="numMessages"> The number of messages to send using the default test procedure. </param>
        /// <param name="assertions">  The list of assertions to apply. </param>
        ///
        /// <return> Any assertions that failed. </return>
        IList<Assertion> Test(int numMessages, IList<Assertion> assertions);
    }
}