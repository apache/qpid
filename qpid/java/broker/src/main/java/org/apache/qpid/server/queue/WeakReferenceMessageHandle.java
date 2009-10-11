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

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private WeakReference<ContentHeaderBody> _contentHeaderBody;

    private WeakReference<MessagePublishInfo> _messagePublishInfo;

    private List<WeakReference<ContentChunk>> _contentBodies;

    private final MessageStore _messageStore;

    private final Long _messageId;
    private long _arrivalTime;

    public WeakReferenceMessageHandle(final Long messageId, MessageStore messageStore)
    {
        _messageId = messageId;
        _messageStore = messageStore;
    }

    public ContentHeaderBody getContentHeaderBody() throws AMQException
    {
        ContentHeaderBody chb = (_contentHeaderBody != null ? _contentHeaderBody.get() : null);
        if (chb == null)
        {
            MessageMetaData mmd = loadMessageMetaData();
            chb = mmd.getContentHeaderBody();
        }
        return chb;
    }

    public Long getMessageId()
    {
        return _messageId;
    }

    private MessageMetaData loadMessageMetaData()
            throws AMQException
    {
        MessageMetaData mmd = _messageStore.getMessageMetaData(_messageId);
        populateFromMessageMetaData(mmd);
        return mmd;
    }

    private void populateFromMessageMetaData(MessageMetaData mmd)
    {
        _arrivalTime = mmd.getArrivalTime();
        _contentHeaderBody = new WeakReference<ContentHeaderBody>(mmd.getContentHeaderBody());
        _messagePublishInfo = new WeakReference<MessagePublishInfo>(mmd.getMessagePublishInfo());
    }

    public int getBodyCount() throws AMQException
    {
        if (_contentBodies == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(_messageId);
            int chunkCount = mmd.getContentChunkCount();
            _contentBodies = new ArrayList<WeakReference<ContentChunk>>(chunkCount);
            for (int i = 0; i < chunkCount; i++)
            {
                _contentBodies.add(new WeakReference<ContentChunk>(null));
            }
        }
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
        WeakReference<ContentChunk> wr = _contentBodies.get(index);
        ContentChunk cb = wr.get();
        if (cb == null)
        {
            cb = _messageStore.getContentBodyChunk(_messageId, index);
            _contentBodies.set(index, new WeakReference<ContentChunk>(cb));
        }
        return cb;
    }

    /**
     * Content bodies are set <i>before</i> the publish and header frames
     *
     * @param contentChunk
     * @param isLastContentBody
     * @throws AMQException
     */
    public void addContentBodyFrame(ContentChunk contentChunk, boolean isLastContentBody) throws AMQException
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

    }

    public MessagePublishInfo getMessagePublishInfo() throws AMQException
    {
        MessagePublishInfo bpb = (_messagePublishInfo != null ? _messagePublishInfo.get() : null);
        if (bpb == null)
        {
            MessageMetaData mmd = loadMessageMetaData();

            bpb = mmd.getMessagePublishInfo();
        }
        return bpb;
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
    public MessageMetaData setPublishAndContentHeaderBody(MessagePublishInfo publishBody,
                                                          ContentHeaderBody contentHeaderBody)
    {
        // if there are no content bodies the list will be null so we must
        // create en empty list here
        if (contentHeaderBody.bodySize == 0)
        {
            _contentBodies = new LinkedList<WeakReference<ContentChunk>>();
        }

        final long arrivalTime = System.currentTimeMillis();


        MessageMetaData mmd = new MessageMetaData(publishBody, contentHeaderBody, _contentBodies == null ? 0 : _contentBodies.size(), arrivalTime);

        populateFromMessageMetaData(mmd);
        return mmd;
    }

    public void removeMessage() throws AMQException
    {
        _messageStore.removeMessage(_messageId);
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
