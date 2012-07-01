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
package org.apache.qpid.server.model;

import java.util.UUID;

import org.apache.qpid.test.utils.QpidTestCase;

public class UUIDGeneratorTest extends QpidTestCase
{
    private static final String VIRTUAL_HOST_NAME_1 = "virtualHost1";
    private static final String VIRTUAL_HOST_NAME_2 = "virtualHost2";
    private static final String QUEUE_NAME_1 = "queue1";
    private static final String QUEUE_NAME_2 = "queue2";
    private static final String EXCHANGE_NAME_1 = "exchange1";
    private static final String EXCHANGE_NAME_2 = "exchange2";
    private static final String BINDING_KEY_1 = "bindingKey1";
    private static final String BINDING_KEY_2 = "bindingKey2";

    public void testDifferentObjectTypeReturnDifferentIdFromSameValues() throws Exception
    {
        UUID id1 = UUIDGenerator.generateQueueUUID("name", "vhost");
        UUID id2 = UUIDGenerator.generateExchangeUUID("name", "vhost");
        UUID id3 = UUIDGenerator.generateBindingUUID("name", "name", "name", "vhost");

        assertFalse("IDs should not be equal", id1.equals(id2));
        assertFalse("IDs should not be equal", id2.equals(id3));
        assertFalse("IDs should not be equal", id1.equals(id3));
    }

    public void testRepeatedQueueIdGenerationIsDeterministic() throws Exception
    {
        UUID queueIdIteration1 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID queueIdIteration2 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_1, VIRTUAL_HOST_NAME_1);
        assertEquals("Queue IDs should be equal", queueIdIteration1, queueIdIteration2);
    }

    public void testRepeatedExchangeIdGenerationIsDeterministic() throws Exception
    {
        UUID exchangeIdIteration1 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID exchangeIdIteration2 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_1, VIRTUAL_HOST_NAME_1);
        assertEquals("Exchange IDs should be equal", exchangeIdIteration1, exchangeIdIteration2);
    }

    public void testRepeatedBindingIdGenerationIsDeterministic() throws Exception
    {
        UUID bindingIdIteration1 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_1, VIRTUAL_HOST_NAME_1);
        UUID bindingIdIteration2 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_1, VIRTUAL_HOST_NAME_1);
        assertEquals("Binding IDs should be equal", bindingIdIteration1, bindingIdIteration2);
    }

    public void testDifferentQueueNameGivesDifferentQueueId() throws Exception
    {
        UUID queue1 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID queue2 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_2, VIRTUAL_HOST_NAME_1);
        assertFalse("Queue IDs should not be equal", queue1.equals(queue2));
    }

    public void testDifferentExchangeNameGivesDifferentExchangeId() throws Exception
    {
        UUID exchange1 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID exchange2 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_2, VIRTUAL_HOST_NAME_1);
        assertFalse("Exchange IDs should not be equal", exchange1.equals(exchange2));
    }

    public void testDifferentBindingNameGivesDifferentBindingId() throws Exception
    {
        UUID binding1 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_1, VIRTUAL_HOST_NAME_1);
        UUID binding2 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_2, VIRTUAL_HOST_NAME_1);
        assertFalse("Binding IDs should not be equal", binding1.equals(binding2));
    }

    public void testDifferentVirtualHostNameGivesDifferentQueueId() throws Exception
    {
        UUID queue1 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID queue2 = UUIDGenerator.generateQueueUUID(QUEUE_NAME_1, VIRTUAL_HOST_NAME_2);
        assertFalse("Queue IDs should not be equal", queue1.equals(queue2));
    }

    public void testDifferentVirtualHostNameGivesDifferentExchangeId() throws Exception
    {
        UUID exchange1 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_1, VIRTUAL_HOST_NAME_1);
        UUID exchange2 = UUIDGenerator.generateExchangeUUID(EXCHANGE_NAME_1, VIRTUAL_HOST_NAME_2);
        assertFalse("Exchange IDs should not be equal", exchange1.equals(exchange2));
    }

    public void testDifferentVirtualHostNameGivesDifferentBindingId() throws Exception
    {
        UUID binding1 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_1, VIRTUAL_HOST_NAME_1);
        UUID binding2 = UUIDGenerator.generateBindingUUID(EXCHANGE_NAME_1, QUEUE_NAME_1, BINDING_KEY_1, VIRTUAL_HOST_NAME_2);
        assertFalse("Binding IDs should not be equal", binding1.equals(binding2));
    }
}
