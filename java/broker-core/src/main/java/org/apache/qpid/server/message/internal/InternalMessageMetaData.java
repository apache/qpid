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
package org.apache.qpid.server.message.internal;

import org.apache.qpid.server.store.StorableMessageMetaData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class InternalMessageMetaData implements StorableMessageMetaData
{


    private boolean _isPersistent;
    private byte[] _headerBytes;
    private int _contentSize;

    public InternalMessageMetaData(final boolean isPersistent, final byte[] headerBytes, final int contentSize)
    {
        _isPersistent = isPersistent;
        _headerBytes = headerBytes;
        _contentSize = contentSize;
    }

    @Override
    public InternalMessageMetaDataType getType()
    {
        return InternalMessageMetaDataType.INSTANCE;
    }

    @Override
    public int getStorableSize()
    {
        return _headerBytes.length;
    }

    @Override
    public int writeToBuffer(final ByteBuffer dest)
    {
        dest.put(_headerBytes);
        return _headerBytes.length;
    }

    @Override
    public int getContentSize()
    {
        return _contentSize;
    }

    @Override
    public boolean isPersistent()
    {
        return _isPersistent;
    }

    static InternalMessageMetaData create(boolean persistent, final InternalMessageHeader header, int contentSize)
    {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        try
        {
            ObjectOutputStream os = new ObjectOutputStream(bytesOut);
            os.writeObject(header);
            byte[] bytes = bytesOut.toByteArray();

            return new InternalMessageMetaData(persistent, bytes, contentSize);

        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }


}
