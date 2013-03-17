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

package org.apache.qpid.amqp_1_0.messaging;

import org.apache.qpid.amqp_1_0.codec.ValueWriter;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SectionEncoderImpl implements SectionEncoder
{
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(new byte[0]);
    private ValueWriter.Registry _registry;

    private int _totalSize = 0;

    private List<byte[]> _output = new ArrayList<byte[]>();
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;
    private ByteBuffer _current;

    public SectionEncoderImpl(final AMQPDescribedTypeRegistry describedTypeRegistry)
    {
        _registry = describedTypeRegistry;
        reset();
    }

    public void reset()
    {
        _totalSize = 0;
        _output.clear();
        _current = null;

    }

    public Binary getEncoding()
    {
        byte[] data = new byte[_totalSize];
        int offset = 0;
        for(byte[] src : _output)
        {
            int length = src.length;
            System.arraycopy(src, 0, data, offset, _totalSize - offset < length ? _totalSize - offset : length);
            offset+= length;
        }
        return new Binary(data);
    }

    public void encodeObject(Object obj)
    {
        final ValueWriter<Object> valueWriter = _registry.getValueWriter(obj);
        valueWriter.setValue(obj);
        int size = valueWriter.writeToBuffer(EMPTY_BYTE_BUFFER);

        byte[] data = new byte[size];
        _current = ByteBuffer.wrap(data);
        valueWriter.writeToBuffer(_current);
        _output.add(data);


        _totalSize += size;


    }

    public void encodeRaw(byte[] data)
    {
        if(_current == null)
        {
            byte[] buf = new byte[data.length];
            _current = ByteBuffer.wrap(buf);
            _output.add(buf);
        }
        int remaining = _current.remaining();
        int length = data.length;

        if(remaining < length)
        {
            _current.put(data,0,remaining);
            byte[] dst = new byte[length-remaining];
            _output.add(dst);
            _current = ByteBuffer.wrap(dst).put(data,remaining,length-remaining);
        }
        else
        {
            _current.put(data);
        }
        _totalSize += data.length;
    }

}
