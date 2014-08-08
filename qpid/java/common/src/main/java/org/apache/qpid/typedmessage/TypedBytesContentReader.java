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


import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class TypedBytesContentReader implements TypedBytesCodes
{

    private final ByteBuffer _data;
    private final int _position;
    private final int _limit;


    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private final CharsetDecoder _charsetDecoder = UTF8_CHARSET.newDecoder();

    private int _byteArrayRemaining = -1;


    public TypedBytesContentReader(final ByteBuffer data)
    {
        _data = data.duplicate();
        _position = _data.position();
        _limit = _data.limit();
    }

    /**
     * Check that there is at least a certain number of bytes available to read
     *
     * @param len the number of bytes
     * @throws EOFException if there are less than len bytes available to read
     */
    public void checkAvailable(int len) throws EOFException
    {
        if (_data.remaining() < len)
        {
            throw new EOFException("Unable to read " + len + " bytes");
        }
    }

    public byte readWireType() throws TypedBytesFormatException, EOFException
    {
        checkAvailable(1);
        return _data.get();
    }

    public boolean readBoolean() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        boolean result;
        try
        {
            switch (wireType)
            {
                case BOOLEAN_TYPE:
                    checkAvailable(1);
                    result = readBooleanImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Boolean.parseBoolean(readStringImpl());
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a boolean");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public boolean readBooleanImpl()
    {
        return _data.get() != 0;
    }

    public byte readByte() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        byte result;
        try
        {
            switch (wireType)
            {
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = readByteImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Byte.parseByte(readStringImpl());
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a byte");
            }
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
        return result;
    }

    public byte readByteImpl()
    {
        return _data.get();
    }

    public short readShort() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        short result;
        try
        {
            switch (wireType)
            {
                case SHORT_TYPE:
                    checkAvailable(2);
                    result = readShortImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Short.parseShort(readStringImpl());
                    break;
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = readByteImpl();
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a short");
            }
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
        return result;
    }

    public short readShortImpl()
    {
        return _data.getShort();
    }

    /**
     * Note that this method reads a unicode character as two bytes from the stream
     *
     * @return the character read from the stream
     * @throws EOFException if there are less than the required bytes available to read
     * @throws TypedBytesFormatException if the current write type is not compatible
     */
    public char readChar() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        try
        {
            if (wireType == NULL_STRING_TYPE)
            {
                throw new NullPointerException();
            }

            if (wireType != CHAR_TYPE)
            {
                _data.position(position);
                throw new TypedBytesFormatException("Unable to convert " + wireType + " to a char");
            }
            else
            {
                checkAvailable(2);
                return readCharImpl();
            }
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public char readCharImpl()
    {
        return _data.getChar();
    }

    public int readInt() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        int result;
        try
        {
            switch (wireType)
            {
                case INT_TYPE:
                    checkAvailable(4);
                    result = readIntImpl();
                    break;
                case SHORT_TYPE:
                    checkAvailable(2);
                    result = readShortImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Integer.parseInt(readStringImpl());
                    break;
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = readByteImpl();
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to an int");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public int readIntImpl()
    {
        return _data.getInt();
    }

    public long readLong() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        long result;
        try
        {
            switch (wireType)
            {
                case LONG_TYPE:
                    checkAvailable(8);
                    result = readLongImpl();
                    break;
                case INT_TYPE:
                    checkAvailable(4);
                    result = readIntImpl();
                    break;
                case SHORT_TYPE:
                    checkAvailable(2);
                    result = readShortImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Long.parseLong(readStringImpl());
                    break;
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = readByteImpl();
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a long");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public long readLongImpl()
    {
        return _data.getLong();
    }

    public float readFloat() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        float result;
        try
        {
            switch (wireType)
            {
                case FLOAT_TYPE:
                    checkAvailable(4);
                    result = readFloatImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Float.parseFloat(readStringImpl());
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a float");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public float readFloatImpl()
    {
        return _data.getFloat();
    }

    public double readDouble() throws TypedBytesFormatException, EOFException
    {
        int position = _data.position();
        byte wireType = readWireType();
        double result;
        try
        {
            switch (wireType)
            {
                case DOUBLE_TYPE:
                    checkAvailable(8);
                    result = readDoubleImpl();
                    break;
                case FLOAT_TYPE:
                    checkAvailable(4);
                    result = readFloatImpl();
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = Double.parseDouble(readStringImpl());
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a double");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public double readDoubleImpl()
    {
        return _data.getDouble();
    }

    public String readString() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        String result;
        try
        {
            switch (wireType)
            {
                case STRING_TYPE:
                    checkAvailable(1);
                    result = readStringImpl();
                    break;
                case NULL_STRING_TYPE:
                    result = null;
                    throw new NullPointerException("data is null");
                case BOOLEAN_TYPE:
                    checkAvailable(1);
                    result = String.valueOf(readBooleanImpl());
                    break;
                case LONG_TYPE:
                    checkAvailable(8);
                    result = String.valueOf(readLongImpl());
                    break;
                case INT_TYPE:
                    checkAvailable(4);
                    result = String.valueOf(readIntImpl());
                    break;
                case SHORT_TYPE:
                    checkAvailable(2);
                    result = String.valueOf(readShortImpl());
                    break;
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = String.valueOf(readByteImpl());
                    break;
                case FLOAT_TYPE:
                    checkAvailable(4);
                    result = String.valueOf(readFloatImpl());
                    break;
                case DOUBLE_TYPE:
                    checkAvailable(8);
                    result = String.valueOf(readDoubleImpl());
                    break;
                case CHAR_TYPE:
                    checkAvailable(2);
                    result = String.valueOf(readCharImpl());
                    break;
                default:
                    _data.position(position);
                    throw new TypedBytesFormatException("Unable to convert " + wireType + " to a String");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public String readStringImpl() throws TypedBytesFormatException
    {
        try
        {
            _charsetDecoder.reset();
            ByteBuffer dup = _data.duplicate();
            int pos = _data.position();
            byte b;
            while((b = _data.get()) != 0) {};
            dup.limit(_data.position()-1);
            return _charsetDecoder.decode(dup).toString();

        }
        catch (CharacterCodingException e)
        {
            TypedBytesFormatException jmse = new TypedBytesFormatException("Error decoding byte stream as a UTF8 string: " + e);
            jmse.initCause(e);
            throw jmse;
        }
    }

    public int readBytes(byte[] bytes) throws EOFException, TypedBytesFormatException
    {
        if (bytes == null)
        {
            throw new IllegalArgumentException("byte array must not be null");
        }
        // first call
        if (_byteArrayRemaining == -1)
        {
            // type discriminator checked separately so you get a MessageFormatException rather than
            // an EOF even in the case where both would be applicable
            checkAvailable(1);
            byte wireType = readWireType();
            if (wireType != BYTEARRAY_TYPE)
            {
                throw new TypedBytesFormatException("Unable to convert " + wireType + " to a byte array");
            }
            checkAvailable(4);
            int size = _data.getInt();
            // length of -1 indicates null
            if (size == -1)
            {
                return -1;
            }
            else
            {
                if (size > _data.remaining())
                {
                    throw new EOFException("Byte array has stated length "
                                                  + size
                                                  + " but message only contains "
                                                  +
                                                  _data.remaining()
                                                  + " bytes");
                }
                else
                {
                    _byteArrayRemaining = size;
                }
            }
        }
        else if (_byteArrayRemaining == 0)
        {
            _byteArrayRemaining = -1;
            return -1;
        }

        int returnedSize = readBytesImpl(bytes);
        if (returnedSize < bytes.length)
        {
            _byteArrayRemaining = -1;
        }
        return returnedSize;
    }

    private int readBytesImpl(byte[] bytes)
    {
        int count = (_byteArrayRemaining >= bytes.length ? bytes.length : _byteArrayRemaining);
        _byteArrayRemaining -= count;

        if (count == 0)
        {
            return 0;
        }
        else
        {
            _data.get(bytes, 0, count);
            return count;
        }
    }

    public Object readObject() throws EOFException, TypedBytesFormatException
    {
        int position = _data.position();
        byte wireType = readWireType();
        Object result = null;
        try
        {
            switch (wireType)
            {
                case BOOLEAN_TYPE:
                    checkAvailable(1);
                    result = readBooleanImpl();
                    break;
                case BYTE_TYPE:
                    checkAvailable(1);
                    result = readByteImpl();
                    break;
                case BYTEARRAY_TYPE:
                    checkAvailable(4);
                    int size = _data.getInt();
                    if (size == -1)
                    {
                        result = null;
                    }
                    else
                    {
                        _byteArrayRemaining = size;
                        byte[] bytesResult = new byte[size];
                        readBytesImpl(bytesResult);
                        result = bytesResult;
                    }
                    break;
                case SHORT_TYPE:
                    checkAvailable(2);
                    result = readShortImpl();
                    break;
                case CHAR_TYPE:
                    checkAvailable(2);
                    result = readCharImpl();
                    break;
                case INT_TYPE:
                    checkAvailable(4);
                    result = readIntImpl();
                    break;
                case LONG_TYPE:
                    checkAvailable(8);
                    result = readLongImpl();
                    break;
                case FLOAT_TYPE:
                    checkAvailable(4);
                    result = readFloatImpl();
                    break;
                case DOUBLE_TYPE:
                    checkAvailable(8);
                    result = readDoubleImpl();
                    break;
                case NULL_STRING_TYPE:
                    result = null;
                    break;
                case STRING_TYPE:
                    checkAvailable(1);
                    result = readStringImpl();
                    break;
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    public void reset()
    {
        _byteArrayRemaining = -1;
        _data.position(_position);
        _data.limit(_limit);
    }

    public ByteBuffer getData()
    {
        ByteBuffer buf = _data.duplicate();
        buf.position(_position);
        buf.limit(_limit);
        return buf;
    }

    public long size()
    {
        return _limit - _position;
    }

    public int remaining()
    {
        return _data.remaining();
    }

    public void readRawBytes(final byte[] bytes, final int offset, final int count)
    {
        _data.get(bytes, offset, count);
    }

    public String readLengthPrefixedUTF() throws TypedBytesFormatException
    {
        try
        {
            short length = readShortImpl();
            if(length == 0)
            {
                return "";
            }
            else
            {
                _charsetDecoder.reset();
                ByteBuffer encodedString = _data.slice();
                encodedString.limit(length);
                _data.position(_data.position()+length);
                CharBuffer string = _charsetDecoder.decode(encodedString);

                return string.toString();
            }
        }
        catch(CharacterCodingException e)
        {
            TypedBytesFormatException jmse = new TypedBytesFormatException("Error decoding byte stream as a UTF8 string: " + e);
            jmse.initCause(e);
            throw jmse;
        }
    }
}
