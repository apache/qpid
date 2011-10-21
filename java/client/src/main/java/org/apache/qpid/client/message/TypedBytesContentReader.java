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
package org.apache.qpid.client.message;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.MessageNotReadableException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

class TypedBytesContentReader implements TypedBytesCodes
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
     * @throws javax.jms.MessageEOFException if there are less than len bytes available to read
     */
    protected void checkAvailable(int len) throws MessageEOFException
    {
        if (_data.remaining() < len)
        {
            throw new MessageEOFException("Unable to read " + len + " bytes");
        }
    }

    protected byte readWireType() throws MessageFormatException, MessageEOFException,
                                         MessageNotReadableException
    {
        checkAvailable(1);
        return _data.get();
    }

    protected boolean readBoolean() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a boolean");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    boolean readBooleanImpl()
    {
        return _data.get() != 0;
    }

    protected byte readByte() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a byte");
            }
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
        return result;
    }

    byte readByteImpl()
    {
        return _data.get();
    }

    protected short readShort() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a short");
            }
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
        return result;
    }

    short readShortImpl()
    {
        return _data.getShort();
    }

    /**
     * Note that this method reads a unicode character as two bytes from the stream
     *
     * @return the character read from the stream
     * @throws javax.jms.JMSException
     */
    protected char readChar() throws JMSException
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
                throw new MessageFormatException("Unable to convert " + wireType + " to a char");
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

    char readCharImpl()
    {
        return _data.getChar();
    }

    protected int readInt() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to an int");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    protected int readIntImpl()
    {
        return _data.getInt();
    }

    protected long readLong() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a long");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    long readLongImpl()
    {
        return _data.getLong();
    }

    protected float readFloat() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a float");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    float readFloatImpl()
    {
        return _data.getFloat();
    }

    protected double readDouble() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a double");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    double readDoubleImpl()
    {
        return _data.getDouble();
    }

    protected String readString() throws JMSException
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
                    throw new MessageFormatException("Unable to convert " + wireType + " to a String");
            }
            return result;
        }
        catch (RuntimeException e)
        {
            _data.position(position);
            throw e;
        }
    }

    protected String readStringImpl() throws JMSException
    {
        try
        {
            _charsetDecoder.reset();
            ByteBuffer dup = _data.duplicate();
            int pos = _data.position();
            byte b;
            while((b = _data.get()) != 0);
            dup.limit(_data.position()-1);
            return _charsetDecoder.decode(dup).toString();

        }
        catch (CharacterCodingException e)
        {
            JMSException jmse = new JMSException("Error decoding byte stream as a UTF8 string: " + e);
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }

    protected int readBytes(byte[] bytes) throws JMSException
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
                throw new MessageFormatException("Unable to convert " + wireType + " to a byte array");
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
                    throw new MessageEOFException("Byte array has stated length "
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

    protected Object readObject() throws JMSException
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

    public String readLengthPrefixedUTF() throws JMSException
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
            JMSException jmse = new JMSException("Error decoding byte stream as a UTF8 string: " + e);
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }
}
