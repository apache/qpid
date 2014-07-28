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
package org.apache.qpid.server.protocol.v0_8;

import org.apache.log4j.Logger;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.message.AMQMessageHeader;
import org.apache.qpid.server.message.AbstractServerMessageImpl;
import org.apache.qpid.server.store.StoredMessage;

/**
 * A deliverable message.
 */
public class AMQMessage extends AbstractServerMessageImpl<AMQMessage, MessageMetaData>
{
    /** Used for debugging purposes. */
    private static final Logger _log = Logger.getLogger(AMQMessage.class);

    private final long _size;

    public AMQMessage(StoredMessage<MessageMetaData> handle)
    {
        this(handle, null);
    }

    public AMQMessage(StoredMessage<MessageMetaData> handle, Object connectionReference)
    {
        super(handle, connectionReference);
        _size = handle.getMetaData().getContentSize();
    }

    public MessageMetaData getMessageMetaData()
    {
        return getStoredMessage().getMetaData();
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return getMessageMetaData().getContentHeaderBody();
    }

    public String getInitialRoutingAddress()
    {
        MessageMetaData messageMetaData = getMessageMetaData();
        if (messageMetaData != null)
        {
            AMQShortString routingKey = messageMetaData.getMessagePublishInfo().getRoutingKey();
            if (routingKey != null)
            {
                return routingKey.asString();
            }
        }
        return null;
    }

    public AMQMessageHeader getMessageHeader()
    {
        return getMessageMetaData().getMessageHeader();
    }

    public MessagePublishInfo getMessagePublishInfo()
    {
        return getMessageMetaData().getMessagePublishInfo();
    }

    public long getArrivalTime()
    {
        return getMessageMetaData().getArrivalTime();
    }

    public long getSize()
    {
        return _size;
    }

    public boolean isImmediate()
    {
        return getMessagePublishInfo().isImmediate();
    }

    public boolean isMandatory()
    {
        return getMessagePublishInfo().isMandatory();
    }

    public long getExpiration()
    {
        return getMessageHeader().getExpiration();
    }


}
