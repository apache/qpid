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
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.BasicPublishBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;

import java.util.LinkedList;
import java.util.List;

/**
 */
public class InMemoryMessageHandle implements AMQMessageHandle
{

    private ContentHeaderBody _contentHeaderBody;

    private BasicPublishBody _publishBody;

    private List<ContentBody> _contentBodies = new LinkedList<ContentBody>();

    private boolean _redelivered;

    private long _arrivalTime;

    public InMemoryMessageHandle()
    {
    }

    public ContentHeaderBody getContentHeaderBody(Long messageId) throws AMQException
    {
        return _contentHeaderBody;
    }

    public int getBodyCount(Long messageId)
    {
        return _contentBodies.size();
    }

    public long getBodySize(Long messageId) throws AMQException
    {
        return getContentHeaderBody(messageId).bodySize;
    }

    public ContentBody getContentBody(Long messageId, int index) throws AMQException, IllegalArgumentException
    {
        if (index > _contentBodies.size() - 1)
        {
            throw new IllegalArgumentException("Index " + index + " out of valid range 0 to " +
                                               (_contentBodies.size() - 1));
        }
        return _contentBodies.get(index);
    }

    public void addContentBodyFrame(StoreContext storeContext, Long messageId, ContentBody contentBody, boolean isLastContentBody)
            throws AMQException
    {
        _contentBodies.add(contentBody);
    }

    public BasicPublishBody getPublishBody(Long messageId) throws AMQException
    {
        return _publishBody;
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
        ContentHeaderBody chb = getContentHeaderBody(messageId);
        return chb.properties instanceof BasicContentHeaderProperties &&
               ((BasicContentHeaderProperties) chb.properties).getDeliveryMode() == 2;
    }

    /**
     * This is called when all the content has been received.
     * @param publishBody
     * @param contentHeaderBody
     * @throws AMQException
     */
    public void setPublishAndContentHeaderBody(StoreContext storeContext, Long messageId, BasicPublishBody publishBody,
                                               ContentHeaderBody contentHeaderBody)
            throws AMQException
    {
        _publishBody = publishBody;
        _contentHeaderBody = contentHeaderBody;
        _arrivalTime = System.currentTimeMillis();
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

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

}
