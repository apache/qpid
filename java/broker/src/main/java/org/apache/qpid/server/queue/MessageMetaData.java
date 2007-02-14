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
 */
package org.apache.qpid.server.queue;

import org.apache.qpid.framing.MessageTransferBody;

/**
 * Encapsulates a publish body and a content header. In the context of the message store these are treated as a
 * single unit.
 */
public class MessageMetaData
{
    private MessageTransferBody _messageTransferBody;

    private int _contentChunkCount;

    public MessageMetaData(MessageTransferBody messageTransferBody, int contentChunkCount)
    {
        _messageTransferBody = messageTransferBody;
        _contentChunkCount = contentChunkCount;
    }

    public int getContentChunkCount()
    {
        return _contentChunkCount;
    }

    public void setContentChunkCount(int contentChunkCount)
    {
        _contentChunkCount = contentChunkCount;
    }

    public MessageTransferBody getMessageTransferBody()
    {
        return _messageTransferBody;
    }

    public void setMessageTransferBody(MessageTransferBody messageTransferBody)
    {
        _messageTransferBody = messageTransferBody;
    }
}
