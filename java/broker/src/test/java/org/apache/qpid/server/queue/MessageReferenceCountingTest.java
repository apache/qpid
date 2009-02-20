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

import junit.framework.TestCase;

public class MessageReferenceCountingTest extends TestCase
{
    AMQMessage _message;

    public void setUp()
    {
        _message = MessageFactory.getInstance().createMessage(null, false);
    }

    public void testInitialState()
    {

        assertTrue("New messages should have a reference", _message.isReferenced());
    }

    public void testIncrementReference()
    {
        assertTrue("Message should maintain Referenced state", _message.isReferenced());
        assertTrue("Incrementing should be allowed ",_message.incrementReference(1));
        assertTrue("Message should maintain Referenced state", _message.isReferenced());
        assertTrue("Incrementing should be allowed as much as we need",_message.incrementReference(1));
        assertTrue("Message should maintain Referenced state", _message.isReferenced());
        assertTrue("Incrementing should be allowed as much as we need",_message.incrementReference(2));
        assertTrue("Message should maintain Referenced state", _message.isReferenced());
    }

    public void testDecrementReference()
    {
        assertTrue("Message should maintain Referenced state", _message.isReferenced());
        try
        {
            _message.decrementReference(null);
        }
        catch (MessageCleanupException e)
        {
            fail("Decrement should be allowed:"+e.getMessage());
        }

        assertFalse("Message should not be Referenced state", _message.isReferenced());

        try
        {
            _message.decrementReference(null);
            fail("Decrement should not be allowed as we should have a ref count of 0");
        }
        catch (MessageCleanupException e)
        {
            assertTrue("Incorrect exception thrown.",e.getMessage().contains("has gone below 0"));
        }

    }

}
