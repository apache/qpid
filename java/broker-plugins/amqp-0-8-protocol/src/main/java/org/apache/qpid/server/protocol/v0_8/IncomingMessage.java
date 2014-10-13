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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.message.MessageDestination;

public class IncomingMessage
{

    private final MessagePublishInfo _messagePublishInfo;
    private ContentHeaderBody _contentHeaderBody;
    private MessageDestination _messageDestination;

    /**
     * Keeps a track of how many bytes we have received in body frames
     */
    private long _bodyLengthReceived = 0;
    private List<ContentBody> _contentChunks = new ArrayList<ContentBody>();

    public IncomingMessage(MessagePublishInfo info)
    {
        _messagePublishInfo = info;
    }

    public void setContentHeaderBody(final ContentHeaderBody contentHeaderBody)
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public MessagePublishInfo getMessagePublishInfo()
    {
        return _messagePublishInfo;
    }

    public void addContentBodyFrame(final ContentBody contentChunk)
    {
        _bodyLengthReceived += contentChunk.getSize();
        _contentChunks.add(contentChunk);
    }

    public boolean allContentReceived()
    {
        return (_bodyLengthReceived == getContentHeader().getBodySize());
    }

    public AMQShortString getExchangeName()
    {
        return _messagePublishInfo.getExchange();
    }

    public MessageDestination getDestination()
    {
        return _messageDestination;
    }

    public ContentHeaderBody getContentHeader()
    {
        return _contentHeaderBody;
    }

    public long getSize()
    {
        return getContentHeader().getBodySize();
    }

    public void setMessageDestination(final MessageDestination e)
    {
        _messageDestination = e;
    }

    public int getBodyCount()
    {
        return _contentChunks.size();
    }

    public ContentBody getContentChunk(int index)
    {
        return _contentChunks.get(index);
    }

}
