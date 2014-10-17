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
package org.apache.qpid.client;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.transport.TestNetworkConnection;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.url.AMQBindingURL;

public class AMQSession_0_8Test extends QpidTestCase
{
    private AMQConnection _connection;

    public void setUp() throws Exception
    {
        _connection = new MockAMQConnection("amqp://guest:guest@/test?brokerlist='tcp://localhost:5672'");
        NetworkConnection network = new TestNetworkConnection();
        _connection.getProtocolHandler().setNetworkConnection(network);
    }

    public void testQueueNameIsGeneratedOnDeclareQueueWithEmptyQueueName() throws Exception
    {
        final AMQShortString testQueueName = AMQShortString.valueOf("tmp_127_0_0_1_1_1");

        _connection.setConnectionListener(new ConnectionListenerSupport()
        {
            @Override
            public void bytesSent(long count)
            {
                try
                {
                    _connection.getProtocolHandler().methodBodyReceived(1, new QueueDeclareOkBody(testQueueName, 0, 0));
                }
                catch (AMQException e)
                {
                    throw new RuntimeException(e);
                }
            }
        });

        AMQSession_0_8 session = new AMQSession_0_8(_connection, 1, true, 0, 1, 1);

        AMQBindingURL bindingURL = new AMQBindingURL("topic://amq.topic//?routingkey='testTopic'");
        AMQQueue queue = new AMQQueue(bindingURL);

        assertEquals("Unexpected queue name", AMQShortString.EMPTY_STRING, queue.getAMQQueueName());

        session.declareQueue(queue, true);

        assertEquals("Unexpected queue name", testQueueName, queue.getAMQQueueName());
    }
}
