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
package org.apache.qpid.server.plugin;

import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.test.utils.QpidTestCase;
import org.mockito.Mockito;

public class ProtocolEngineCreatorComparatorTest extends QpidTestCase
{
    public void testProtocolEngineCreatorComparator() throws Exception
    {
        final ProtocolEngineCreatorComparator comparator = new ProtocolEngineCreatorComparator();

        final ProtocolEngineCreator amqp_0_8 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_8);
        final ProtocolEngineCreator amqp_0_9 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_9);
        final ProtocolEngineCreator amqp_0_9_1 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_9_1);
        final ProtocolEngineCreator amqp_0_10 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_0_10);
        final ProtocolEngineCreator amqp_1_0 = createAMQPProtocolEngineCreatorMock(Protocol.AMQP_1_0);

        assertTrue(comparator.compare(amqp_0_8,amqp_0_9) < 0);
        assertTrue(comparator.compare(amqp_0_9,amqp_0_9_1) < 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_10) < 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_1_0) < 0);

        assertTrue(comparator.compare(amqp_0_9,amqp_0_8) > 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_9) > 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_0_9_1) > 0);
        assertTrue(comparator.compare(amqp_1_0,amqp_0_10) > 0);

        assertTrue(comparator.compare(amqp_0_8,amqp_0_8) == 0);
        assertTrue(comparator.compare(amqp_0_9,amqp_0_9) == 0);
        assertTrue(comparator.compare(amqp_0_9_1,amqp_0_9_1) == 0);
        assertTrue(comparator.compare(amqp_0_10,amqp_0_10) == 0);
        assertTrue(comparator.compare(amqp_1_0,amqp_1_0) == 0);
    }

    private ProtocolEngineCreator createAMQPProtocolEngineCreatorMock(Protocol protocol)
    {
        final ProtocolEngineCreator protocolMock = Mockito.mock(ProtocolEngineCreator.class);
        Mockito.when(protocolMock.getVersion()).thenReturn(protocol);
        return protocolMock;
    }
}
