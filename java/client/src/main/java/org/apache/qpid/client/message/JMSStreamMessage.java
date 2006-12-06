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

import javax.jms.StreamMessage;
import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

/**
 * @author Apache Software Foundation
 */
public class JMSStreamMessage extends AbstractBytesMessage implements StreamMessage
{
    private static final String MIME_TYPE="jms/stream-message";

    private static final String[] _typeNames = {
            "boolean",
            "byte",
            "short",
            "char",
            "int",
            "long",
            "float",
            "double",
            "utf string"};

    private static final byte BOOLEAN_TYPE = (byte) 1;

    private static final byte BYTE_TYPE = (byte) 2;

    private static final byte SHORT_TYPE = (byte) 3;

    private static final byte CHAR_TYPE = (byte) 4;

    private static final byte INT_TYPE = (byte) 5;

    private static final byte LONG_TYPE = (byte) 6;

    private static final byte FLOAT_TYPE = (byte) 7;

    private static final byte DOUBLE_TYPE = (byte) 8;

    private static final byte STRING_TYPE = (byte) 9;

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        readAndCheckType(BOOLEAN_TYPE);
        return _data.get() != 0;
    }

    private void readAndCheckType(byte type) throws MessageFormatException
    {
        if (_data.get() != type)
        {
            throw new MessageFormatException("Type " + _typeNames[type] + " not found next in stream");
        }
    }

    private void writeTypeDiscriminator(byte type)
    {
        _data.put(type);
    }

    public byte readByte() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        readAndCheckType(BYTE_TYPE);
        return _data.get();
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        checkAvailable(3);
        readAndCheckType(SHORT_TYPE);
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
        return _data.getChar();
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        checkAvailable(5);
        readAndCheckType(INT_TYPE);
        return _data.getInt();
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        checkAvailable(9);
        readAndCheckType(LONG_TYPE);
        return _data.getLong();
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        checkAvailable(5);
        readAndCheckType(FLOAT_TYPE);
        return _data.getFloat();
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        checkAvailable(9);
        readAndCheckType(DOUBLE_TYPE);
        return _data.getDouble();
    }    

    public String readString() throws JMSException
    {
        checkReadable();
        // we check only for one byte plus the type byte since theoretically the string could be only a
        // single byte when using UTF-8 encoding
        checkAvailable(2);
        readAndCheckType(STRING_TYPE);
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
        int count = (_data.remaining() >= bytes.length ? bytes.length : _data.remaining());
        if (count == 0)
        {
            return -1;
        }
        else
        {
            _data.get(bytes, 0, count);
            return count;
        }
    }

    public Object readObject() throws JMSException
    {
        return null;
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
        _data.put(bytes);
    }

    public void writeBytes(byte[] bytes, int offset, int length) throws JMSException
    {
        checkWritable();
        _data.put(bytes, offset, length);
    }

    public void writeObject(Object object) throws JMSException
    {
        checkWritable();
        if (object == null)
        {
            throw new NullPointerException("Argument must not be null");
        }
        _data.putObject(object);
    }
}
