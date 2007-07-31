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

import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;

/**
 * Encapsulates a publish body and a content header. In the context of the message store these are treated as a
 * single unit.
 */
public class MessageMetaData
{
    private MessagePublishInfo _messagePublishInfo;

    private ContentHeaderBody _contentHeaderBody;

    private int _contentChunkCount;

    private long _arrivalTime;

    public MessageMetaData(MessagePublishInfo publishBody, ContentHeaderBody contentHeaderBody, int contentChunkCount)
    {
        this(publishBody,contentHeaderBody, contentChunkCount, System.currentTimeMillis());
    }

    public MessageMetaData(MessagePublishInfo publishBody, ContentHeaderBody contentHeaderBody, int contentChunkCount, long arrivalTime)
    {
        _contentHeaderBody = contentHeaderBody;
        _messagePublishInfo = publishBody;
        _contentChunkCount = contentChunkCount;
        _arrivalTime = arrivalTime;
    }

    public int getContentChunkCount()
    {
        return _contentChunkCount;
    }

    public void setContentChunkCount(int contentChunkCount)
    {
        _contentChunkCount = contentChunkCount;
    }

    public ContentHeaderBody getContentHeaderBody()
    {
        return _contentHeaderBody;
    }

    public void setContentHeaderBody(ContentHeaderBody contentHeaderBody)
    {
        _contentHeaderBody = contentHeaderBody;
    }

    public MessagePublishInfo getMessagePublishInfo()
    {
        return _messagePublishInfo;
    }

    public void setMessagePublishInfo(MessagePublishInfo messagePublishInfo)
    {
        _messagePublishInfo = messagePublishInfo;
    }

    public long getArrivalTime()
    {
        return _arrivalTime;
    }

    public void setArrivalTime(long arrivalTime)
    {
        _arrivalTime = arrivalTime;
    }
}
