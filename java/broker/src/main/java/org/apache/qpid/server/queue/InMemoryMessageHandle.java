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
import org.apache.qpid.server.store.StoreContext;

import java.util.LinkedList;
import java.util.List;

/**
 */
public class InMemoryMessageHandle implements AMQMessageHandle
{

    private MessageTransferBody _messageTransferBody;

    private List<byte[]> _contentBodies = new LinkedList<byte[]>();

    private boolean _redelivered;

    public InMemoryMessageHandle()
    {
    }

    public MessageTransferBody getContentHeaderBody(Long messageId) throws AMQException
    {
        return _messageTransferBody;
    }

    public int getBodyCount(Long messageId)
    {
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
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(StoreContext storeContext, Long messageId, byte[] contentBody, boolean isLastContentBody)
            throws AMQException
    {
        _contentBodies.add(contentBody);
    }

    public MessageTransferBody getPublishBody(Long messageId) throws AMQException
    {
        return _messageTransferBody;
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
        _messageTransferBody = messageTransferBody;
    }

    public void removeMessage(StoreContext storeContext, Long messageId) throws AMQException
    {
        // NO OP
    }

    public void enqueue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException
    {
        // NO OP
    }

    public void dequeue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException
    {
        // NO OP
    }
}
