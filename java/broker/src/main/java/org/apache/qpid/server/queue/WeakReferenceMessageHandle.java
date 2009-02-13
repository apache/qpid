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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private WeakReference<ContentHeaderBody> _contentHeaderBody;

    private WeakReference<MessagePublishInfo> _messagePublishInfo;

    private List<WeakReference<ContentChunk>> _contentBodies;

    private boolean _redelivered;

    private final MessageStore _messageStore;

    private final Long _messageId;
    private long _arrivalTime;

    public WeakReferenceMessageHandle(final Long messageId, MessageStore messageStore)
    {
        _messageId = messageId;
        _messageStore = messageStore;
    }

    public ContentHeaderBody getContentHeaderBody(StoreContext context) throws AMQException
    {
        ContentHeaderBody chb = (_contentHeaderBody != null ? _contentHeaderBody.get() : null);
        if (chb == null)
        {
            MessageMetaData mmd = loadMessageMetaData(context);
            chb = mmd.getContentHeaderBody();
        }
        return chb;
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    private MessageMetaData loadMessageMetaData(StoreContext context)
            throws AMQException
    {
        MessageMetaData mmd = _messageStore.getMessageMetaData(context, _messageId);
        populateFromMessageMetaData(mmd);
        return mmd;
    }

    private void populateFromMessageMetaData(MessageMetaData mmd)
    {
        _arrivalTime = mmd.getArrivalTime();
        _contentHeaderBody = new WeakReference<ContentHeaderBody>(mmd.getContentHeaderBody());
        _messagePublishInfo = new WeakReference<MessagePublishInfo>(mmd.getMessagePublishInfo());
    }

    public int getBodyCount(StoreContext context) throws AMQException
    {
        if (_contentBodies == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(context, _messageId);
            int chunkCount = mmd.getContentChunkCount();
            _contentBodies = new ArrayList<WeakReference<ContentChunk>>(chunkCount);
            for (int i = 0; i < chunkCount; i++)
            {
                _contentBodies.add(new WeakReference<ContentChunk>(null));
            }
        }
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
        WeakReference<ContentChunk> wr = _contentBodies.get(index);
        ContentChunk cb = wr.get();
        if (cb == null)
        {
            cb = _messageStore.getContentBodyChunk(context, _messageId, index);
            _contentBodies.set(index, new WeakReference<ContentChunk>(cb));
        }
        return cb;
    }

    /**
     * Content bodies are set <i>before</i> the publish and header frames
     *
     * @param storeContext
     * @param contentChunk
     * @param isLastContentBody
     * @throws AMQException
     */
    public void addContentBodyFrame(StoreContext storeContext, ContentChunk contentChunk, boolean isLastContentBody) throws AMQException
    {
        if (_contentBodies == null && isLastContentBody)
        {
            _contentBodies = new ArrayList<WeakReference<ContentChunk>>(1);
        }
        else
        {
            if (_contentBodies == null)
            {
                _contentBodies = new LinkedList<WeakReference<ContentChunk>>();
            }
        }
        _contentBodies.add(new WeakReference<ContentChunk>(contentChunk));
        _messageStore.storeContentBodyChunk(storeContext, _messageId, _contentBodies.size() - 1,
                                            contentChunk, isLastContentBody);
    }

    public MessagePublishInfo getMessagePublishInfo(StoreContext context) throws AMQException
    {
        MessagePublishInfo bpb = (_messagePublishInfo != null ? _messagePublishInfo.get() : null);
        if (bpb == null)
        {
            MessageMetaData mmd = loadMessageMetaData(context);

            bpb = mmd.getMessagePublishInfo();
        }
        return bpb;
    }

    public boolean isRedelivered()
    {
        return _redelivered;
    }

    public void setRedelivered(boolean redelivered)
    {
        _redelivered = redelivered;
    }

    public boolean isPersistent()
    {
        return true;
    }

    /**
     * This is called when all the content has been received.
     *
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(StoreContext storeContext, MessagePublishInfo publishBody,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        // if there are no content bodies the list will be null so we must
        // create en empty list here
        if (contentHeaderBody.bodySize == 0)
        {
            _contentBodies = new LinkedList<WeakReference<ContentChunk>>();
        }

        final long arrivalTime = System.currentTimeMillis();

        MessageMetaData mmd = new MessageMetaData(publishBody, contentHeaderBody, _contentBodies == null ? 0 : _contentBodies.size(), arrivalTime);

        _messageStore.storeMessageMetaData(storeContext, _messageId, mmd);


        populateFromMessageMetaData(mmd);
    }

    public void removeMessage(StoreContext storeContext) throws AMQException
    {
        _messageStore.removeMessage(storeContext, _messageId);
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
