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
import org.apache.qpid.framing.Content;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.StoreContext;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;

/**
 * @author Robert Greig (robert.j.greig@jpmorgan.com)
 */
public class WeakReferenceMessageHandle implements AMQMessageHandle
{
    private WeakReference<MessageTransferBody> _messageTransferBody;

    private List<WeakReference<byte[]>> _contentBodies;

    private boolean _redelivered;

    private final MessageStore _messageStore;


    public WeakReferenceMessageHandle(MessageStore messageStore)
    {
        _messageStore = messageStore;
    }

    public MessageTransferBody getContentHeaderBody(Long messageId) throws AMQException
    {
        MessageTransferBody mtb = (_messageTransferBody != null?_messageTransferBody.get():null);
        if (mtb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(messageId);
            mtb = mmd.getMessageTransferBody();
            _messageTransferBody = new WeakReference<MessageTransferBody>(mtb);
        }
        return mtb;
    }

    public int getBodyCount(Long messageId) throws AMQException
    {
        if (_contentBodies == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(messageId);
            int chunkCount = mmd.getContentChunkCount();
            _contentBodies = new ArrayList<WeakReference<byte[]>>(chunkCount);
            for (int i = 0; i < chunkCount; i++)
            {
                _contentBodies.add(new WeakReference<byte[]>(null));
            }
        }
        return _contentBodies.size();
    }

    public long getBodySize(Long messageId) throws AMQException
    {
        Content content = getContentHeaderBody(messageId).getBody();
        return content.getContent().remaining();
    }

    public byte[] getContentBody(Long messageId, int index) throws AMQException, IllegalArgumentException
    {
        if (index > _contentBodies.size() - 1)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        WeakReference<byte[]> wr = _contentBodies.get(index);
        byte[] cb = wr.get();
        if (cb == null)
        {
            cb = _messageStore.getContentBodyChunk(messageId, index);
            _contentBodies.set(index, new WeakReference<byte[]>(cb));
        }
        return cb;
    }

    /**
     * Content bodies are set <i>before</i> the publish and header frames
     * @param storeContext
     * @param messageId
     * @param contentBody
     * @param isLastContentBody
     * @throws AMQException
     */
    public void addContentBodyFrame(StoreContext storeContext, Long messageId, byte[] contentBody, boolean isLastContentBody) throws AMQException
    {
        if(_contentBodies == null && isLastContentBody)
        {
            _contentBodies = Collections.singletonList(new WeakReference<byte[]>(contentBody));

        }
        else
        {
            if (_contentBodies == null)
            {
                _contentBodies = new LinkedList<WeakReference<byte[]>>();
            }


            _contentBodies.add(new WeakReference<byte[]>(contentBody));
        }
        _messageStore.storeContentBodyChunk(storeContext, messageId, _contentBodies.size() - 1, contentBody, isLastContentBody);
    }

    public MessageTransferBody getPublishBody(Long messageId) throws AMQException
    {
        MessageTransferBody bpb = (_messageTransferBody != null?_messageTransferBody.get():null);
        if (bpb == null)
        {
            MessageMetaData mmd = _messageStore.getMessageMetaData(messageId);
            bpb = mmd.getMessageTransferBody();
            _messageTransferBody = new WeakReference<MessageTransferBody>(bpb);
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

    public boolean isPersistent(Long messageId) throws AMQException
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        MessageTransferBody chb = getContentHeaderBody(messageId);
        return chb.getDeliveryMode() == 2;
    }

    /**
     * This is called when all the content has been received.
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(StoreContext storeContext, Long messageId, MessageTransferBody messageTransferBody)
            throws AMQException
    {
        // if there are no content bodies the list will be null so we must
        // create en empty list here
        Content content = messageTransferBody.getBody();
        _contentBodies = new LinkedList<WeakReference<byte[]>>();
        if (content.getContent().remaining() > 0)
        {
            _contentBodies.add(new WeakReference<byte[]>(content.getContentAsByteArray()));
        }
        _messageStore.storeMessageMetaData(storeContext, messageId, new MessageMetaData(messageTransferBody,
                                                                                        _contentBodies.size()));
        _messageTransferBody = new WeakReference<MessageTransferBody>(messageTransferBody);
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
}
