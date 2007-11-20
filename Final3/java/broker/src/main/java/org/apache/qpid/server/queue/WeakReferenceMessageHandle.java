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
import org.apache.qpid.framing.BasicContentHeaderProperties;
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

    private long _arrivalTime;


    public WeakReferenceMessageHandle(MessageStore messageStore)
    {
        _messageStore = messageStore;
    }

    public ContentHeaderBody getContentHeaderBody(StoreContext context, Long messageId) throws AMQException
    {
        ContentHeaderBody chb = (_contentHeaderBody != null ? _contentHeaderBody.get() : null);
        if (chb == null)
        {
            MessageMetaData mmd = loadMessageMetaData(context, messageId);
            chb = mmd.getContentHeaderBody();
        }
        return chb;
    }

    private MessageMetaData loadMessageMetaData(StoreContext context, Long messageId)
            throws AMQException
    {
        MessageMetaData mmd = _messageStore.getMessageMetaData(context, messageId);
        populateFromMessageMetaData(mmd);
        return mmd;
    }

    private void populateFromMessageMetaData(MessageMetaData mmd)
    {
        _arrivalTime = mmd.getArrivalTime();
        _contentHeaderBody = new WeakReference<ContentHeaderBody>(mmd.getContentHeaderBody());
        _messagePublishInfo = new WeakReference<MessagePublishInfo>(mmd.getMessagePublishInfo());
    }

    public int getBodyCount(StoreContext context, Long messageId) throws AMQException
    {
        if (_contentBodies == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(context, messageId);
            int chunkCount = mmd.getContentChunkCount();
            _contentBodies = new ArrayList<WeakReference<ContentChunk>>(chunkCount);
            for (int i = 0; i < chunkCount; i++)
            {
                _contentBodies.add(new WeakReference<ContentChunk>(null));
            }
        }
        return _contentBodies.size();
    }

    public long getBodySize(StoreContext context, Long messageId) throws AMQException
    {
        return getContentHeaderBody(context, messageId).bodySize;
    }

    public ContentChunk getContentChunk(StoreContext context, Long messageId, int index) throws AMQException, IllegalArgumentException
    {
        if (index > _contentBodies.size() - 1)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        WeakReference<ContentChunk> wr = _contentBodies.get(index);
        ContentChunk cb = wr.get();
        if (cb == null)
        {
            cb = _messageStore.getContentBodyChunk(context, messageId, index);
            _contentBodies.set(index, new WeakReference<ContentChunk>(cb));
        }
        return cb;
    }

    /**
     * Content bodies are set <i>before</i> the publish and header frames
     *
     * @param storeContext
     * @param messageId
     * @param contentChunk
     * @param isLastContentBody
     * @throws AMQException
     */
    public void addContentBodyFrame(StoreContext storeContext, Long messageId, ContentChunk contentChunk, boolean isLastContentBody) throws AMQException
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
        _messageStore.storeContentBodyChunk(storeContext, messageId, _contentBodies.size() - 1,
                                            contentChunk, isLastContentBody);
    }

    public MessagePublishInfo getMessagePublishInfo(StoreContext context, Long messageId) throws AMQException
    {
        MessagePublishInfo bpb = (_messagePublishInfo != null ? _messagePublishInfo.get() : null);
        if (bpb == null)
        {
            MessageMetaData mmd = loadMessageMetaData(context, messageId);

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

    public boolean isPersistent(StoreContext context, Long messageId) throws AMQException
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        ContentHeaderBody chb = getContentHeaderBody(context, messageId);
        return chb.properties instanceof BasicContentHeaderProperties &&
               ((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 2;
    }

    /**
     * This is called when all the content has been received.
     *
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(StoreContext storeContext, Long messageId, MessagePublishInfo publishBody,
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


        MessageMetaData mmd = new MessageMetaData(publishBody, contentHeaderBody, _contentBodies.size(), arrivalTime);

        _messageStore.storeMessageMetaData(storeContext, messageId, mmd);

        populateFromMessageMetaData(mmd);
    }

    public void removeMessage(StoreContext storeContext, Long messageId) throws AMQException
    {
        _messageStore.removeMessage(storeContext, messageId);
    }

    public void enqueue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException
    {
        _messageStore.enqueueMessage(storeContext, queue.getName(), messageId);
    }

    public void dequeue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException
    {
        _messageStore.dequeueMessage(storeContext, queue.getName(), messageId);
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
