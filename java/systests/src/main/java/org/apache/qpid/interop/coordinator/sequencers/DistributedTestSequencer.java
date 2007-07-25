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
package org.apache.qpid.interop.coordinator.sequencers;

import org.apache.qpid.interop.coordinator.TestClientDetails;
import org.apache.qpid.util.ConversationFactory;

import java.util.List;

/**
 * A DistributedTestSequencer is a test sequencer that coordinates activity amongst many
 * {@link org.apache.qpid.interop.testclient.TestClient}s.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Accept notification of test case participants.
 * <tr><td> Accept JMS Connection to carry out the coordination over.
 * <tr><td> Coordinate a test sequence amongst participants. <td> {@link ConversationFactory}
 * </table>
 */
public interface DistributedTestSequencer extends TestCaseSequencer
{
    /**
     * Sets the sender test client to coordinate the test with.
     *
     * @param sender The contact details of the sending client in the test.
     */
    public void setSender(TestClientDetails sender);

    /**
     * Sets the receiving test client to coordinate the test with.
     *
     * @param receiver The contact details of the sending client in the test.
     */
    public void setReceiver(TestClientDetails receiver);

    /**
     * Supplies the sending test client.
     *
     * @return The sending test client.
     */
    public TestClientDetails getSender();

    /**
     * Supplies the receiving test client.
     *
     * @return The receiving test client.
     */
    public List<TestClientDetails> getReceivers();

    /**
     * Accepts the conversation factory over which to hold the test coordinating conversation.
     *
     * @param conversationFactory The conversation factory to coordinate the test over.
     */
    public void setConversationFactory(ConversationFactory conversationFactory);
}
