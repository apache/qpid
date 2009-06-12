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
package org.apache.qpid.agent.binding;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;
import org.apache.qpid.transport.codec.Decoder;
import org.apache.qpid.transport.codec.Encoder;

/**
 * Binding information from a java Map to a QMF schema.
 */
public class MapBinding implements TypeBinding
{
    private static Log log = LogFactory.getLog(MapBinding.class);
    protected BindingContext bctx;
    protected Class javaClass;

    public MapBinding(BindingContext bctx, Class javaClass)
    {
        this.bctx = bctx;
        this.javaClass = javaClass;
    }

    @SuppressWarnings("unchecked")
    public void encode(Encoder enc, Object value)
    {
        Map map = (Map) value;
        BBEncoder newEncoder = new BBEncoder(10);
        newEncoder.writeUint32(map.size());
        for (Object key : map.keySet())
        {
            String keyString = key.toString();
            Object mapValue = map.get(key);
            TypeBinding binding = bctx.getTypeBinding(mapValue.getClass());
            newEncoder.writeStr8(keyString);
            newEncoder.writeUint8(binding.getCode());
            binding.encode(newEncoder, mapValue);
        }
        enc.writeVbin32(newEncoder.buffer().array());
    }

    public Object decode(Decoder dec)
    {
        Map map = null;
        try
        {
            if (javaClass.isInterface())
            {
                map = new HashMap();
            } else
            {
                map = (Map) javaClass.newInstance();
            }
        } catch (Exception e)
        {
            throw new BindingException(
                    "Could not create a Map implementation for "
                            + javaClass.getName(), e);
        }
        BBDecoder newDecoder = new BBDecoder();
        newDecoder.init(ByteBuffer.wrap(dec.readVbin32()));
        long count = newDecoder.readUint32();
        while (count > 0)
        {
            String key = newDecoder.readStr8();
            short typeCode = newDecoder.readUint8();
            TypeBinding type = QMFTypeBinding.getType(typeCode);
            if (type == null)
            {
                type = bctx.getTypeBinding(Object.class);
            }
            map.put(key, type.decode(newDecoder));
            count -= 1;
        }
        return map;
    }

    // QMF List Type
    public short getCode()
    {
        return (short) 15;
    }

    @Override
    public Class<?> getJavaClass()
    {
        return javaClass;
    }

    @Override
    public String getRefClass()
    {
        return null;
    }

    @Override
    public String getRefPackage()
    {
        return null;
    }

    @Override
    public boolean isNative()
    {
        return true;
    }

    public boolean optionalDefault()
    {
        return false;
    }
}
