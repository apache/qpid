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

import java.util.ArrayList;

import junit.framework.TestCase;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.MessageHeaders;
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.SkeletonMessageStore;
import org.apache.qpid.server.util.TestApplicationRegistry;

class MessageTestHelper extends TestCase
{
    private final MessageStore _messageStore = new SkeletonMessageStore();

    MessageTestHelper() throws Exception
    {
        ApplicationRegistry.initialise(new TestApplicationRegistry());
    }

    AMQMessage message() throws AMQException
    {
        return message(false);
    }

    AMQMessage message(boolean immediate) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Establish some way to determine the version for the test.
    	MessageHeaders messageHeaders = new MessageHeaders();
    	
    	MessageTransferBody methodBody = MessageTransferBody.createMethodBody(
            (byte)0, (byte)9,               // AMQP version (major, minor)
            messageHeaders.getAppId(),      // String appId
            messageHeaders.getJMSHeaders(), // FieldTable applicationHeaders
            new Content(),                        // Content body
            messageHeaders.getEncoding(),   // String contentEncoding
            messageHeaders.getContentType(), // String contentType
            messageHeaders.getCorrelationId(), // String correlationId
            (short)1,  // short deliveryMode
            "someExchange",                  // String destination
            "someExchange",                  // String exchange
            messageHeaders.getExpiration(), // long expiration
            immediate,                          // boolean immediate
            "",                         // String messageId
            (short)0,                       // short priority
            false,                          // boolean redelivered
            messageHeaders.getReplyTo(),    // String replyTo
            "rk",                           // String routingKey
            new String("abc123").getBytes(), // byte[] securityToken
            0,                              // int ticket
            messageHeaders.getTimestamp(),  // long timestamp
            messageHeaders.getTransactionId(), // String transactionId
            0,                              // long ttl
            messageHeaders.getUserId());    // String userId
    	
    	return new AMQMessage(_messageStore, methodBody, new ArrayList()); 
    }

}
