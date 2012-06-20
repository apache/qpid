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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.Queue;

import junit.framework.TestCase;

public class QueueMBeanTest extends TestCase
{
    private static final String QUEUE_NAME = "QUEUE_NAME";
    private static final String QUEUE_DESCRIPTION = "QUEUE_DESCRIPTION";

    private Queue _mockQueue;
    private VirtualHostMBean _mockVirtualHostMBean;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private QueueMBean _queueMBean;

    @Override
    protected void setUp() throws Exception
    {
        _mockQueue = mock(Queue.class);
        _mockVirtualHostMBean = mock(VirtualHostMBean.class);

        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);
        when(_mockVirtualHostMBean.getRegistry()).thenReturn(_mockManagedObjectRegistry);

        _queueMBean = new QueueMBean(_mockQueue, _mockVirtualHostMBean);
    }

    public void testQueueName()
    {
        when(_mockQueue.getName()).thenReturn(QUEUE_NAME);

        assertEquals(QUEUE_NAME, _queueMBean.getName());
    }

    public void testQueueDescription()
    {
        when(_mockQueue.getAttribute(Queue.DESCRIPTION)).thenReturn(QUEUE_DESCRIPTION);

        assertEquals(QUEUE_DESCRIPTION, _queueMBean.getDescription());
    }
}
