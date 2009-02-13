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

public class MessageFactoryRecoveryTest extends TestCase
{
    private MessageFactory _factory;

    public void setUp()
    {
        _factory = MessageFactory.getInstance();

    }

    public void test()
    {
        AMQMessage message = _factory.createMessage(null, false);

        _factory.enableRecover();

        Long messasgeID = message.getMessageId();

        try
        {
            _factory.createMessage(messasgeID, null);
            fail("Cannot recreate message with an existing id");
        }
        catch (RuntimeException re)
        {
            assertEquals("Incorrect exception thrown ",
                         "Message IDs can only increase current id is:" + messasgeID + ". Requested:" + messasgeID, re.getMessage());
        }

        //Check we cannot go backwords with ids.
        try
        {
            _factory.createMessage(messasgeID - 1, null);
            fail("Cannot recreate message with an old id");
        }
        catch (RuntimeException re)
        {
            assertEquals("Incorrect exception thrown ",
                         "Message IDs can only increase current id is:" + messasgeID + ". Requested:" + (messasgeID - 1), re.getMessage());
        }

        //Check that we can jump forward in ids during recovery.
        messasgeID += 100;
        try
        {
            message = _factory.createMessage(messasgeID, null);
            assertEquals("Factory assigned incorrect id.", messasgeID, message.getMessageId());
        }
        catch (Exception re)
        {
            fail("Message with a much higher value should be created");
        }

        // End the reovery process.
        _factory.start();

        //Check we cannot still create by id after ending recovery phase
        try
        {
            _factory.createMessage(messasgeID, null);
            fail("We have left recovery mode so we cannot create by id any more");
        }
        catch (Exception re)
        {
            assertEquals("Incorrect exception thrown ",
                         "Unable to create message by ID when not recovering", re.getMessage());
        }

        // Check that the next message created has the next available id

        messasgeID++;

        try
        {
            message = _factory.createMessage(null, false);
            assertEquals("Factory assigned incorrect id.", messasgeID, message.getMessageId());
        }
        catch (Exception re)
        {
            fail("Message with a much higher value should be created");
        }

    }

}