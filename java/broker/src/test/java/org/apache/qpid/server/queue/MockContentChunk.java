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

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.FixedSizeByteBufferAllocator;
import org.apache.qpid.framing.abstraction.ContentChunk;

public class MockContentChunk implements ContentChunk
{
    public static final int DEFAULT_SIZE=0;

    private ByteBuffer _bytebuffer;
    private int _size;



    public MockContentChunk()
    {
        this(0);
    }

    public MockContentChunk(int size)
    {
        FixedSizeByteBufferAllocator allocator = new FixedSizeByteBufferAllocator();
        _bytebuffer = allocator.allocate(size, false);

        _size = size;
    }

    public MockContentChunk(ByteBuffer bytebuffer, int size)
    {
        _bytebuffer = bytebuffer;
        _size = size;
    }

    public int getSize()
    {
        return _size;
    }

    public ByteBuffer getData()
    {
        return _bytebuffer;
    }

    public void reduceToFit()
    {
    }
}
