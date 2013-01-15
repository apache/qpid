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
package org.apache.qpid.server.subscription;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.flow.WindowCreditManager;
import org.apache.qpid.server.logging.UnitTestMessageLogger;
import org.apache.qpid.server.logging.actors.GenericActor;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.protocol.ProtocolEngine_0_10;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.server.transport.ServerSession;
import org.apache.qpid.server.transport.ServerSessionDelegate;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.Binary;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.TestNetworkConnection;

public class SubscriptionFactoryImplTest extends QpidTestCase
{
    private AMQChannel _channel;
    private AMQProtocolSession _session;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        BrokerTestHelper.setUp();
        _channel = BrokerTestHelper.createChannel();
        _session = _channel.getProtocolSession();
        GenericActor.setDefaultMessageLogger(new UnitTestMessageLogger(false));
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            if (_channel != null)
            {
                _channel.getVirtualHost().close();
            }
        }
        finally
        {
            BrokerTestHelper.tearDown();
            super.tearDown();
        }
    }

    /**
     * Tests that while creating Subscriptions of various types, the 
     * ID numbers assigned are allocated from a common sequence
     * (in increasing order).
     */
    public void testDifferingSubscriptionTypesShareCommonIdNumberingSequence() throws Exception
    {
        //create a No-Ack subscription, get the first Subscription ID
        long previousId = 0;
        Subscription noAckSub = SubscriptionFactoryImpl.INSTANCE.createSubscription(1, _session, new AMQShortString("1"), false, null, false, _channel.getCreditManager());
        previousId = noAckSub.getSubscriptionID();

        //create an ack subscription, verify the next Subscription ID is used
        Subscription ackSub = SubscriptionFactoryImpl.INSTANCE.createSubscription(1, _session, new AMQShortString("1"), true, null, false, _channel.getCreditManager());
        assertEquals("Unexpected Subscription ID allocated", previousId + 1, ackSub.getSubscriptionID());
        previousId = ackSub.getSubscriptionID();

        //create a browser subscription
        FieldTable filters = new FieldTable();
        filters.put(AMQPFilterTypes.NO_CONSUME.getValue(), true);
        Subscription browerSub = SubscriptionFactoryImpl.INSTANCE.createSubscription(1, _session, new AMQShortString("1"), true, null, false, _channel.getCreditManager());
        assertEquals("Unexpected Subscription ID allocated", previousId + 1, browerSub.getSubscriptionID());
        previousId = browerSub.getSubscriptionID();

        //create an BasicGet NoAck subscription
        Subscription getNoAckSub = SubscriptionFactoryImpl.INSTANCE.createBasicGetNoAckSubscription(_channel, _session, new AMQShortString("1"), null, false,
                _channel.getCreditManager(),_channel.getClientDeliveryMethod(), _channel.getRecordDeliveryMethod());
        assertEquals("Unexpected Subscription ID allocated", previousId + 1, getNoAckSub.getSubscriptionID());
        previousId = getNoAckSub.getSubscriptionID();

        //create a 0-10 subscription
        ServerConnection conn = new ServerConnection(1);
        ProtocolEngine_0_10 engine = new ProtocolEngine_0_10(conn, new TestNetworkConnection());
        conn.setVirtualHost(_session.getVirtualHost());
        ServerSessionDelegate sesDel = new ServerSessionDelegate();
        Binary name = new Binary(new byte[]{new Byte("1")});
        ServerSession session = new ServerSession(conn, sesDel, name, 0);

        Subscription sub_0_10 = SubscriptionFactoryImpl.INSTANCE.createSubscription(session, "1", MessageAcceptMode.EXPLICIT,
                MessageAcquireMode.PRE_ACQUIRED, MessageFlowMode.WINDOW, new WindowCreditManager(), null, null);
        assertEquals("Unexpected Subscription ID allocated", previousId + 1, sub_0_10.getSubscriptionID());
    }

}
