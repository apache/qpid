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
package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.type.AmqpErrorException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

public class StringTypeConstructor extends VariableWidthTypeConstructor
{
    private Charset _charSet;

    private BinaryString _defaultBinaryString = new BinaryString();
    private ValueCache<BinaryString, String> _cachedValues = new ValueCache<BinaryString, String>(10);

    private static final class ValueCache<K,V> extends LinkedHashMap<K,V>
    {
        private final int _cacheSize;

        public ValueCache(int cacheSize)
        {
            _cacheSize = cacheSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
        {
            return size() > _cacheSize;
        }

        public boolean isFull()
        {
            return size() == _cacheSize;
        }
    }


    public static StringTypeConstructor getInstance(int i, Charset c)
    {
        return new StringTypeConstructor(i, c);
    }


    private StringTypeConstructor(int size, Charset c)
    {
        super(size);
        _charSet = c;
    }

    @Override
    public Object construct(final ByteBuffer in, boolean isCopy, ValueHandler handler) throws AmqpErrorException
    {
        int size;

        if(getSize() == 1)
        {
            size = in.get() & 0xFF;
        }
        else
        {
            size = in.getInt();
        }

        int origPosition = in.position();
        _defaultBinaryString.setData(in.array(), in.arrayOffset()+ origPosition, size);

        BinaryString binaryStr = _defaultBinaryString;

        boolean isFull = _cachedValues.isFull();

        String str = isFull ? _cachedValues.remove(binaryStr) : _cachedValues.get(binaryStr);

        if(str == null)
        {

            ByteBuffer dup = in.duplicate();
            try
            {
                dup.limit(dup.position()+size);
            }
            catch(IllegalArgumentException e)
            {
                throw new IllegalArgumentException("position: " + dup.position() + "size: " + size + " capacity: " + dup.capacity());
            }
            CharBuffer charBuf = _charSet.decode(dup);

            str = charBuf.toString();

            byte[] data = new byte[size];
            in.get(data);
            binaryStr = new BinaryString(data, 0, size);

            _cachedValues.put(binaryStr, str);
        }
        else if(isFull)
        {
            byte[] data = new byte[size];
            in.get(data);
            binaryStr = new BinaryString(data, 0, size);

            _cachedValues.put(binaryStr, str);
        }

        in.position(origPosition+size);

        return str;

    }

}