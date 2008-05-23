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
package org.apache.qpidity.transport.codec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.transport.Struct;


/**
 * Validator
 *
 */

public class Validator
{

    public static final void checkBit(boolean b)
    {
        // no illegal values
    }

    public static final void checkUint8(short s)
    {
        if (s > 0xFF || s < 0)
        {
            throw new IllegalArgumentException("" + s);
        }
    }

    public static final void checkUint16(int i)
    {
        if (i > 0xFFFF || i < 0)
        {
            throw new IllegalArgumentException("" + i);
        }
    }

    public static final void checkUint32(long l)
    {
        // XXX: we can't currently validate this because we do thinks
        // like pass in -1 for 0xFFFFFFFF
        // if (l > 0xFFFFFFFFL || l < 0)
        // {
        //     throw new IllegalArgumentException("" + l);
        // }
    }

    public static final void checkSequenceNo(int s)
    {
        // no illegal values
    }

    public static final void checkUint64(long l)
    {
        // no illegal values
    }

    public static final void checkDatetime(long l)
    {
        // no illegal values
    }

    public static final void checkUuid(UUID u)
    {
        // no illegal values
    }

    public static final void checkStr8(String value)
    {
        if (value != null && value.length() > 255)
        {
            throw new IllegalArgumentException("" + value);
        }
    }

    public static final void checkStr16(String value)
    {
        if (value != null && value.length() > 0xFFFF)
        {
            throw new IllegalArgumentException("" + value);
        }
    }

    public static final void checkVbin8(byte[] value)
    {
        if (value != null && value.length > 255)
        {
            throw new IllegalArgumentException("" + value);
        }
    }

    public static final void checkVbin16(byte[] value)
    {
        if (value != null && value.length > 0xFFFF)
        {
            throw new IllegalArgumentException("" + value);
        }
    }

    public static final void checkByteRanges(RangeSet r)
    {
        // no illegal values
    }

    public static final void checkSequenceSet(RangeSet r)
    {
        // no illegal values
    }

    public static final void checkVbin32(byte[] value)
    {
        // no illegal values
    }

    public static final void checkStruct32(Struct s)
    {
        // no illegal values
    }

    public static final void checkArray(List<Object> array)
    {
        if (array == null)
        {
            return;
        }

        for (Object o : array)
        {
            checkObject(o);
        }
    }

    public static final void checkMap(Map<String,Object> map)
    {
        if (map == null)
        {
            return;
        }

        for (Map.Entry<String,Object> entry : map.entrySet())
        {
            checkStr8(entry.getKey());
            checkObject(entry.getValue());
        }
    }

    public static final void checkObject(Object o)
    {
        if (o != null && AbstractEncoder.resolve(o.getClass()) == null)
        {
            throw new IllegalArgumentException("cannot encode " + o.getClass());
        }
    }

}
