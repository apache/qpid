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

package org.apache.qpid.server.store;

import java.nio.ByteBuffer;

import org.apache.qpid.transport.util.Functions;

public class StoredMemoryMessage<T extends StorableMessageMetaData> implements StoredMessage<T>, MessageHandle<T>
{
    private final long _messageNumber;
    private ByteBuffer _content;
    private final T _metaData;

    public StoredMemoryMessage(long messageNumber, T metaData)
    {
        _messageNumber = messageNumber;
        _metaData = metaData;
    }

    public long getMessageNumber()
    {
        return _messageNumber;
    }

    public void addContent(ByteBuffer src)
    {
        if(_content == null)
        {
            _content = src.slice();
            _content.position(_content.limit());
        }
        else
        {
            if(_content.remaining() >= src.remaining())
            {
                _content.put(src.duplicate());
            }
            else
            {
                final int contentSize = _metaData.getContentSize();
                int size = (contentSize < _content.position() + src.remaining())
                        ? _content.position() + src.remaining()
                        : contentSize;
                ByteBuffer oldContent = _content;
                oldContent.flip();
                _content = ByteBuffer.allocate(size);
                _content.put(oldContent);
                _content.put(src.duplicate());
            }

        }
    }

    @Override
    public StoredMessage<T> allContentAdded()
    {
        if(_content != null)
        {
            _content.flip();
        }
        return this;
    }

    public int getContent(int offset, ByteBuffer dst)
    {
        if(_content == null)
        {
            return 0;
        }
        ByteBuffer src = _content.duplicate();

        int oldPosition = src.position();

        src.position(oldPosition + offset);

        int length = dst.remaining() < src.remaining() ? dst.remaining() : src.remaining();
        src.limit(oldPosition + length);

        dst.put(src);


        return length;
    }


    public ByteBuffer getContent(int offsetInMessage, int size)
    {
        if(_content == null)
        {
            return null;
        }
        ByteBuffer buf = _content.duplicate();

        if(offsetInMessage != 0)
        {
            buf.position(offsetInMessage);
            buf = buf.slice();
        }

        buf.limit(size);
        return buf;
    }

    public T getMetaData()
    {
        return _metaData;
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
