/*
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
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.OperationsException;
import javax.management.openmbean.CompositeDataSupport;

import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.apache.qpid.management.common.mbeans.ManagedQueue;
import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.jmx.mbeans.QueueMBean.GetMessageVisitor;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.NotificationCheck;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.test.utils.QpidTestCase;

public class QueueMBeanTest extends QpidTestCase
{
    private static final String QUEUE_NAME = "QUEUE_NAME";
    private static final String QUEUE_DESCRIPTION = "QUEUE_DESCRIPTION";
    private static final String  QUEUE_TYPE = "QUEUE_TYPE";
    private static final String QUEUE_ALTERNATE_EXCHANGE = "QUEUE_ALTERNATE_EXCHANGE";

    private Queue _mockQueue;
    private VirtualHostMBean _mockVirtualHostMBean;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private QueueMBean _queueMBean;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        _mockQueue = mock(Queue.class);
        when(_mockQueue.getName()).thenReturn(QUEUE_NAME);
        _mockVirtualHostMBean = mock(VirtualHostMBean.class);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        when(_mockVirtualHostMBean.getRegistry()).thenReturn(_mockManagedObjectRegistry);

        _queueMBean = new QueueMBean(_mockQueue, _mockVirtualHostMBean);
    }

    public void testQueueName()
    {
        assertEquals(QUEUE_NAME, _queueMBean.getName());
    }

    /**********  Statistics **********/

    public void testGetMessageCount() throws Exception
    {
        when(_mockQueue.getQueueDepthMessages()).thenReturn(1000);
        assertStatistic("messageCount", 1000);
    }

    public void testGetReceivedMessageCount() throws Exception
    {
        when(_mockQueue.getTotalEnqueuedMessages()).thenReturn(1000l);
        assertStatistic("receivedMessageCount", 1000l);
    }

    public void testQueueDepth() throws Exception
    {
        when(_mockQueue.getQueueDepthBytes()).thenReturn(4096l);
        assertStatistic("queueDepth", 4096l);
    }

    public void testActiveConsumerCount() throws Exception
    {
        when(_mockQueue.getConsumerCountWithCredit()).thenReturn(3);
        assertStatistic("activeConsumerCount", 3);
    }

    public void testConsumerCount() throws Exception
    {
        when(_mockQueue.getConsumerCount()).thenReturn(3);
        assertStatistic("consumerCount", 3);
    }

    public void testOldestMessageAge() throws Exception
    {
        when(_mockQueue.getOldestMessageAge()).thenReturn(3l);
        assertStatistic("oldestMessageAge", 3l);
    }

    /**********  Simple Attributes **********/

    public void testGetQueueDescription() throws Exception
    {
        when(_mockQueue.getDescription()).thenReturn(QUEUE_DESCRIPTION);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "description", QUEUE_DESCRIPTION);
    }

    public void testSetQueueDescription() throws Exception
    {
        when(_mockQueue.getDescription()).thenReturn("descriptionold");

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "description", "descriptionnew");

        verify(_mockQueue).setAttribute(Queue.DESCRIPTION, "descriptionold", "descriptionnew");
    }

    public void testQueueType() throws Exception
    {
        when(_mockQueue.getType()).thenReturn(QUEUE_TYPE);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "queueType", QUEUE_TYPE);
    }

    public void testMaximumDeliveryCount() throws Exception
    {
        when(_mockQueue.getMaximumDeliveryAttempts()).thenReturn(5);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "maximumDeliveryCount", 5);
    }

    public void testOwner() throws Exception
    {
        when(_mockQueue.getOwner()).thenReturn("testOwner");
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "owner", "testOwner");
    }

    public void testIsDurable() throws Exception
    {
        when(_mockQueue.isDurable()).thenReturn(true);
        assertTrue(_queueMBean.isDurable());
    }

    public void testIsNotDurable() throws Exception
    {
        when(_mockQueue.isDurable()).thenReturn(false);
        assertFalse(_queueMBean.isDurable());
    }

    public void testIsAutoDelete() throws Exception
    {
        when(_mockQueue.getLifetimePolicy()).thenReturn(LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS);
        assertTrue(_queueMBean.isAutoDelete());
    }

    public void testIsNotAutoDelete() throws Exception
    {
        when(_mockQueue.getLifetimePolicy()).thenReturn(LifetimePolicy.PERMANENT);
        assertFalse(_queueMBean.isAutoDelete());
    }

    public void testGetMaximumMessageAge() throws Exception
    {
        when(_mockQueue.getAlertThresholdMessageAge()).thenReturn(10000l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "maximumMessageAge", 10000l);
    }

    public void testSetMaximumMessageAge() throws Exception
    {
        when(_mockQueue.getAlertThresholdMessageAge()).thenReturn(1000l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "maximumMessageAge", 10000l);

        verify(_mockQueue).setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_AGE, 1000l, 10000l);
    }

    public void testGetMaximumMessageSize() throws Exception
    {
        when(_mockQueue.getAlertThresholdMessageSize()).thenReturn(1024l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "maximumMessageSize", 1024l);
    }

    public void testSetMaximumMessageSize() throws Exception
    {
        when(_mockQueue.getAlertThresholdMessageSize()).thenReturn(1024l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "maximumMessageSize", 2048l);

        verify(_mockQueue).setAttribute(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, 1024l, 2048l);
    }

    public void testGetMaximumMessageCount() throws Exception
    {
        when(_mockQueue.getAlertThresholdQueueDepthMessages()).thenReturn(5000l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "maximumMessageCount", 5000l);
    }

    public void testSetMaximumMessageCount() throws Exception
    {
        when(_mockQueue.getAlertThresholdQueueDepthMessages()).thenReturn(4000l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "maximumMessageCount", 5000l);

        verify(_mockQueue).setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, 4000l, 5000l);
    }

    public void testGetMaximumQueueDepth() throws Exception
    {
        when(_mockQueue.getAlertThresholdQueueDepthBytes()).thenReturn(1048576l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "maximumQueueDepth", 1048576l);
    }

    public void testSetMaximumQueueDepth() throws Exception
    {
        when(_mockQueue.getAlertThresholdQueueDepthBytes()).thenReturn(1048576l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "maximumQueueDepth", 2097152l);

        verify(_mockQueue).setAttribute(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, 1048576l, 2097152l);
    }

    public void testGetCapacity() throws Exception
    {
        when(_mockQueue.getQueueFlowControlSizeBytes()).thenReturn(1048576l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "capacity", 1048576l);
    }

    public void testSetCapacity() throws Exception
    {
        when(_mockQueue.getQueueFlowControlSizeBytes()).thenReturn(1048576l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "capacity", 2097152l);

        verify(_mockQueue).setAttribute(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, 1048576l, 2097152l);
    }

    public void testGetFlowResumeCapacity() throws Exception
    {
        when(_mockQueue.getQueueFlowResumeSizeBytes()).thenReturn(1048576l);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "flowResumeCapacity", 1048576l);
    }

    public void testSetFlowResumeCapacity() throws Exception
    {
        when(_mockQueue.getQueueFlowResumeSizeBytes()).thenReturn(1048576l);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "flowResumeCapacity", 2097152l);

        verify(_mockQueue).setAttribute(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, 1048576l, 2097152l);
    }


    /**********  Other attributes **********/


    public void testIsExclusive() throws Exception
    {
        when(_mockQueue.getExclusive()).thenReturn(ExclusivityPolicy.CONTAINER);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "exclusive", true);
    }

    public void testIsNotExclusive() throws Exception
    {
        when(_mockQueue.getExclusive()).thenReturn(ExclusivityPolicy.NONE);
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, "exclusive", false);
    }

    public void testSetExclusive() throws Exception
    {
        when(_mockQueue.getExclusive()).thenReturn(ExclusivityPolicy.NONE);

        MBeanTestUtils.setMBeanAttribute(_queueMBean, "exclusive", Boolean.TRUE);

        verify(_mockQueue).setAttribute(Queue.EXCLUSIVE, ExclusivityPolicy.NONE, ExclusivityPolicy.CONTAINER);

    }

    public void testGetAlternateExchange()
    {
        Exchange mockAlternateExchange = mock(Exchange.class);
        when(mockAlternateExchange.getName()).thenReturn(QUEUE_ALTERNATE_EXCHANGE);

        when(_mockQueue.getAlternateExchange()).thenReturn(mockAlternateExchange);

        assertEquals(QUEUE_ALTERNATE_EXCHANGE, _queueMBean.getAlternateExchange());
    }

    public void testGetAlternateExchangeWhenQueueHasNone()
    {
        when(_mockQueue.getAlternateExchange()).thenReturn(null);

        assertNull(_queueMBean.getAlternateExchange());
    }

    public void testSetAlternateExchange() throws Exception
    {
        Exchange mockExchange1 = mock(Exchange.class);
        when(mockExchange1.getName()).thenReturn("exchange1");

        Exchange mockExchange2 = mock(Exchange.class);
        when(mockExchange2.getName()).thenReturn("exchange2");

        Exchange mockExchange3 = mock(Exchange.class);
        when(mockExchange3.getName()).thenReturn("exchange3");

        VirtualHost mockVirtualHost = mock(VirtualHost.class);
        when(mockVirtualHost.getExchanges()).thenReturn(Arrays.asList(new Exchange[] {mockExchange1, mockExchange2, mockExchange3}));
        when(_mockQueue.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);

        _queueMBean.setAlternateExchange("exchange2");
        verify(_mockQueue).setAttributes(Collections.<String,Object>singletonMap(Queue.ALTERNATE_EXCHANGE, "exchange2"));
    }

    public void testSetAlternateExchangeWithUnknownExchangeName() throws Exception
    {
        Exchange mockExchange = mock(Exchange.class);
        when(mockExchange.getName()).thenReturn("exchange1");

        VirtualHost mockVirtualHost = mock(VirtualHost.class);
        when(mockVirtualHost.getExchanges()).thenReturn(Collections.singletonList(mockExchange));
        when(_mockQueue.getParent(VirtualHost.class)).thenReturn(mockVirtualHost);
        doThrow(new IllegalArgumentException()).when(_mockQueue).setAttributes(
                eq(Collections.<String, Object>singletonMap(Queue.ALTERNATE_EXCHANGE, "notknown")));
        try
        {
            _queueMBean.setAlternateExchange("notknown");
            fail("Exception not thrown");
        }
        catch(OperationsException oe)
        {
            // PASS
        }
    }

    public void testRemoveAlternateExchange() throws Exception
    {
        _queueMBean.setAlternateExchange("");
        verify(_mockQueue).setAttributes(Collections.singletonMap(Queue.ALTERNATE_EXCHANGE, null));
    }

    /**********  Operations **********/

    /**********  Notifications **********/

    public void testNotificationListenerCalled() throws Exception
    {
        NotificationListener listener = mock(NotificationListener.class);
        _queueMBean.addNotificationListener(listener, null, null);

        NotificationCheck notification = mock(NotificationCheck.class);
        String notificationMsg = "Test notification message";

        _queueMBean.notifyClients(notification, _mockQueue, notificationMsg);
        verify(listener).handleNotification(isNotificationWithMessage(notificationMsg),
                                            isNull());
    }

    public void testAddRemoveNotificationListener() throws Exception
    {
        NotificationListener listener1 = mock(NotificationListener.class);
        _queueMBean.addNotificationListener(listener1, null, null);
        _queueMBean.removeNotificationListener(listener1);
    }

    public void testRemoveUnknownNotificationListener() throws Exception
    {
        NotificationListener listener1 = mock(NotificationListener.class);
        try
        {
            _queueMBean.removeNotificationListener(listener1);
            fail("Exception not thrown");
        }
        catch (ListenerNotFoundException e)
        {
            // PASS
        }
    }

    private Notification isNotificationWithMessage(final String expectedMessage)
    {
        return argThat( new ArgumentMatcher<Notification>()
        {
            @Override
            public boolean matches(Object argument)
            {
                Notification actual = (Notification) argument;
                return actual.getMessage().endsWith(expectedMessage);
            }
        });
    }

    private void assertStatistic(String jmxAttributeName, Object expectedValue) throws Exception
    {
        MBeanTestUtils.assertMBeanAttribute(_queueMBean, jmxAttributeName, expectedValue);
    }

    public void testViewMessageContent() throws Exception
    {
        viewMessageContentTestImpl(16L, 1000, 1000);
    }

    public void testViewMessageContentWithMissingPayload() throws Exception
    {
        viewMessageContentTestImpl(16L, 1000, 0);
    }

    private void viewMessageContentTestImpl(final long messageNumber,
                                       final int messageSize,
                                       final int messageContentSize) throws Exception
    {
        final byte[] content = new byte[messageContentSize];

        //mock message and queue entry to return a given message size, and have a given content
        final ServerMessage<?> serverMessage = mock(ServerMessage.class);
        when(serverMessage.getMessageNumber()).thenReturn(messageNumber);
        when(serverMessage.getSize()).thenReturn((long)messageSize);
        doAnswer(new Answer<Object>()
        {
            public Object answer(InvocationOnMock invocation)
            {
                Object[] args = invocation.getArguments();

                //verify the arg types / expected values
                assertEquals(2, args.length);
                assertTrue(args[0] instanceof ByteBuffer);
                assertTrue(args[1] instanceof Integer);

                ByteBuffer dest = (ByteBuffer) args[0];
                int offset = (Integer) args[1];
                assertEquals(0, offset);

                dest.put(content);
                return messageContentSize;
            }
        }).when(serverMessage).getContent(Matchers.any(ByteBuffer.class), Matchers.anyInt());

        final QueueEntry entry = mock(QueueEntry.class);
        when(entry.getMessage()).thenReturn(serverMessage);

        //mock the queue.visit() method to ensure we match the mock message
        doAnswer(new Answer<Object>()
        {
            public Object answer(InvocationOnMock invocation)
            {
                Object[] args = invocation.getArguments();
                GetMessageVisitor visitor = (GetMessageVisitor) args[0];
                visitor.visit(entry);
                return null;
            }
        }).when(_mockQueue).visit(Matchers.any(GetMessageVisitor.class));

        //now retrieve the content and verify its size
        CompositeDataSupport comp = (CompositeDataSupport) _queueMBean.viewMessageContent(messageNumber);
        assertNotNull(comp);
        byte[] data = (byte[]) comp.get(ManagedQueue.CONTENT);
        assertEquals(messageSize, data.length);
    }

    public void testGetMessageGroupKey()
    {
        when(_mockQueue.getMessageGroupKey()).thenReturn(getTestName());
        assertEquals("Unexpected message group key", getTestName(), _queueMBean.getMessageGroupKey());
    }

    public void testIsSharedMessageGroup()
    {
        when(_mockQueue.isMessageGroupSharedGroups()).thenReturn(true);
        assertEquals("Unexpected message group sharing", true, _queueMBean.isMessageGroupSharedGroups());
    }
}
