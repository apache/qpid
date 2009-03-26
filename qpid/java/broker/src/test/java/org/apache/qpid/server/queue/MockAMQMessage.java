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

import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.abstraction.MessagePublishInfoImpl;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.amqp_8_0.BasicPublishBodyImpl;

import java.util.LinkedList;
import java.util.ArrayList;

public class MockAMQMessage extends TransientAMQMessage
{
    public MockAMQMessage(long messageId)
            throws AMQException
    {
       super(messageId);
        _messagePublishInfo = new MessagePublishInfoImpl(null,false,false,null);
        BasicContentHeaderProperties properties = new BasicContentHeaderProperties();

        properties.setMessageId(String.valueOf(messageId));
        properties.setTimestamp(System.currentTimeMillis());
        properties.setDeliveryMode((byte)1);

        _contentHeaderBody = new ContentHeaderBody(properties, BasicPublishBodyImpl.CLASS_ID);
        _contentBodies = new ArrayList<ContentChunk>();
    }
}
