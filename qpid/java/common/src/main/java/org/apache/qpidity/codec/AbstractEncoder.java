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
package org.apache.qpidity.codec;

import java.nio.ByteBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.transport.Range;
import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.transport.Struct;
import org.apache.qpidity.transport.Type;

import static org.apache.qpidity.transport.util.Functions.*;


/**
 * AbstractEncoder
 *
 * @author Rafael H. Schloming
 */

abstract class AbstractEncoder implements Encoder
{

    private static Map<Class<?>,Type> ENCODINGS = new HashMap<Class<?>,Type>();
    static
    {
        ENCODINGS.put(String.class, Type.LONG_STRING);
        ENCODINGS.put(Long.class, Type.SIGNED_LONG);
        ENCODINGS.put(Integer.class, Type.SIGNED_INT);
        ENCODINGS.put(Short.class, Type.SIGNED_SHORT);
        ENCODINGS.put(Byte.class, Type.SIGNED_BYTE);
        ENCODINGS.put(Map.class, Type.TABLE);
        ENCODINGS.put(List.class, Type.SEQUENCE);
        ENCODINGS.put(Float.class, Type.FLOAT);
        ENCODINGS.put(Double.class, Type.DOUBLE);
        ENCODINGS.put(Character.class, Type.CHAR);
        ENCODINGS.put(byte[].class, Type.LONG_BINARY);
    }

    private final byte major;
    private final byte minor;
    private final boolean calcsize;

    protected AbstractEncoder(byte major, byte minor, boolean calcsize)
    {
        this.major = major;
        this.minor = minor;
        this.calcsize = calcsize;
    }

    protected AbstractEncoder(byte major, byte minor)
    {
        this(major, minor, true);
    }

    protected abstract void doPut(byte b);

    protected abstract void doPut(ByteBuffer src);

    protected void put(byte b)
    {
        flushBits();
        doPut(b);
    }

    protected void put(ByteBuffer src)
    {
        flushBits();
        doPut(src);
    }

    protected void put(byte[] bytes)
    {
        put(ByteBuffer.wrap(bytes));
    }

    private byte bits = 0x0;
    private byte nbits = 0;

    public void writeBit(boolean b)
    {
        if (b)
        {
            bits |= 1 << nbits;
        }

        nbits += 1;

        if (nbits == 8)
        {
            flushBits();
        }
    }

    private void flushBits()
    {
        if (nbits > 0)
        {
            doPut(bits);
            bits = 0x0;
            nbits = 0;
        }
    }

    public void flush()
    {
        flushBits();
    }

    public void writeOctet(short b)
    {
        assert b < 0x100;

        put((byte) b);
    }

    public void writeShort(int s)
    {
        assert s < 0x10000;

        put(lsb(s >>> 8));
        put(lsb(s));
    }

    public void writeLong(long i)
    {
        assert i < 0x100000000L;

        put(lsb(i >>> 24));
        put(lsb(i >>> 16));
        put(lsb(i >>> 8));
        put(lsb(i));
    }

    public void writeLonglong(long l)
    {
        for (int i = 0; i < 8; i++)
        {
            put(lsb(l >> (56 - i*8)));
        }
    }


    public void writeTimestamp(long l)
    {
        writeLonglong(l);
    }


    public void writeShortstr(String s)
    {
        if (s == null) { s = ""; }
        if (s.length() > 255) {
            throw new IllegalArgumentException(s);
        }
        writeOctet((short) s.length());
        put(ByteBuffer.wrap(s.getBytes()));
    }

    public void writeLongstr(String s)
    {
        if (s == null) { s = ""; }
        writeLong(s.length());
        put(ByteBuffer.wrap(s.getBytes()));
    }


    public void writeRfc1982LongSet(RangeSet ranges)
    {
        if (ranges == null)
        {
            writeShort((short) 0);
        }
        else
        {
            writeShort(ranges.size() * 8);
            for (Range range : ranges)
            {
                writeLong(range.getLower());
                writeLong(range.getUpper());
            }
        }
    }

    public void writeUuid(UUID uuid)
    {
        long msb = 0;
        long lsb = 0;
        if (uuid != null)
        {
            msb = uuid.getMostSignificantBits();
            uuid.getLeastSignificantBits();
        }
        writeLonglong(msb);
        writeLonglong(lsb);
    }

    public void writeContent(String c)
    {
        throw new Error("Deprecated");
    }

    public void writeLongStruct(Struct s)
    {
        if (s == null)
        {
            writeLong(0);
        }
        else
        {
            int size = 0;
            if (calcsize)
            {
                SizeEncoder sizer = new SizeEncoder(major, minor);
                sizer.writeShort(s.getEncodedType());
                s.write(sizer, major, minor);
                size = sizer.getSize();
            }

            writeLong(size);
            writeShort(s.getEncodedType());
            s.write(this, major, minor);
        }
    }

    private Type encoding(Object value)
    {
        if (value == null)
        {
            return Type.VOID;
        }

        Class klass = value.getClass();
        Type type = resolve(klass);

        if (type == null)
        {
            throw new IllegalArgumentException
                ("unable to resolve type: " + klass + ", " + value);
        }
        else
        {
            return type;
        }
    }

    private Type resolve(Class klass)
    {
        Type type = ENCODINGS.get(klass);
        if (type != null)
        {
            return type;
        }

        Class sup = klass.getSuperclass();
        if (sup != null)
        {
            type = resolve(klass.getSuperclass());

            if (type != null)
            {
                return type;
            }
        }

        for (Class iface : klass.getInterfaces())
        {
            type = resolve(iface);
            if (type != null)
            {
                return type;
            }
        }

        return null;
    }

