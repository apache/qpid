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
package org.apache.qpid.amqp_1_0.codec;

import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class ArrayTypeConstructor implements TypeConstructor<Object[]>
{



    public Object[] construct(final ByteBuffer in, final ValueHandler handler) throws AmqpErrorException
    {
        int size = read(in);
        if(in.remaining() < size)
        {
            throw new AmqpErrorException(AmqpError.DECODE_ERROR,
                                         "Insufficient data to decode array - requires %d octects, only %d remaining.",
                                         size, in.remaining());
        }
        ByteBuffer dup = in.slice();
        dup.limit(size);
        in.position(in.position()+size);
        int count = read(dup);
        TypeConstructor t = handler.readConstructor(dup);
        List rval = new ArrayList(count);
        for(int i = 0; i < count; i++)
        {
            rval.add(t.construct(dup, handler));
        }
        if(dup.hasRemaining())
        {
            throw new AmqpErrorException(AmqpError.DECODE_ERROR,
                                         "Array incorrectly encoded, %d bytes remaining after decoding %d elements",
                                         dup.remaining(), count);
        }
        if(rval.size() == 0)
        {
            return null;
        }
        else
        {


            return rval.toArray((Object[])Array.newInstance(rval.get(0).getClass(), rval.size()));
        }
    }


    abstract int read(ByteBuffer in) throws AmqpErrorException;


    private static final ArrayTypeConstructor ONE_BYTE_SIZE_ARRAY = new ArrayTypeConstructor()
    {

        @Override int read(final ByteBuffer in) throws AmqpErrorException
        {
            if(!in.hasRemaining())
            {
                throw new AmqpErrorException(AmqpError.DECODE_ERROR, "Insufficient data to decode array");
            }
            return ((int)in.get()) & 0xff;
        }

    };

    private static final ArrayTypeConstructor FOUR_BYTE_SIZE_ARRAY = new ArrayTypeConstructor()
    {

        @Override int read(final ByteBuffer in) throws AmqpErrorException
        {
            if(in.remaining()<4)
            {
                throw new AmqpErrorException(AmqpError.DECODE_ERROR, "Insufficient data to decode array");
            }
            return in.getInt();
        }

    };

    public static ArrayTypeConstructor getOneByteSizeTypeConstructor()
    {
        return ONE_BYTE_SIZE_ARRAY;
    }

    public static ArrayTypeConstructor getFourByteSizeTypeConstructor()
    {
        return FOUR_BYTE_SIZE_ARRAY;
    }

}