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

import org.apache.qpid.client.AMQNoConsumersException;
import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.test.framework.*;

import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * Provides an implementation of the {@link Publisher} interface and wraps a single message producer and consumer on
 * a single controlSession, as a {@link CircuitEnd}. A local publisher also acts as a circuit end, because for a locally
 * located circuit the assertions may be applied directly, there does not need to be any inter process messaging
 * between the publisher and its single circuit end, in order to ascertain its status.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Provide a message producer for sending messages.
 * <tr><td> Provide a message consumer for receiving messages.
 * <tr><td> Provide assertion that the publisher received no exceptions.
 * <tr><td> Provide assertion that the publisher received a no consumers error code.
 * <tr><td> Provide assertion that the publisher received a no route error code.
 * </table>
 */
public class LocalPublisherImpl extends CircuitEndBase implements Publisher
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
    public LocalPublisherImpl(MessageProducer producer, MessageConsumer consumer, Session session,
        MessageMonitor messageMonitor, ExceptionMonitor exceptionMonitor)
    {
        super(producer, consumer, session, messageMonitor, exceptionMonitor);
    }

    /**
     * Creates a circuit end point from the producer, consumer and controlSession in a circuit end base implementation.
     *
     * @param end The circuit end base implementation to take producers and consumers from.
     */
    public LocalPublisherImpl(CircuitEndBase end)
    {
        super(end.getProducer(), end.getConsumer(), end.getSession(), end.getMessageMonitor(), end.getExceptionMonitor());
    }

    /**
     * Provides an assertion that the publisher encountered no exceptions.
     *
     * @return An assertion that the publisher encountered no exceptions.
     */
    public Assertion noExceptionsAssertion()
    {
        return new AssertionBase()
            {
                public boolean apply()
                {
                    boolean passed = true;
                    ExceptionMonitor sessionExceptionMonitor = circuit.getExceptionMonitor();
                    ExceptionMonitor connectionExceptionMonitor = circuit.getConnectionExceptionMonitor();

                    if (!connectionExceptionMonitor.assertNoExceptions())
                    {
                        passed = false;

                        addError("Was expecting no exceptions.\n");
                        addError("Got the following exceptions on the connection, "
                            + circuit.getConnectionExceptionMonitor());
                    }

                    if (!sessionExceptionMonitor.assertNoExceptions())
                    {
                        passed = false;

                        addError("Was expecting no exceptions.\n");
                        addError("Got the following exceptions on the producer, " + circuit.getExceptionMonitor());
                    }

                    return passed;
                }
            };
    }

    /**
     * Provides an assertion that the publisher got a no consumers exception on every message.
     *
     * @return An assertion that the publisher got a no consumers exception on every message.
     */
    public Assertion noConsumersAssertion()
    {
        return new AssertionBase()
            {
                public boolean apply()
                {
                    boolean passed = true;
                    ExceptionMonitor connectionExceptionMonitor = circuit.getConnectionExceptionMonitor();

                    if (!connectionExceptionMonitor.assertOneJMSExceptionWithLinkedCause(AMQNoConsumersException.class))
                    {
                        passed = false;

                        addError("Was expecting linked exception type " + AMQNoConsumersException.class.getName()
                            + " on the connection.\n");
                        addError((connectionExceptionMonitor.size() > 0)
                            ? ("Actually got the following exceptions on the connection, " + connectionExceptionMonitor)
                            : "Got no exceptions on the connection.");
                    }

                    return passed;
                }
            };
    }

    /**
     * Provides an assertion that the publisher got a no rout exception on every message.
     *
     * @return An assertion that the publisher got a no rout exception on every message.
     */
    public Assertion noRouteAssertion()
    {
        return new AssertionBase()
            {
                public boolean apply()
                {
                    boolean passed = true;
                    ExceptionMonitor connectionExceptionMonitor = circuit.getConnectionExceptionMonitor();

                    if (!connectionExceptionMonitor.assertOneJMSExceptionWithLinkedCause(AMQNoRouteException.class))
                    {
                        passed = false;

                        addError("Was expecting linked exception type " + AMQNoRouteException.class.getName()
                            + " on the connection.\n");
                        addError((connectionExceptionMonitor.size() > 0)
                            ? ("Actually got the following exceptions on the connection, " + connectionExceptionMonitor)
                            : "Got no exceptions on the connection.");
                    }

                    return passed;
                }
            };
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
