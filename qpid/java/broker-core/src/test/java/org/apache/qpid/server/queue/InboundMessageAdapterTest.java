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
package org.apache.qpid.server.queue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.test.utils.QpidTestCase;

public class InboundMessageAdapterTest extends QpidTestCase
{
    private ServerMessage<?> _mockMessage;
    private QueueEntry _mockQueueEntry;
    private InboundMessageAdapter _inboundMessageAdapter;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _mockMessage = mock(ServerMessage.class);
        _mockQueueEntry = mock(QueueEntry.class);
        when(_mockQueueEntry.getMessage()).thenReturn(_mockMessage);

        _inboundMessageAdapter = new InboundMessageAdapter(_mockQueueEntry);
    }

    public void testGetRoutingKey() throws Exception
    {
        String routingKey = getTestName();
        when(_mockMessage.getRoutingKey()).thenReturn(routingKey);

        assertEquals("Unexpected value for routing key", routingKey, _inboundMessageAdapter.getRoutingKey());
    }


    public void testGetMessageHeader() throws Exception
    {
        AMQMessageHeader mockMessageHeader = mock(AMQMessageHeader.class);
        when(_mockQueueEntry.getMessageHeader()).thenReturn(mockMessageHeader);

        assertSame("unexpected message header", mockMessageHeader, _inboundMessageAdapter.getMessageHeader());
    }

    public void testIsRedelivered() throws Exception
    {
        when(_mockQueueEntry.isRedelivered()).thenReturn(true);
        assertTrue("unexpected isRedelivered value", _inboundMessageAdapter.isRedelivered());

        when(_mockQueueEntry.isRedelivered()).thenReturn(false);
        assertFalse("unexpected isRedelivered value", _inboundMessageAdapter.isRedelivered());
    }

    public void testIsPersistent() throws Exception
    {
        when(_mockQueueEntry.isPersistent()).thenReturn(true);
        assertTrue("unexpected isPersistent value", _inboundMessageAdapter.isPersistent());

        when(_mockQueueEntry.isPersistent()).thenReturn(false);
        assertFalse("unexpected isPersistent value", _inboundMessageAdapter.isPersistent());
    }

    public void testGetSize() throws Exception
    {
        long size = 32526215;
        when(_mockQueueEntry.getSize()).thenReturn(size);
        assertEquals("unexpected getSize value", size, _inboundMessageAdapter.getSize());
    }
}
