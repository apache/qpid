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
package org.apache.qpid.framing;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class FieldArray<T> extends AbstractCollection<T>
{

    private final Collection<T> _underlying;

    private FieldArray(final Collection<T> underlying)
    {
        _underlying = underlying;
    }

    @Override
    public Iterator<T> iterator()
    {
        return _underlying.iterator();
    }

    @Override
    public int size()
    {
        return _underlying.size();
    }

    public int getEncodingSize()
    {
        int size = 0;
        for( T obj : this)
        {
            size += AMQTypedValue.toTypedValue(obj).getEncodingSize()+1;
        }
        return size;
    }

    public static <T> FieldArray<T>  asFieldArray(Collection<T> collection)
    {
        if(collection instanceof FieldArray)
        {
            return (FieldArray<T>) collection;
        }
        else
        {
            validateCollection(collection);
            return new FieldArray<>(collection);
        }
    }

    private static final Set<Class<?>> SUPPORTED_CLASSES = new HashSet<>(Arrays.asList(Boolean.class,
                                                                                       Byte.class,
                                                                                       Short.class,
                                                                                       Character.class,
                                                                                       Integer.class,
                                                                                       Long.class,
                                                                                       Float.class,
                                                                                       Double.class,
                                                                                       String.class,
                                                                                       FieldTable.class,
                                                                                       Date.class,
                                                                                       BigDecimal.class,
                                                                                       byte[].class));

    private static <T> void validateCollection(final Collection<T> collection)
    {
        for(T val : collection)
        {
            if(!(val == null || SUPPORTED_CLASSES.contains(val.getClass()) || val instanceof Collection || val instanceof Map))
            {
                throw new IllegalArgumentException("Cannot convert an object of type " + val.getClass().getName());
            }

        }
    }

    public void writeToBuffer(final DataOutput buffer) throws IOException
    {
        buffer.writeInt(getEncodingSize());
        for( T obj : this)
        {
            AMQTypedValue.toTypedValue(obj).writeToBuffer(buffer);
        }
    }

    public static FieldArray<?> readFromBuffer(final DataInput buffer) throws IOException
    {
        ArrayList<Object> result = new ArrayList<>();
        int size = EncodingUtils.readInteger(buffer);
        byte[] data = new byte[size];
        buffer.readFully(data);
        ByteArrayDataInput slicedBuffer = new ByteArrayDataInput(data);
        while(slicedBuffer.available() > 0)
        {
            result.add(AMQTypedValue.readFromBuffer(slicedBuffer).getValue());
        }
        return new FieldArray<>(result);
    }
}
