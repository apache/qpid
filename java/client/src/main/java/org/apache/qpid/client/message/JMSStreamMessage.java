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

import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.AMQException;
import org.apache.mina.common.ByteBuffer;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.StreamMessage;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

/**
 * @author Apache Software Foundation
 */
public class JMSStreamMessage extends AbstractBytesMessage implements StreamMessage
{
    public static final String MIME_TYPE="jms/stream-message";

    private static final String[] _typeNames = { "boolean",
                                                 "byte",
                                                 "byte array",
                                                 "short",
                                                 "char",
                                                 "int",
                                                 "long",
                                                 "float",
                                                 "double",
                                                 "utf string" };

    private static final byte BOOLEAN_TYPE = (byte) 1;

    private static final byte BYTE_TYPE = (byte) 2;

    private static final byte BYTEARRAY_TYPE = (byte) 3;

    private static final byte SHORT_TYPE = (byte) 4;

    private static final byte CHAR_TYPE = (byte) 5;

    private static final byte INT_TYPE = (byte) 6;

    private static final byte LONG_TYPE = (byte) 7;

    private static final byte FLOAT_TYPE = (byte) 8;

    private static final byte DOUBLE_TYPE = (byte) 9;

    private static final byte STRING_TYPE = (byte) 10;

    /**
     * This is set when reading a byte array. The readBytes(byte[]) method supports multiple calls to read
     * a byte array in multiple chunks, hence this is used to track how much is left to be read
     */
    private int _byteArrayRemaining = -1;

    JMSStreamMessage()
    {
        this(null);
    }
    
    /**
     * Construct a stream message with existing data.
     *
     * @param data the data that comprises this message. If data is null, you get a 1024 byte buffer that is
     *             set to auto expand
     */
    JMSStreamMessage(ByteBuffer data)
    {
        super(data); // this instanties a content header
    }


    JMSStreamMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data)
            throws AMQException
    {
        super(messageNbr, contentHeader, data);
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    private void readAndCheckType(byte type) throws MessageFormatException
    {
        if (_data.get() != type)
        {
            throw new MessageFormatException("Type " + _typeNames[type - 1] + " not found next in stream");
        }
    }

    private void writeTypeDiscriminator(byte type)
    {
        _data.put(type);
    }

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        readAndCheckType(BOOLEAN_TYPE);
        return readBooleanImpl();
    }

    private boolean readBooleanImpl()
    {
        return _data.get() != 0;
    }

    public byte readByte() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        readAndCheckType(BYTE_TYPE);
        return readByteImpl();
    }

    private byte readByteImpl()
    {
        return _data.get();
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        checkAvailable(3);
        readAndCheckType(SHORT_TYPE);
        return readShortImpl();
    }

    private short readShortImpl()
    {
        return _data.getShort();
    }

    /**
     * Note that this method reads a unicode character as two bytes from the stream
     *
     * @return the character read from the stream
     * @throws JMSException
     */
    public char readChar() throws JMSException
    {
        checkReadable();
        checkAvailable(3);
        readAndCheckType(CHAR_TYPE);
        return readCharImpl();
    }

    private char readCharImpl()
    {
        return _data.getChar();
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        checkAvailable(5);
        readAndCheckType(INT_TYPE);
        return readIntImpl();
    }

    private int readIntImpl()
    {
        return _data.getInt();
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        checkAvailable(9);
        readAndCheckType(LONG_TYPE);
        return readLongImpl();
    }

    private long readLongImpl()
    {
        return _data.getLong();
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        checkAvailable(5);
        readAndCheckType(FLOAT_TYPE);
        return readFloatImpl();
    }

    private float readFloatImpl()
    {
        return _data.getFloat();
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        checkAvailable(9);
        readAndCheckType(DOUBLE_TYPE);
        return readDoubleImpl();
    }

    private double readDoubleImpl()
    {
        return _data.getDouble();
    }

    public String readString() throws JMSException
    {
        checkReadable();
        // we check only for one byte plus the type byte since theoretically the string could be only a
        // single byte when using UTF-8 encoding
        checkAvailable(2);
        readAndCheckType(STRING_TYPE);
        return readStringImpl();
    }

    private String readStringImpl() throws JMSException
    {
        try
        {
            return _data.getString(Charset.forName("UTF-8").newDecoder());
        }
        catch (CharacterCodingException e)
        {
            JMSException je = new JMSException("Error decoding byte stream as a UTF8 string: " + e);
            je.setLinkedException(e);
            throw je;
        }
    }

    public int readBytes(byte[] bytes) throws JMSException
    {
        if (bytes == null)
        {
            throw new IllegalArgumentException("byte array must not be null");
        }
        checkReadable();
        // first call
        if (_byteArrayRemaining == -1)
        {
            // type discriminator plus array size
            checkAvailable(5);
            readAndCheckType(BYTEARRAY_TYPE);
            int size = _data.getInt();
            // size of -1 indicates null
            if (size == -1)
            {
                return -1;
            }
            else
            {
                if (size > _data.remaining())
                {
                    throw new MessageEOFException("Byte array has stated size " + size + " but message only contains " +
                                                  _data.remaining() + " bytes");
                }
                else
                {
                    _byteArrayRemaining = size;
                }
            }
        }

        return readBytesImpl(bytes);
    }

    private int readBytesImpl(byte[] bytes)
    {
        int count = (_byteArrayRemaining >= bytes.length ? bytes.length : _byteArrayRemaining);
        _byteArrayRemaining -= count;
        if (_byteArrayRemaining == 0)
        {
            _byteArrayRemaining = -1;            
        }
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

    public Object readObject() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        byte type = _data.get();
        Object result = null;
        switch (type)
        {
            case BOOLEAN_TYPE:
                result = readBooleanImpl();
                break;
            case BYTE_TYPE:
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
                    result = new byte[size];
                    readBytesImpl(new byte[size]);
                }
                break;
            case SHORT_TYPE:
                result = readShortImpl();
                break;
            case CHAR_TYPE:
                result = readCharImpl();
                break;
            case INT_TYPE:
                result = readIntImpl();
                break;
            case LONG_TYPE:
                result = readLongImpl();
                break;
            case FLOAT_TYPE:
                result = readFloatImpl();
                break;
            case DOUBLE_TYPE:
                result = readDoubleImpl();
                break;
            case STRING_TYPE:
                result = readStringImpl();
                break;
        }
        return result;
    }

    public void writeBoolean(boolean b) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(BOOLEAN_TYPE);
        _data.put(b ? (byte) 1 : (byte) 0);
    }

    public void writeByte(byte b) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(BYTE_TYPE);
        _data.put(b);
    }

    public void writeShort(short i) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(SHORT_TYPE);
        _data.putShort(i);
    }

    public void writeChar(char c) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(CHAR_TYPE);
        _data.putChar(c);
    }

    public void writeInt(int i) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(INT_TYPE);
        _data.putInt(i);
    }

    public void writeLong(long l) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(LONG_TYPE);
        _data.putLong(l);
    }

    public void writeFloat(float v) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(FLOAT_TYPE);
        _data.putFloat(v);
    }

    public void writeDouble(double v) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(DOUBLE_TYPE);
        _data.putDouble(v);
    }

    public void writeString(String string) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(STRING_TYPE);
        try
        {
            _data.putString(string, Charset.forName("UTF-8").newEncoder());
            // we must write the null terminator ourselves
            _data.put((byte)0);
        }
        catch (CharacterCodingException e)
        {
            JMSException ex = new JMSException("Unable to encode string: " + e);
            ex.setLinkedException(e);
            throw ex;
        }
    }

    public void writeBytes(byte[] bytes) throws JMSException
    {
        checkWritable();
        writeBytes(bytes, 0, bytes == null?0:bytes.length);
    }

    public void writeBytes(byte[] bytes, int offset, int length) throws JMSException
    {
        checkWritable();
        writeTypeDiscriminator(BYTEARRAY_TYPE);
        if (bytes == null)
        {
            _data.putInt(-1);
        }
        else
        {
            _data.putInt(length);
            _data.put(bytes, offset, length);
        }
    }

    public void writeObject(Object object) throws JMSException
    {
        checkWritable();
        if (object == null)
        {
            throw new NullPointerException("Argument must not be null");
        }
        Class clazz = object.getClass();
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
            throw new MessageFormatException("Only primitives plus byte arrays and String are valid types");
        }
    }
}
