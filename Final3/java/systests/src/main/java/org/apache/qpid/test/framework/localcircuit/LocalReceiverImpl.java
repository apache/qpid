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
package org.apache.qpid.test.framework.localcircuit;

import org.apache.qpid.test.framework.*;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * Provides an implementation of the {@link Receiver} interface that wraps a single message producer and consumer on
 * a single controlSession, as a {@link CircuitEnd}. A local receiver also acts as a circuit end, because for a locally
 * located circuit the assertions may be applied directly, there does not need to be any inter process messaging
 * between the publisher and its single circuit end, in order to ascertain its status.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a message producer for sending messages.
 * <tr><td> Provide a message consumer for receiving messages.
 * <tr><td> Provide assertion that the receivers received no exceptions.
 * <tr><td> Provide assertion that the receivers received all test messages sent to it.
 * </table>
 */
public class LocalReceiverImpl extends CircuitEndBase implements Receiver
{
    /** Holds a reference to the containing circuit. */
    private LocalCircuitImpl circuit;

    /**
     * Creates a circuit end point on the specified producer, consumer and controlSession.
     *
     * @param producer The message producer for the circuit end point.
     * @param consumer The message consumer for the circuit end point.
     * @param session  The controlSession for the circuit end point.
     */
    public LocalReceiverImpl(MessageProducer producer, MessageConsumer consumer, Session session,
        MessageMonitor messageMonitor, ExceptionMonitor exceptionMonitor)
    {
        super(producer, consumer, session, messageMonitor, exceptionMonitor);
    }

    /**
     * Creates a circuit end point from the producer, consumer and controlSession in a circuit end base implementation.
     *
     * @param end The circuit end base implementation to take producers and consumers from.
     */
    public LocalReceiverImpl(CircuitEndBase end)
    {
        super(end.getProducer(), end.getConsumer(), end.getSession(), end.getMessageMonitor(), end.getExceptionMonitor());
    }

    /**
     * Provides an assertion that the receivers encountered no exceptions.
     *
     * @return An assertion that the receivers encountered no exceptions.
     */
    public Assertion noExceptionsAssertion()
    {
        return null;
    }

    /**
     * Provides an assertion that the receivers got all messages that were sent to it.
     *
     * @return An assertion that the receivers got all messages that were sent to it.
     */
    public Assertion allMessagesAssertion()
    {
        return null;
    }

    /**
     * Sets the contianing circuit.
     *
     * @param circuit The containing circuit.
     */
    public void setCircuit(LocalCircuitImpl circuit)
    {
        this.circuit = circuit;
    }
}
