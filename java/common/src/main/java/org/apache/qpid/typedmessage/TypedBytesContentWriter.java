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
package org.apache.qpid.typedmessage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

public class TypedBytesContentWriter implements TypedBytesCodes
{
    private final        ByteArrayOutputStream _baos = new ByteArrayOutputStream();
    private final        DataOutputStream      _data = new DataOutputStream(_baos);
    private static final Charset               UTF8 = Charset.forName("UTF-8");

    protected void writeTypeDiscriminator(byte type)
    {
        try
        {
            _data.writeByte(type);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    private RuntimeException handle(final IOException e)
    {
        RuntimeException jmsEx = new RuntimeException("Unable to write value: " + e.getMessage());
        return jmsEx;
    }


    public void writeBoolean(boolean b)
    {
        writeTypeDiscriminator(BOOLEAN_TYPE);
        writeBooleanImpl(b);
    }

    public void writeBooleanImpl(final boolean b)
    {
        try
        {
            _data.writeByte(b ? (byte) 1 : (byte) 0);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeByte(byte b)
    {
        writeTypeDiscriminator(BYTE_TYPE);
        writeByteImpl(b);
    }

    public void writeByteImpl(final byte b)
    {
        try
        {
            _data.writeByte(b);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeShort(short i)
    {
        writeTypeDiscriminator(SHORT_TYPE);
        writeShortImpl(i);
    }

    public void writeShortImpl(final short i)
    {
        try
        {
            _data.writeShort(i);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeChar(char c)
    {
        writeTypeDiscriminator(CHAR_TYPE);
        writeCharImpl(c);
    }

    public void writeCharImpl(final char c)
    {
        try
        {
            _data.writeChar(c);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeInt(int i)
    {
        writeTypeDiscriminator(INT_TYPE);
        writeIntImpl(i);
    }

    public void writeIntImpl(int i)
    {
        try
        {
            _data.writeInt(i);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeLong(long l)
    {
        writeTypeDiscriminator(LONG_TYPE);
        writeLongImpl(l);
    }

    public void writeLongImpl(final long l)
    {
        try
        {
            _data.writeLong(l);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeFloat(float v)
    {
        writeTypeDiscriminator(FLOAT_TYPE);
        writeFloatImpl(v);
    }

    public void writeFloatImpl(final float v)
    {
        try
        {
            _data.writeFloat(v);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeDouble(double v)
    {
        writeTypeDiscriminator(DOUBLE_TYPE);
        writeDoubleImpl(v);
    }

    public void writeDoubleImpl(final double v)
    {
        try
        {
            _data.writeDouble(v);
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeString(String string)
    {
        if (string == null)
        {
            writeTypeDiscriminator(NULL_STRING_TYPE);
        }
        else
        {
            writeTypeDiscriminator(STRING_TYPE);
            writeNullTerminatedStringImpl(string);
        }
    }

    public void writeNullTerminatedStringImpl(String string)

    {
        try
        {
            _data.write(string.getBytes(UTF8));
            _data.writeByte((byte) 0);
        }
        catch (IOException e)
        {
            throw handle(e);
        }

    }

    public void writeBytes(byte[] bytes)
    {
        writeBytes(bytes, 0, bytes == null ? 0 : bytes.length);
    }

    public void writeBytes(byte[] bytes, int offset, int length)
    {
        writeTypeDiscriminator(BYTEARRAY_TYPE);
        writeBytesImpl(bytes, offset, length);
    }

    public void writeBytesImpl(final byte[] bytes, final int offset, final int length)
    {
        try
        {
            if (bytes == null)
            {
                _data.writeInt(-1);
            }
            else
            {
                _data.writeInt(length);
                _data.write(bytes, offset, length);
            }
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }

    public void writeBytesRaw(final byte[] bytes, final int offset, final int length)
    {
        try
        {
            if (bytes != null)
            {
                _data.write(bytes, offset, length);
            }
        }
        catch (IOException e)
        {
            throw handle(e);
        }
    }


    public void writeObject(Object object) throws TypedBytesFormatException
    {
        Class clazz;

        if (object == null)
        {
            // string handles the output of null values
            clazz = String.class;
        }
        else
        {
            clazz = object.getClass();
        }

        if (clazz == Byte.class)
        {
            writeByte((Byte) object);
        }
        else if (clazz == Boolean.class)
        {
            writeBoolean((Boolean) object);
        }
        else if (clazz == byte[].class)
        {
            writeBytes((byte[]) object);
        }
        else if (clazz == Short.class)
        {
            writeShort((Short) object);
        }
        else if (clazz == Character.class)
        {
            writeChar((Character) object);
        }
        else if (clazz == Integer.class)
        {
            writeInt((Integer) object);
        }
        else if (clazz == Long.class)
        {
            writeLong((Long) object);
        }
        else if (clazz == Float.class)
        {
            writeFloat((Float) object);
        }
        else if (clazz == Double.class)
        {
            writeDouble((Double) object);
        }
        else if (clazz == String.class)
        {
            writeString((String) object);
        }
        else
        {
            throw new TypedBytesFormatException("Only primitives plus byte arrays and String are valid types");
        }
    }

    public ByteBuffer getData()
    {
        return ByteBuffer.wrap(_baos.toByteArray());
    }

    public void writeLengthPrefixedUTF(final String string) throws TypedBytesFormatException
    {
        try
        {
            CharsetEncoder encoder = UTF8.newEncoder();
            java.nio.ByteBuffer encodedString = encoder.encode(CharBuffer.wrap(string));

            writeShortImpl((short) encodedString.limit());
            while(encodedString.hasRemaining())
            {
                _data.writeByte(encodedString.get());
            }
        }
        catch (CharacterCodingException e)
        {
            TypedBytesFormatException jmse = new TypedBytesFormatException("Unable to encode string: " + e);
            jmse.initCause(e);
            throw jmse;
        }
        catch (IOException e)
        {
            throw handle(e);
        }

    }
}
