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
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class InternalMessageMetaData implements StorableMessageMetaData
{


    private boolean _isPersistent;
    private InternalMessageHeader _header;
    private int _contentSize;
    private byte[] _headerBytes;

    public InternalMessageMetaData(final boolean isPersistent, final InternalMessageHeader header, final int contentSize)
    {
        _isPersistent = isPersistent;
        _header = header;
        _contentSize = contentSize;

        try(ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bytesOut))
        {
            os.writeInt(contentSize);
            os.writeObject(header);
            _headerBytes = bytesOut.toByteArray();
        }
        catch (IOException e)
        {
            throw new ConnectionScopedRuntimeException("Unexpected IO Exception on in memory operation", e);
        }
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
        return new InternalMessageMetaData(persistent, header, contentSize);
    }


}
