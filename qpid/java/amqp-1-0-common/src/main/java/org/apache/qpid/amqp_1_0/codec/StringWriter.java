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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

public class StringWriter extends SimpleVariableWidthWriter<String>
{
    private static final Charset ENCODING_CHARSET;
    private static final byte ONE_BYTE_CODE;
    private static final byte FOUR_BYTE_CODE;
    static
    {
        Charset defaultCharset = Charset.defaultCharset();
        if(defaultCharset.name().equals("UTF-16") || defaultCharset.name().equals("UTF-16BE"))
        {
            ENCODING_CHARSET = defaultCharset;
            ONE_BYTE_CODE = (byte) 0xa2;
            FOUR_BYTE_CODE = (byte) 0xb2;
        }
        else
        {
            ENCODING_CHARSET = Charset.forName("UTF-8");
            ONE_BYTE_CODE = (byte) 0xa1;
            FOUR_BYTE_CODE = (byte) 0xb1;
        }
    }

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

    private final ValueCache<String, byte[]> _cachedEncodings = new ValueCache<String, byte[]>(10);


    @Override
    protected byte getFourOctetEncodingCode()
    {
        return FOUR_BYTE_CODE;
    }

    @Override
    protected byte getSingleOctetEncodingCode()
    {
        return ONE_BYTE_CODE;
    }

    @Override
    protected byte[] getByteArray(String value)
    {

        byte[] encoding;
        boolean isFull = _cachedEncodings.isFull();
        if(isFull)
        {
            encoding = _cachedEncodings.remove(value);
        }
        else
        {
            encoding = _cachedEncodings.get(value);
        }


        if(encoding == null)
        {
            ByteBuffer buf = ENCODING_CHARSET.encode(value);
            if(buf.hasArray() && buf.arrayOffset() == 0 && buf.limit()==buf.capacity())
            {
                encoding = buf.array();
            }
            else
            {
                byte[] bufArray = new byte[buf.limit()-buf.position()];
                buf.get(bufArray);
                encoding = bufArray;
            }
            _cachedEncodings.put(value,encoding);

        }
        else if(isFull)
        {
            _cachedEncodings.put(value,encoding);
        }

        return encoding;
    }

    @Override
    protected int getOffset()
    {
        return 0;
    }

    private static Factory<String> FACTORY = new Factory<String>()
                                            {

                                                public ValueWriter<String> newInstance(Registry registry)
                                                {
                                                    return new StringWriter();
                                                }
                                            };

    public static void register(ValueWriter.Registry registry)
    {
        registry.register(String.class, FACTORY);
    }
}