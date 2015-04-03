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

import java.nio.ByteBuffer;

import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.server.store.MessageHandle;
import org.apache.qpid.server.store.StoredMessage;

public class MockStoredMessage implements StoredMessage<MessageMetaData>, MessageHandle<MessageMetaData>
{
    private long _messageId;
    private MessageMetaData _metaData;
    private final ByteBuffer _content;

    public MockStoredMessage(long messageId)
    {
        this(messageId, (String)null, null);
    }

    public MockStoredMessage(long messageId, String headerName, Object headerValue)
    {
        this(messageId, new MessagePublishInfo(null, false, false, null), new ContentHeaderBody(new BasicContentHeaderProperties()), headerName, headerValue);
    }

    public MockStoredMessage(long messageId, MessagePublishInfo info, ContentHeaderBody chb)
    {
        this(messageId, info, chb, null, null);
    }

    public MockStoredMessage(long messageId, MessagePublishInfo info, ContentHeaderBody chb, String headerName, Object headerValue)
    {
        _messageId = messageId;
        if (headerName != null)
        {
            FieldTable headers = new FieldTable();
            headers.setString(headerName, headerValue == null? null :String.valueOf(headerValue));
            ( chb.getProperties()).setHeaders(headers);
        }
        _metaData = new MessageMetaData(info, chb);
        _content = ByteBuffer.allocate(_metaData.getContentSize());
    }

    public MessageMetaData getMetaData()
    {
        return _metaData;
    }

    public long getMessageNumber()
    {
        return _messageId;
    }

    public void addContent(ByteBuffer src)
    {
        src = src.duplicate();
        _content.put(src);
    }

    @Override
    public StoredMessage<MessageMetaData> allContentAdded()
    {
        _content.flip();
        return this;
    }

    public int getContent(int offset, ByteBuffer dst)
    {
        ByteBuffer src = _content.duplicate();
        src.position(offset);
        src = src.slice();
        if(dst.remaining() < src.limit())
        {
            src.limit(dst.remaining());
        }
        dst.put(src);
        return src.limit();
    }



    public ByteBuffer getContent(int offsetInMessage, int size)
    {
        ByteBuffer buf = ByteBuffer.allocate(size);
        getContent(offsetInMessage, buf);
        buf.position(0);
        return  buf;
    }

    public void remove()
    {
    }

    @Override
    public boolean isInMemory()
    {
        return true;
    }

    @Override
    public boolean flowToDisk()
    {
        return false;
    }
}
