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

import java.io.UnsupportedEncodingException;

import java.nio.ByteBuffer;

import java.util.Collections;
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
        ENCODINGS.put(Boolean.class, Type.BOOLEAN);
        ENCODINGS.put(String.class, Type.STR16);
        ENCODINGS.put(Long.class, Type.INT64);
        ENCODINGS.put(Integer.class, Type.INT32);
        ENCODINGS.put(Short.class, Type.INT16);
        ENCODINGS.put(Byte.class, Type.INT8);
        ENCODINGS.put(Map.class, Type.MAP);
        ENCODINGS.put(List.class, Type.LIST);
        ENCODINGS.put(Float.class, Type.FLOAT);
        ENCODINGS.put(Double.class, Type.DOUBLE);
        ENCODINGS.put(Character.class, Type.CHAR);
        ENCODINGS.put(byte[].class, Type.VBIN32);
    }

    protected Sizer sizer()
    {
        return new SizeEncoder();
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

    public void writeUint8(short b)
    {
        assert b < 0x100;

        put((byte) b);
    }

    public void writeUint16(int s)
    {
        assert s < 0x10000;

        put(lsb(s >>> 8));
        put(lsb(s));
    }

    public void writeUint32(long i)
    {
        assert i < 0x100000000L;

        put(lsb(i >>> 24));
        put(lsb(i >>> 16));
        put(lsb(i >>> 8));
        put(lsb(i));
    }

    public void writeSequenceNo(int i)
    {
        writeUint32(i);
    }

    public void writeUint64(long l)
    {
        for (int i = 0; i < 8; i++)
        {
            put(lsb(l >> (56 - i*8)));
        }
    }


    public void writeDatetime(long l)
    {
        writeUint64(l);
    }

    private static final String checkLength(String s, int n)
    {
        if (s == null)
        {
            return "";
        }

        if (s.length() > n)
        {
            throw new IllegalArgumentException("string too long: " + s);
        }
        else
        {
            return s;
        }
    }

    private static final byte[] encode(String s, String charset)
    {
        try
        {
            return s.getBytes(charset);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void writeStr8(String s)
    {
        s = checkLength(s, 255);
        writeUint8((short) s.length());
        put(ByteBuffer.wrap(encode(s, "UTF-8")));
    }

    public void writeStr16(String s)
    {
        s = checkLength(s, 65535);
        writeUint16(s.length());
        put(ByteBuffer.wrap(encode(s, "UTF-8")));
    }

    public void writeVbin8(byte[] bytes)
    {
        if (bytes == null) { bytes = new byte[0]; }
        if (bytes.length > 255)
        {
            throw new IllegalArgumentException("array too long: " + bytes.length);
        }
        writeUint8((short) bytes.length);
        put(ByteBuffer.wrap(bytes));
    }

    public void writeVbin16(byte[] bytes)
    {
        if (bytes == null) { bytes = new byte[0]; }
        writeUint16(bytes.length);
        put(ByteBuffer.wrap(bytes));
    }

    public void writeVbin32(byte[] bytes)
    {
        if (bytes == null) { bytes = new byte[0]; }
        writeUint32(bytes.length);
        put(ByteBuffer.wrap(bytes));
    }

    public void writeSequenceSet(RangeSet ranges)
    {
        if (ranges == null)
        {
            writeUint16((short) 0);
        }
        else
        {
            writeUint16(ranges.size() * 8);
            for (Range range : ranges)
            {
                writeSequenceNo(range.getLower());
                writeSequenceNo(range.getUpper());
            }
        }
    }

    public void writeByteRanges(RangeSet ranges)
    {
        throw new Error("not implemented");
    }

    public void writeUuid(UUID uuid)
    {
        long msb = 0;
        long lsb = 0;
        if (uuid != null)
        {
            msb = uuid.getMostSignificantBits();
            lsb = uuid.getLeastSignificantBits();
        }
        writeUint64(msb);
        writeUint64(lsb);
    }

    public void writeStruct(int type, Struct s)
    {
        boolean empty = false;
        if (s == null)
        {
            s = Struct.create(type);
            empty = true;
        }

        int width = s.getSizeWidth();
        if (width > 0)
        {
            if (empty)
            {
                writeSize(width, 0);
            }
            else
            {
                Sizer sizer = sizer();
                s.write(sizer);
                writeSize(width, sizer.size());
            }
        }

        if (type > 0)
        {
            writeUint16(type);
        }

        s.write(this);
    }

    public void writeStruct32(Struct s)
    {
        if (s == null)
        {
            writeUint32(0);
        }
        else
        {
            Sizer sizer = sizer();
            sizer.writeUint16(s.getEncodedType());
            s.write(sizer);
            writeUint32(sizer.size());
            writeUint16(s.getEncodedType());
            s.write(this);
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

    public void writeMap(Map<String,Object> map)
    {
        if (map == null)
        {
            writeUint32(0);
            return;
        }

        Sizer sizer = sizer();
        sizer.writeMap(map);
        // XXX: - 4
        writeUint32(sizer.size() - 4);
        writeUint32(map.size());
        writeMapEntries(map);
    }

    protected void writeMapEntries(Map<String,Object> map)
    {
        for (Map.Entry<String,Object> entry : map.entrySet())
        {
            String key = entry.getKey();
            Object value = entry.getValue();
            Type type = encoding(value);
            writeStr8(key);
            put(type.code);
            write(type, value);
        }
    }

    public void writeList(List<Object> list)
    {
        Sizer sizer = sizer();
        sizer.writeList(list);
        // XXX: - 4
        writeUint32(sizer.size() - 4);
        writeListEntries(list);
    }

    protected void writeListEntries(List<Object> list)
    {
        for (Object value : list)
        {
            Type type = encoding(value);
            put(type.code);
            write(type, value);
        }
    }

    public void writeArray(List<Object> array)
    {
        if (array == null)
        {
            array = Collections.EMPTY_LIST;
        }

        Sizer sizer = sizer();
        sizer.writeArray(array);
        // XXX: -4
        writeUint32(sizer.size() - 4);
        writeArrayEntries(array);
    }

    protected void writeArrayEntries(List<Object> array)
    {
        Type type;

        if (array.isEmpty())
        {
            return;
        }
        else
        {
            type = encoding(array.get(0));
        }

        put(type.code);

        writeUint32(array.size());

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
            writeSize(t.width, size);
        }
    }

    private void writeSize(int width, int size)
    {
        // XXX: should check lengths
        switch (width)
        {
        case 1:
            writeUint8((short) size);
            break;
        case 2:
            writeUint16(size);
            break;
        case 4:
            writeUint32(size);
            break;
        default:
            throw new IllegalStateException("illegal width: " + width);
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
        case BIN8:
        case UINT8:
            writeUint8(coerce(Short.class, value));
            break;
        case INT8:
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

        case BIN16:
        case UINT16:
            writeUint16(coerce(Integer.class, value));
            break;

        case INT16:
            writeUint16(coerce(Short.class, value));
            break;

        case BIN32:
        case UINT32:
            writeUint32(coerce(Long.class, value));
            break;

        case CHAR_UTF32:
        case INT32:
            writeUint32(coerce(Integer.class, value));
            break;

        case FLOAT:
            writeUint32(Float.floatToIntBits(coerce(Float.class, value)));
            break;

        case BIN64:
        case UINT64:
        case INT64:
        case DATETIME:
            writeUint64(coerce(Long.class, value));
            break;

        case DOUBLE:
            long bits = Double.doubleToLongBits(coerce(Double.class, value));
            writeUint64(bits);
            break;

        case UUID:
            writeUuid(coerce(UUID.class, value));
            break;

        case STR8:
            writeStr8(coerce(String.class, value));
            break;

        case STR16:
            writeStr16(coerce(String.class, value));
            break;

        case STR8_LATIN:
        case STR8_UTF16:
        case STR16_LATIN:
        case STR16_UTF16:
            // XXX: need to do character conversion
            writeBytes(t, coerce(String.class, value).getBytes());
            break;

        case MAP:
            writeMap((Map<String,Object>) coerce(Map.class, value));
            break;
        case LIST:
            writeList(coerce(List.class, value));
            break;
        case ARRAY:
            writeArray(coerce(List.class, value));
            break;
        case STRUCT32:
            writeStruct32(coerce(Struct.class, value));
            break;

        case BIN40:
        case DEC32:
        case BIN72:
        case DEC64:
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
