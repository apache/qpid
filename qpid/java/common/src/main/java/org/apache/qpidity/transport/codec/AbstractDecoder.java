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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.transport.Struct;
import org.apache.qpidity.transport.Type;

import static org.apache.qpidity.transport.util.Functions.*;


/**
 * AbstractDecoder
 *
 * @author Rafael H. Schloming
 */

abstract class AbstractDecoder implements Decoder
{

    private final byte major;
    private final byte minor;
    private int count;

    protected AbstractDecoder(byte major, byte minor)
    {
        this.major = major;
        this.minor = minor;
        this.count = 0;
    }

    protected abstract byte doGet();

    protected abstract void doGet(byte[] bytes);

    protected byte get()
    {
        clearBits();
        byte b = doGet();
        count += 1;
        return b;
    }

    protected void get(byte[] bytes)
    {
        clearBits();
        doGet(bytes);
        count += bytes.length;
    }

    protected short uget()
    {
        return (short) (0xFF & get());
    }

    private byte bits = 0x0;
    private byte nbits = 0;

    public boolean readBit()
    {
        if (nbits == 0)
        {
            bits = get();
        }

        boolean result = (bits & (1 << nbits++)) != 0;
        return result;
    }

    private void clearBits()
    {
        bits = 0x0;
        nbits = 0;
    }

    public short readOctet()
    {
        return uget();
    }

    public int readShort()
    {
        int i = uget() << 8;
        i |= uget();
        return i;
    }

    public long readLong()
    {
        long l = uget() << 24;
        l |= uget() << 16;
        l |= uget() << 8;
        l |= uget();
        return l;
    }

    public long readLonglong()
    {
        long l = 0;
        for (int i = 0; i < 8; i++)
        {
            l |= ((long) (0xFF & get())) << (56 - i*8);
        }
        return l;
    }

    public long readTimestamp()
    {
        return readLonglong();
    }


    public String readShortstr()
    {
        short size = readOctet();
        byte[] bytes = new byte[size];
        get(bytes);
        return new String(bytes);
    }

    public String readLongstr()
    {
        long size = readLong();
        byte[] bytes = new byte[(int) size];
        get(bytes);
        return new String(bytes);
    }

    public RangeSet readRfc1982LongSet()
    {
        int count = readShort()/8;
        if (count == 0)
        {
            return null;
        }
        else
        {
            RangeSet ranges = new RangeSet();
            for (int i = 0; i < count; i++)
            {
                ranges.add(readLong(), readLong());
            }
            return ranges;
        }
    }

    public UUID readUuid()
    {
        long msb = readLonglong();
        long lsb = readLonglong();
        return new UUID(msb, lsb);
    }

    public String readContent()
    {
        throw new Error("Deprecated");
    }

    public Struct readLongStruct()
    {
        long size = readLong();
        if (size == 0)
        {
            return null;
        }
        else
        {
            int type = readShort();
            Struct result = Struct.create(type);
            result.read(this, major, minor);
            return result;
        }
    }

    public Map<String,Object> readTable()
    {
        long size = readLong();
        int start = count;
        Map<String,Object> result = new LinkedHashMap();
        while (count < start + size)
        {
            String key = readShortstr();
            byte code = get();
            Type t = getType(code);
            Object value = read(t);
            result.put(key, value);
        }
        return result;
    }

    public List<Object> readSequence()
    {
        long size = readLong();
        int start = count;
        List<Object> result = new ArrayList();
        while (count < start + size)
        {
            byte code = get();
            Type t = getType(code);
            Object value = read(t);
            result.add(value);
        }
        return result;
    }

    public List<Object> readArray()
    {
        long size = readLong();
        byte code = get();
        Type t = getType(code);
        long count = readLong();

        List<Object> result = new ArrayList<Object>();
        for (int i = 0; i < count; i++)
        {
            Object value = read(t);
            result.add(value);
        }
        return result;
    }

    private Type getType(byte code)
    {
        Type type = Type.get(code);
        if (type == null)
        {
            throw new IllegalArgumentException("unknown code: " + code);
        }
        else
        {
            return type;
        }
    }

    private long readSize(Type t)
    {
        if (t.fixed)
        {
            return t.width;
        }
        else
        {
            switch (t.width)
            {
            case 1:
                return readOctet();
            case 2:
                return readShort();
            case 4:
                return readLong();
            default:
                throw new IllegalStateException("irregular width: " + t);
            }
        }
    }

    private byte[] readBytes(Type t)
    {
        long size = readSize(t);
        byte[] result = new byte[(int) size];
        get(result);
        return result;
    }

    private Object read(Type t)
    {
        switch (t)
        {
        case OCTET:
        case UNSIGNED_BYTE:
            return readOctet();
        case SIGNED_BYTE:
            return get();
        case CHAR:
            return (char) get();
        case BOOLEAN:
            return get() > 0;

        case TWO_OCTETS:
        case UNSIGNED_SHORT:
            return readShort();

        case SIGNED_SHORT:
            return (short) readShort();

        case FOUR_OCTETS:
        case UNSIGNED_INT:
            return readLong();

        case UTF32_CHAR:
        case SIGNED_INT:
            return (int) readLong();

        case FLOAT:
            return Float.intBitsToFloat((int) readLong());

        case EIGHT_OCTETS:
        case SIGNED_LONG:
        case UNSIGNED_LONG:
        case DATETIME:
            return readLonglong();

        case DOUBLE:
            long bits = readLonglong();
            System.out.println("double in: " + bits);
            return Double.longBitsToDouble(bits);

        case SIXTEEN_OCTETS:
        case THIRTY_TWO_OCTETS:
        case SIXTY_FOUR_OCTETS:
        case _128_OCTETS:
        case SHORT_BINARY:
        case BINARY:
        case LONG_BINARY:
            return readBytes(t);

        case UUID:
            return readUuid();

        case SHORT_STRING:
        case SHORT_UTF8_STRING:
        case SHORT_UTF16_STRING:
        case SHORT_UTF32_STRING:
        case STRING:
        case UTF8_STRING:
        case UTF16_STRING:
        case UTF32_STRING:
        case LONG_STRING:
        case LONG_UTF8_STRING:
        case LONG_UTF16_STRING:
        case LONG_UTF32_STRING:
            // XXX: need to do character conversion
            return new String(readBytes(t));

        case TABLE:
            return readTable();
        case SEQUENCE:
            return readSequence();
        case ARRAY:
            return readArray();

        case FIVE_OCTETS:
        case DECIMAL:
        case NINE_OCTETS:
        case LONG_DECIMAL:
            // XXX: what types are we supposed to use here?
            return readBytes(t);

        case VOID:
            return null;

        default:
            return readBytes(t);
        }
    }

}
