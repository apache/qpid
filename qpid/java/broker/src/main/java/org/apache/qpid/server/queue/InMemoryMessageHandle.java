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

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ContentChunk;

/**
 */
public class InMemoryMessageHandle implements AMQMessageHandle
{

    private ContentHeaderBody _contentHeaderBody;

    private MessagePublishInfo _messagePublishInfo;

    private List<ContentChunk> _contentBodies;

    private long _arrivalTime;

    private final Long _messageId;

    public InMemoryMessageHandle(final Long messageId)
    {
        _messageId = messageId;
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        return _contentHeaderBody;
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    public int getBodyCount()
    {
        return _contentBodies.size();
    }

    public long getBodySize() throws AMQException
    {
        return getContentHeaderBody().bodySize;
    }

    public ContentChunk getContentChunk(int index) throws AMQException, IllegalArgumentException
    {
        if (index > _contentBodies.size() - 1)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(ContentChunk contentBody, boolean isLastContentBody)
            throws AMQException
    {
        if(_contentBodies == null)
        {
            if(isLastContentBody)
            {
                _contentBodies = Collections.singletonList(contentBody);
            }
            else
            {
                _contentBodies = new ArrayList<ContentChunk>();
                _contentBodies.add(contentBody);
            }
        }
        else
        {
            _contentBodies.add(contentBody);
        }
    }

    public MessagePublishInfo getMessagePublishInfo() throws AMQException
    {
        return _messagePublishInfo;
    }

    public boolean isPersistent()
    {
        return false;
    }

    /**
     * This is called when all the content has been received.
     * @param messagePublishInfo
     * @param contentHeaderBody
     * @throws AMQException
     */
    public MessageMetaData setPublishAndContentHeaderBody(MessagePublishInfo messagePublishInfo,
                                                          ContentHeaderBody contentHeaderBody)
    {
        _messagePublishInfo = messagePublishInfo;
        _contentHeaderBody = contentHeaderBody;
        if(contentHeaderBody.bodySize == 0)
        {
            _contentBodies = Collections.EMPTY_LIST;
        }
        _arrivalTime = System.currentTimeMillis();
        MessageMetaData mmd = new MessageMetaData(messagePublishInfo, contentHeaderBody, _contentBodies == null ? 0 : _contentBodies.size(), _arrivalTime);
        return mmd;
    }

    public void removeMessage() throws AMQException
    {
        // NO OP
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
