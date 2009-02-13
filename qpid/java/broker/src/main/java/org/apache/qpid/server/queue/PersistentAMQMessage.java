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

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

public class PersistentAMQMessage extends TransientAMQMessage
{
    protected MessageStore _messageStore;

    public PersistentAMQMessage(Long messageId, MessageStore store)
    {
        super(messageId);
        _messageStore = store;
    }

    @Override
    public void addContentBodyFrame(StoreContext storeContext, ContentChunk contentChunk, boolean isLastContentBody)
            throws AMQException
    {
        super.addContentBodyFrame(storeContext, contentChunk, isLastContentBody);
        _messageStore.storeContentBodyChunk(storeContext, _messageId, _contentBodies.size() - 1,
                                            contentChunk, isLastContentBody);
    }

    @Override
    public void setPublishAndContentHeaderBody(StoreContext storeContext, MessagePublishInfo messagePublishInfo,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        super.setPublishAndContentHeaderBody(storeContext, messagePublishInfo, contentHeaderBody);
        MessageMetaData mmd = new MessageMetaData(messagePublishInfo, contentHeaderBody, _contentBodies == null ? 0 : _contentBodies.size(), _arrivalTime);

        _messageStore.storeMessageMetaData(storeContext, _messageId, mmd);
    }

    @Override
    public void removeMessage(StoreContext storeContext) throws AMQException
    {
        _messageStore.removeMessage(storeContext, _messageId);
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    public void recoverFromMessageMetaData(MessageMetaData mmd)
    {
        _arrivalTime = mmd.getArrivalTime();
        _contentHeaderBody = mmd.getContentHeaderBody();
        _messagePublishInfo = mmd.getMessagePublishInfo();
    }

    public void recoverContentBodyFrame(ContentChunk contentChunk, boolean isLastContentBody) throws AMQException
    {
        super.addContentBodyFrame(null, contentChunk, isLastContentBody);
    }

}
