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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.store.TransactionLogResource;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.test.utils.QpidTestCase;

public class PriorityQueueListTest extends QpidTestCase
{
    private static final byte[] PRIORITIES = {4, 5, 5, 4};
    PriorityQueueList _list;

    private QueueEntry _priority4message1;
    private QueueEntry _priority4message2;
    private QueueEntry _priority5message1;
    private QueueEntry _priority5message2;

    protected void setUp()
    {
        BrokerTestHelper.setUp();
        QueueEntry[] entries = new QueueEntry[PRIORITIES.length];
        Map<String,Object> queueAttributes = new HashMap<String, Object>();
        queueAttributes.put(Queue.ID, UUID.randomUUID());
        queueAttributes.put(Queue.NAME, getName());
        queueAttributes.put(PriorityQueue.PRIORITIES, 10);
        final VirtualHostImpl virtualHost = mock(VirtualHostImpl.class);
        when(virtualHost.getSecurityManager()).thenReturn(mock(SecurityManager.class));
        when(virtualHost.getEventLogger()).thenReturn(new EventLogger());
        ConfiguredObjectFactory factory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        when(virtualHost.getObjectFactory()).thenReturn(factory);
        when(virtualHost.getModel()).thenReturn(factory.getModel());
        TaskExecutor taskExecutor = CurrentThreadTaskExecutor.newStartedInstance();
        when(virtualHost.getTaskExecutor()).thenReturn(taskExecutor);
        when(virtualHost.getChildExecutor()).thenReturn(taskExecutor);
        PriorityQueueImpl queue = new PriorityQueueImpl(queueAttributes, virtualHost);
        queue.open();
        _list = queue.getEntries();

        for (int i = 0; i < PRIORITIES.length; i++)
        {
            ServerMessage<?> message = mock(ServerMessage.class);
            AMQMessageHeader header = mock(AMQMessageHeader.class);
            @SuppressWarnings({ "rawtypes", "unchecked" })
            MessageReference<ServerMessage> ref = mock(MessageReference.class);

            when(message.getMessageHeader()).thenReturn(header);
            when(message.newReference()).thenReturn(ref);
            when(message.newReference(any(TransactionLogResource.class))).thenReturn(ref);
            when(ref.getMessage()).thenReturn(message);
            when(header.getPriority()).thenReturn(PRIORITIES[i]);

            entries[i] = _list.add(message, null);
        }

        _priority4message1 = entries[0];
        _priority4message2 = entries[3];
        _priority5message1 = entries[1];
        _priority5message2 = entries[2];
    }

    @Override
    public void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
        }
        finally
        {
            BrokerTestHelper.tearDown();
        }
    }

    public void testPriorityQueueEntryCompareToItself()
    {
        //check messages compare to themselves properly
        assertEquals("message should compare 'equal' to itself",
                0, _priority4message1.compareTo(_priority4message1));

        assertEquals("message should compare 'equal' to itself",
                0, _priority5message2.compareTo(_priority5message2));
    }

    public void testPriorityQueueEntryCompareToSamePriority()
    {
        //check messages with the same priority are ordered properly
        assertEquals("first message should be 'earlier' than second message of the same priority",
                -1, _priority4message1.compareTo(_priority4message2));

        assertEquals("first message should be 'earlier' than second message of the same priority",
                -1, _priority5message1.compareTo(_priority5message2));

        //and in reverse
        assertEquals("second message should be 'later' than first message of the same priority",
                1, _priority4message2.compareTo(_priority4message1));

        assertEquals("second message should be 'later' than first message of the same priority",
                1, _priority5message2.compareTo(_priority5message1));
    }

    public void testPriorityQueueEntryCompareToDifferentPriority()
    {
        //check messages with higher priority are ordered 'earlier' than those with lower priority
        assertEquals("first message with priority 5 should be 'earlier' than first message of priority 4",
                -1, _priority5message1.compareTo(_priority4message1));
        assertEquals("first message with priority 5 should be 'earlier' than second message of priority 4",
                -1, _priority5message1.compareTo(_priority4message2));

        assertEquals("second message with priority 5 should be 'earlier' than first message of priority 4",
                -1, _priority5message2.compareTo(_priority4message1));
        assertEquals("second message with priority 5 should be 'earlier' than second message of priority 4",
                -1, _priority5message2.compareTo(_priority4message2));

        //and in reverse
        assertEquals("first message with priority 4 should be 'later' than first message of priority 5",
                1, _priority4message1.compareTo(_priority5message1));
        assertEquals("first message with priority 4 should be 'later' than second message of priority 5",
                1, _priority4message1.compareTo(_priority5message2));

        assertEquals("second message with priority 4 should be 'later' than first message of priority 5",
                1, _priority4message2.compareTo(_priority5message1));
        assertEquals("second message with priority 4 should be 'later' than second message of priority 5",
                1, _priority4message2.compareTo(_priority5message2));
    }
}
