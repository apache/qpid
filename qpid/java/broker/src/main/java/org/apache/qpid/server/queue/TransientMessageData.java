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
import java.util.ArrayList;
import java.util.Collections;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;

/**
 * Contains data that is only used in AMQMessage transiently, e.g. while the content
 * body fragments are arriving.
 *
 * Having this data stored in a separate class means that the AMQMessage class avoids
 * the small overhead of numerous guaranteed-null references.
 *
 * @author Apache Software Foundation
 */
public class TransientMessageData
{
    /**
     * Stored temporarily until the header has been received at which point it is used when
     * constructing the handle
     */
    private MessagePublishInfo _messagePublishInfo;

    /**
     * Also stored temporarily.
     */
    private ContentHeaderBody _contentHeaderBody;

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private List<AMQQueue> _destinationQueues;

    public MessagePublishInfo getMessagePublishInfo()
    {
        return _messagePublishInfo;
    }

    public void setMessagePublishInfo(MessagePublishInfo messagePublishInfo)
    {
        _messagePublishInfo = messagePublishInfo;
    }

    public List<AMQQueue> getDestinationQueues()
    {
        return _destinationQueues == null ? (List<AMQQueue>) Collections.EMPTY_LIST : _destinationQueues;
    }

    public void setDestinationQueues(List<AMQQueue> destinationQueues)
    {
        _destinationQueues = destinationQueues;
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody)
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public long getBodyLengthReceived()
    {
        return _bodyLengthReceived;
    }

    public void addBodyLength(int value)
    {
        _bodyLengthReceived += value;
    }

    public boolean isAllContentReceived() throws AMQException
    {
        return _bodyLengthReceived == _contentHeaderBody.bodySize;
    }

    public void addDestinationQueue(AMQQueue queue)
    {
        if(_destinationQueues == null)
        {
            _destinationQueues = new ArrayList<AMQQueue>();
        }
        _destinationQueues.add(queue);
    }

    public boolean isPersistent()
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        return _contentHeaderBody.properties instanceof BasicContentHeaderProperties &&
             ((BasicContentHeaderProperties) _contentHeaderBody.properties).getDeliveryMode() == 2;
    }
}
