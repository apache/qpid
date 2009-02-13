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

import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.store.StoreContext;

/**
 */
public class InMemoryMessageHandle implements AMQMessageHandle
{

    private ContentHeaderBody _contentHeaderBody;

    private MessagePublishInfo _messagePublishInfo;

    private List<ContentChunk> _contentBodies;

    private boolean _redelivered;

    private long _arrivalTime;

    private final Long _messageId;

    public InMemoryMessageHandle(final Long messageId)
    {
        _messageId = messageId;
    }

    public ContentHeaderBody getContentHeaderBody(StoreContext context) throws AMQException
    {
        return _contentHeaderBody;
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    public int getBodyCount(StoreContext context)
    {
        return _contentBodies.size();
    }

    public long getBodySize(StoreContext context) throws AMQException
    {
        return getContentHeaderBody(context).bodySize;
    }

    public ContentChunk getContentChunk(StoreContext context, int index) throws AMQException, IllegalArgumentException
    {
        if(_contentBodies == null)
        {
            throw new RuntimeException("No ContentBody has been set");
        }

        if (index > _contentBodies.size() - 1 || index < 0)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(StoreContext storeContext, ContentChunk contentBody, boolean isLastContentBody)
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

    public MessagePublishInfo getMessagePublishInfo(StoreContext context) throws AMQException
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
    public void setPublishAndContentHeaderBody(StoreContext storeContext, MessagePublishInfo messagePublishInfo,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _messagePublishInfo = messagePublishInfo;
        _contentHeaderBody = contentHeaderBody;
        if(contentHeaderBody.bodySize == 0)
        {
            _contentBodies = Collections.EMPTY_LIST;
        }
        _arrivalTime = System.currentTimeMillis();
    }

    public void removeMessage(StoreContext storeContext) throws AMQException
    {
        // NO OP
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
