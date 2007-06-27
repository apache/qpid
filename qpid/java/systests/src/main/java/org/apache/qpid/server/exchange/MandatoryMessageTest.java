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
package org.apache.qpid.server.exchange;

import junit.framework.TestCase;

import org.apache.log4j.NDC;

import org.apache.qpid.client.AMQNoRouteException;
import org.apache.qpid.client.transport.TransportConnection;
import static org.apache.qpid.server.exchange.MessagingTestConfigProperties.*;
import org.apache.qpid.server.registry.ApplicationRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.thebadgerset.junit.extensions.util.ParsedProperties;
import uk.co.thebadgerset.junit.extensions.util.TestContextProperties;

/**
 * MandatoryMessageTest tests for the desired behaviour of mandatory messages. Mandatory messages are a non-JMS
 * feature. A message may be marked with a mandatory delivery flag, which means that a valid route for the message
 * must exist, when it is sent, or when its transaction is committed in the case of transactional messaging. If this
 * is not the case, the broker should return the message with a NO_CONSUMERS code.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Check that a mandatory message is sent succesfully not using transactions when a consumer is connected.
 * <tr><td> Check that a mandatory message is committed succesfully in a transaction when a consumer is connected.
 * <tr><td> Check that a mandatory message results in no route code, not using transactions, when no consumer is
 *          connected.
 * <tr><td> Check that a mandatory message results in no route code, upon transaction commit, when a consumer is
 *          connected.
 * </table>
 */
public class MandatoryMessageTest extends TestCase
{
    /** Used for debugging. */
    private static final Logger log = LoggerFactory.getLogger(MandatoryMessageTest.class);

    /** Used to read the tests configurable properties through. */
    ParsedProperties testProps = TestContextProperties.getInstance(MessagingTestConfigProperties.defaults);

    /** All these tests should have the mandatory flag on. */
    // private boolean mandatoryFlag = testProps.setProperty(IMMEDIATE_PROPNAME, true);
    private boolean mandatoryFlag = testProps.setProperty(MANDATORY_PROPNAME, true);

    /** Check that an mandatory message is sent succesfully not using transactions when a consumer is connected. */
    public void test_QPID_508_MandatoryOkNoTx() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);

        // Send one message with no errors.
        ImmediateMessageTest.PublisherReceiverImpl.testNoExceptions(testProps);
    }

    /** Check that an mandatory message is committed succesfully in a transaction when a consumer is connected. */
    public void test_QPID_508_MandatoryOkTx() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, true);

        // Send one message with no errors.
        ImmediateMessageTest.PublisherReceiverImpl.testNoExceptions(testProps);
    }

    /** Check that an mandatory message results in no route code, not using transactions, when no consumer is connected. */
    public void test_QPID_508_MandatoryFailsNoRouteNoTx() throws Exception
    {
        // Ensure transactional sessions are off.
        testProps.setProperty(TRANSACTED_PROPNAME, false);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        // Send one message and get a linked no consumers exception.
        ImmediateMessageTest.PublisherReceiverImpl.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    /** Check that an mandatory message results in no route code, upon transaction commit, when a consumer is connected. */
    public void test_QPID_508_MandatoryFailsNoRouteTx() throws Exception
    {
        // Ensure transactional sessions are on.
        testProps.setProperty(TRANSACTED_PROPNAME, true);

        // Set up the messaging topology so that only the publishers producer is bound (do not set up the receiver to
        // collect its messages).
        testProps.setProperty(RECEIVER_CONSUMER_BIND_PROPNAME, false);

        // Send one message and get a linked no consumers exception.
        ImmediateMessageTest.PublisherReceiverImpl.testWithAssertions(testProps, AMQNoRouteException.class);
    }

    protected void setUp() throws Exception
    {
        NDC.push(getName());

        // Ensure that the in-vm broker is created.
        TransportConnection.createVMBroker(1);
    }

    protected void tearDown() throws Exception
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
}
