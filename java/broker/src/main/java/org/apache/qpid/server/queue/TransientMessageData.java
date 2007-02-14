/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.AMQException;

import java.util.List;
import java.util.LinkedList;

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
    private MessageTransferBody _messageTransferBody;

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;

    /**
     * This is stored during routing, to know the queues to which this message should immediately be
     * delivered. It is <b>cleared after delivery has been attempted</b>. Any persistent record of destinations is done
     * by the message handle.
     */
    private List<AMQQueue> _destinationQueues = new LinkedList<AMQQueue>();

    public MessageTransferBody getMessageTransferBody()
    {
        return _messageTransferBody;
    }

    public void setMessageTransferBody(MessageTransferBody messageTransferBody)
    {
        _messageTransferBody = messageTransferBody;
    }

    public List<AMQQueue> getDestinationQueues()
    {
        return _destinationQueues;
    }

    public void setDestinationQueues(List<AMQQueue> destinationQueues)
    {
        _destinationQueues = destinationQueues;
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
//        return _bodyLengthReceived == _contentHeaderBody.bodySize;
        // MessageTransfer has no mechanism for knowing the full size of the message up front...
        throw new Error("XXX");
    }

    public void addDestinationQueue(AMQQueue queue)
    {
        _destinationQueues.add(queue);
    }

    public boolean isPersistent()
    {
        //todo remove literal values to a constant file such as AMQConstants in common
        return _messageTransferBody.getDeliveryMode() == 2;
    }
}
