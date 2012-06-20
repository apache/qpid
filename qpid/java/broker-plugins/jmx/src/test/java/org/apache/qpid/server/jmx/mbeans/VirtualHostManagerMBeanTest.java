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
 *
 */
package org.apache.qpid.server.jmx.mbeans;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.qpid.server.jmx.ManagedObjectRegistry;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.queue.AMQQueueFactory;

public class VirtualHostManagerMBeanTest extends TestCase
{
    private static final String TEST_QUEUE_NAME = "QUEUE_NAME";
    private static final String TEST_OWNER = "OWNER";
    private static final String TEST_DESCRIPTION = "DESCRIPTION";

    private static final Map<String, Object> EMPTY_ARGUMENT_MAP = Collections.emptyMap();

    private VirtualHost _mockVirtualHost;
    private ManagedObjectRegistry _mockManagedObjectRegistry;
    private VirtualHostManagerMBean _virtualHostManagerMBean;

    @Override
    protected void setUp() throws Exception
    {
        _mockVirtualHost = mock(VirtualHost.class);
        _mockManagedObjectRegistry = mock(ManagedObjectRegistry.class);

        _virtualHostManagerMBean = new VirtualHostManagerMBean(new VirtualHostMBean(_mockVirtualHost, _mockManagedObjectRegistry));
    }

    public void testCreateQueueWithNoOwner() throws Exception
    {
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, null, true);

        verify(_mockVirtualHost).createQueue(TEST_QUEUE_NAME, State.ACTIVE, true, false, LifetimePolicy.PERMANENT, 0, EMPTY_ARGUMENT_MAP);
    }

    /**
     * Some users have been abusing the owner parameter as a description.  Decision has been taken to map this parameter
     * through to the description field (if the description field is passed, the owner is discarded).
     */
    public void testCreateQueueWithOwnerMappedThroughToDescription() throws Exception
    {
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, TEST_OWNER, true);

        Map<String, Object> expectedArguments = Collections.singletonMap(AMQQueueFactory.X_QPID_DESCRIPTION, (Object)TEST_OWNER);
        verify(_mockVirtualHost).createQueue(TEST_QUEUE_NAME, State.ACTIVE, true, false, LifetimePolicy.PERMANENT, 0, expectedArguments);
    }

    public void testCreateQueueWithOwnerAndDescriptionDiscardsOwner() throws Exception
    {
        Map<String, Object> arguments = Collections.singletonMap(AMQQueueFactory.X_QPID_DESCRIPTION, (Object)TEST_DESCRIPTION);
        _virtualHostManagerMBean.createNewQueue(TEST_QUEUE_NAME, TEST_OWNER, true, arguments);

        Map<String, Object> expectedArguments = Collections.singletonMap(AMQQueueFactory.X_QPID_DESCRIPTION, (Object)TEST_DESCRIPTION);
        verify(_mockVirtualHost).createQueue(TEST_QUEUE_NAME, State.ACTIVE, true, false, LifetimePolicy.PERMANENT, 0, expectedArguments);
    }

}
