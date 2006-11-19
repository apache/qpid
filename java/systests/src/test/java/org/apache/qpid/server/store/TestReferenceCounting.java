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
package org.apache.qpid.server.store;

import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.AMQException;

import junit.framework.TestCase;

/**
 * Tests that reference counting works correctly with AMQMessage and the message store
 */
public class TestReferenceCounting extends TestCase
{
    private TestableMemoryMessageStore _store;

    protected void setUp() throws Exception
    {
        super.setUp();
        _store = new TestableMemoryMessageStore();
    }

    /**
     * Check that when the reference count is decremented the message removes itself from the store
     */
    public void testMessageGetsRemoved() throws AMQException
    {
        AMQMessage message = new AMQMessage(_store, null);
        _store.put(message);
        assertTrue(_store.getMessageMap().size() == 1);
        message.decrementReference();
        assertTrue(_store.getMessageMap().size() == 0);
    }

    public void testMessageRemains() throws AMQException
    {
        AMQMessage message = new AMQMessage(_store, null);
        _store.put(message);
        assertTrue(_store.getMessageMap().size() == 1);
        message.incrementReference();
        message.decrementReference();
        assertTrue(_store.getMessageMap().size() == 1);
    }

    public static junit.framework.Test suite()
    {
        return new junit.framework.TestSuite(TestReferenceCounting.class);
    }
}
