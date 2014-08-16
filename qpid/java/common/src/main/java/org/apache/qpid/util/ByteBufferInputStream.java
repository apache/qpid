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
package org.apache.qpid.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Wraps @link {@link ByteBuffer} into {@link InputStream}
 */
public class ByteBufferInputStream extends InputStream
{
    private final ByteBuffer _buffer;

    public ByteBufferInputStream(ByteBuffer buffer)
    {
        _buffer = buffer;
    }

    @Override
    public int read() throws IOException
    {
        if (_buffer.hasRemaining())
        {
            return _buffer.get() & 0xFF;
        }
        return -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (!_buffer.hasRemaining())
        {
            return -1;
        }
        if(_buffer.remaining() < len)
        {
            len = _buffer.remaining();
        }
        _buffer.get(b, off, len);

        return len;
    }

    @Override
    public void mark(int readlimit)
    {
        _buffer.mark();
    }

    @Override
    public void reset() throws IOException
    {
        _buffer.reset();
    }

    @Override
    public boolean markSupported()
    {
        return true;
    }

    @Override
    public long skip(long n) throws IOException
    {
        _buffer.position(_buffer.position()+(int)n);
        return n;
    }

    @Override
    public int available() throws IOException
    {
        return _buffer.remaining();
    }

    @Override
    public void close()
    {
    }
}
