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

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.qpid.framing.EncodingUtils;
import org.apache.qpid.server.plugin.MessageMetaDataType;
import org.apache.qpid.server.util.ByteBufferOutputStream;

public class TestMessageMetaData implements StorableMessageMetaData
{
    public static final MessageMetaDataType.Factory<TestMessageMetaData> FACTORY = new TestMessageMetaDataFactory();

    private static final TestMessageMetaDataType TYPE = new TestMessageMetaDataType();

    private int _contentSize;
    private long _messageId;

    public TestMessageMetaData(long messageId, int contentSize)
    {
        _contentSize = contentSize;
        _messageId = messageId;
    }

    @Override
    public int getContentSize()
    {
        return _contentSize;
    }

    @Override
    public int getStorableSize()
    {
        int storeableSize = 8 + //message id, long, 8bytes/64bits
                            4;  //content size, int, 4bytes/32bits

        return storeableSize;
    }

    @Override
    public MessageMetaDataType getType()
    {
        return TYPE;
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @Override
    public int writeToBuffer(ByteBuffer dest)
    {
        int oldPosition = dest.position();
        try
        {
            DataOutputStream dataOutputStream = new DataOutputStream(new ByteBufferOutputStream(dest));
            EncodingUtils.writeLong(dataOutputStream, _messageId);
            EncodingUtils.writeInteger(dataOutputStream, _contentSize);
        }
        catch (IOException e)
        {
            // This shouldn't happen as we are not actually using anything that can throw an IO Exception
            throw new RuntimeException(e);
        }

        return dest.position() - oldPosition;
    };
}
