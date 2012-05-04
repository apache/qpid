/*
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
 */

package org.apache.qpid.amqp_1_0.transport;

import java.nio.ByteBuffer;

public class CircularBytesBuffer
{

    private final byte[] _buffer;
    private final int _mask;
    private final ByteBuffer _inputBuffer;
    private final ByteBuffer _outputBuffer;

    private volatile int _start;
    private volatile int _size;

    public CircularBytesBuffer(int size)
    {
        size = calculateSize(size);
        _buffer = new byte[size];
        _mask = size - 1;
        _inputBuffer = ByteBuffer.wrap(_buffer);
        _outputBuffer = ByteBuffer.wrap(_buffer);

    }


    public int size()
    {
        return _size;
    }

    public boolean isFull()
    {
        return _size == _buffer.length;
    }

    public boolean isEmpty()
    {
        return _size == 0;
    }

    public void put(ByteBuffer buffer)
    {
        if(!isFull())
        {
            int start;
            int size;

            synchronized(this)
            {
                start = _start;
                size = _size;
            }

            int pos = (start + size) & _mask;
            int length = ((_buffer.length - pos) > size ? start : _buffer.length) - pos;
            int remaining = length > buffer.remaining() ? buffer.remaining() : length;
            buffer.get(_buffer, pos, remaining);

            synchronized(this)
            {
                _size += remaining;
            }

            // We may still have space left if we have to wrap from the end to the start of the buffer
            if(buffer.hasRemaining())
            {
                put(buffer);
            }
        }
    }

    public synchronized void put(BytesProcessor processor)
    {
        if(!isFull())
        {
            int start;
            int size;

            synchronized(this)
            {
                start = _start;
                size = _size;
            }
            int pos = (start + size) & _mask;
            int length = ((_buffer.length - pos) > size ? start : _buffer.length) - pos;
            _outputBuffer.position(pos);
            _outputBuffer.limit(pos+length);
            processor.processBytes(_outputBuffer);

            synchronized (this)
            {
                _size += length - _outputBuffer.remaining();
            }

            if(_outputBuffer.remaining() == 0)
            {
                put(processor);
            }
        }
    }

    public synchronized void get(BytesProcessor processor)
    {
        if(!isEmpty())
        {
            int start;
            int size;

            synchronized(this)
            {
                start = _start;
                size = _size;
            }


            int length = start + size > _buffer.length ? _buffer.length - start : size;

            _inputBuffer.position(start);
            _inputBuffer.limit(start+length);
            processor.processBytes(_inputBuffer);
            final int consumed = length - _inputBuffer.remaining();

            synchronized(this)
            {
                _start += consumed;
                _size -= consumed;
            }

            if(!_inputBuffer.hasRemaining())
            {
                get(processor);
            }
        }
    }

    private int calculateSize(int size)
    {
        int n = 0;
        int s = size;
        do
        {
            s>>=1;
            n++;
        }
        while(s > 0);

        s = 1 << n;
        if(s < size)
        {
            s<<= 1;
        }
        return s;
    }
}