    public void writeTable(Map<String,Object> table)
    {
        if (table == null)
        {
            writeLong(0);
            return;
        }

        int size = 0;
        if (calcsize)
        {
            SizeEncoder sizer = new SizeEncoder(major, minor);
            sizer.writeTableEntries(table);
            size = sizer.getSize();
        }

        writeLong(size);
        writeTableEntries(table);
    }

    protected void writeTableEntries(Map<String,Object> table)
    {
        for (Map.Entry<String,Object> entry : table.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();
            Type type = encoding(value);
            writeShortstr(key);
            put(type.code);
            write(type, value);
        }
    }

    public void writeSequence(List<Object> sequence)
    {
        int size = 0;
        if (calcsize)
        {
            SizeEncoder sizer = new SizeEncoder(major, minor);
            sizer.writeSequenceEntries(sequence);
            size = sizer.getSize();
        }

        writeLong(size);
        writeSequenceEntries(sequence);
    }

    protected void writeSequenceEntries(List<Object> sequence)
    {
        for (Object value : sequence)
        {
            Type type = encoding(value);
            put(type.code);
            write(type, value);
        }
    }

    public void writeArray(List<Object> array)
    {
        int size = 0;
        if (calcsize)
        {
            SizeEncoder sizer = new SizeEncoder(major, minor);
            sizer.writeArrayEntries(array);
            size = sizer.getSize();
        }

        writeLong(size);
        writeArrayEntries(array);
    }

    protected void writeArrayEntries(List<Object> array)
    {
        Type type;

        if (array.isEmpty())
        {
            type = Type.VOID;
        }
        else
        {
            type = encoding(array.get(0));
        }

        put(type.code);

        for (Object value : array)
        {
            write(type, value);
        }
    }

    private void writeSize(Type t, int size)
    {
        if (t.fixed)
        {
            if (size != t.width)
            {
                throw new IllegalArgumentException
                    ("size does not match fixed width " + t.width + ": " +
                     size);
            }
        }
        else
        {
            // XXX: should check lengths
            switch (t.width)
            {
            case 1:
                writeOctet((short) size);
                break;
            case 2:
                writeShort(size);
                break;
            case 4:
                writeLong(size);
                break;
            default:
                throw new IllegalStateException("irregular width: " + t);
            }
        }
    }

    private void writeBytes(Type t, byte[] bytes)
    {
        writeSize(t, bytes.length);
        put(bytes);
    }

    private <T> T coerce(Class<T> klass, Object value)
    {
        if (klass.isInstance(value))
        {
            return klass.cast(value);
        }
        else
        {
            throw new IllegalArgumentException("" + value);
        }
    }

    private void write(Type t, Object value)
    {
        switch (t)
        {
        case OCTET:
        case UNSIGNED_BYTE:
            writeOctet(coerce(Short.class, value));
            break;
        case SIGNED_BYTE:
            put(coerce(Byte.class, value));
            break;
        case CHAR:
            put((byte) ((char)coerce(Character.class, value)));
            break;
        case BOOLEAN:
            if (coerce(Boolean.class, value))
            {
                put((byte) 1);
            }
            else
            {
                put((byte) 0);
            }
            break;

        case TWO_OCTETS:
        case UNSIGNED_SHORT:
            writeShort(coerce(Integer.class, value));
            break;

        case SIGNED_SHORT:
            writeShort(coerce(Short.class, value));
            break;

        case FOUR_OCTETS:
        case UNSIGNED_INT:
            writeLong(coerce(Long.class, value));
            break;

        case UTF32_CHAR:
        case SIGNED_INT:
            writeLong(coerce(Integer.class, value));
            break;

        case FLOAT:
            writeLong(Float.floatToIntBits(coerce(Float.class, value)));
            break;

        case EIGHT_OCTETS:
        case SIGNED_LONG:
        case UNSIGNED_LONG:
        case DATETIME:
            writeLonglong(coerce(Long.class, value));
            break;

        case DOUBLE:
            long bits = Double.doubleToLongBits(coerce(Double.class, value));
            System.out.println("double out: " + bits);
            writeLonglong(bits);
            break;

        case SIXTEEN_OCTETS:
        case THIRTY_TWO_OCTETS:
        case SIXTY_FOUR_OCTETS:
        case _128_OCTETS:
        case SHORT_BINARY:
        case BINARY:
        case LONG_BINARY:
            writeBytes(t, coerce(byte[].class, value));
            break;

        case UUID:
            writeUuid(coerce(UUID.class, value));
            break;

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
            writeBytes(t, coerce(String.class, value).getBytes());
            break;

        case TABLE:
            writeTable((Map<String,Object>) coerce(Map.class, value));
            break;
        case SEQUENCE:
            writeSequence(coerce(List.class, value));
            break;
        case ARRAY:
            writeArray(coerce(List.class, value));
            break;

        case FIVE_OCTETS:
        case DECIMAL:
        case NINE_OCTETS:
        case LONG_DECIMAL:
            // XXX: what types are we supposed to use here?
            writeBytes(t, coerce(byte[].class, value));
            break;

        case VOID:
            break;

        default:
            writeBytes(t, coerce(byte[].class, value));
            break;
        }
    }

}
