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

import org.apache.qpid.AMQException;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import java.nio.ByteBuffer;

public class JMSBytesMessage extends AbstractBytesTypedMessage implements BytesMessage
{
    public static final String MIME_TYPE = "application/octet-stream";


    private TypedBytesContentReader _typedBytesContentReader;
    private TypedBytesContentWriter _typedBytesContentWriter;


    public JMSBytesMessage(AMQMessageDelegateFactory delegateFactory)
    {
        super(delegateFactory,false);
        _typedBytesContentWriter = new TypedBytesContentWriter();
    }

    JMSBytesMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException
    {
        super(delegate, data!=null);
        _typedBytesContentReader = new TypedBytesContentReader(data);
    }


    public void reset()
    {
        _readableMessage = true;

        if(_typedBytesContentReader != null)
        {
            _typedBytesContentReader.reset();
        }
        else if (_typedBytesContentWriter != null)
        {
            _typedBytesContentReader = new TypedBytesContentReader(_typedBytesContentWriter.getData());
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _typedBytesContentReader = null;
        _typedBytesContentWriter = new TypedBytesContentWriter();

    }

    protected String getMimeType()
    {
        return MIME_TYPE;
    }

    @Override
    public java.nio.ByteBuffer getData() throws JMSException
    {
        return _typedBytesContentWriter == null ? _typedBytesContentReader.getData() : _typedBytesContentWriter.getData();
    }

    public long getBodyLength() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.size();
    }

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        checkAvailable(1);

        return _typedBytesContentReader.readBooleanImpl();
    }

    private void checkAvailable(final int i) throws MessageEOFException
    {
        _typedBytesContentReader.checkAvailable(1);
    }

    public byte readByte() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        return _typedBytesContentReader.readByteImpl();
    }

    public int readUnsignedByte() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        return _typedBytesContentReader.readByteImpl() & 0xFF;
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        return _typedBytesContentReader.readShortImpl();
    }

    public int readUnsignedShort() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        return _typedBytesContentReader.readShortImpl() & 0xFFFF;
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
        checkAvailable(2);
        return _typedBytesContentReader.readCharImpl();
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        checkAvailable(4);
        return _typedBytesContentReader.readIntImpl();
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        checkAvailable(8);
        return _typedBytesContentReader.readLongImpl();
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        checkAvailable(4);
        return _typedBytesContentReader.readFloatImpl();
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        checkAvailable(8);
        return _typedBytesContentReader.readDoubleImpl();
    }

    public String readUTF() throws JMSException
    {
        checkReadable();
        // we check only for one byte since theoretically the string could be only a
        // single byte when using UTF-8 encoding

        return _typedBytesContentReader.readLengthPrefixedUTF();
    }

    public int readBytes(byte[] bytes) throws JMSException
    {
        if (bytes == null)
        {
            throw new IllegalArgumentException("byte array must not be null");
        }
        checkReadable();
        int count = (_typedBytesContentReader.remaining() >= bytes.length ? bytes.length : _typedBytesContentReader.remaining());
        if (count == 0)
        {
            return -1;
        }
        else
        {
            _typedBytesContentReader.readRawBytes(bytes, 0, count);
            return count;
        }
    }

    public int readBytes(byte[] bytes, int maxLength) throws JMSException
    {
        if (bytes == null)
        {
            throw new IllegalArgumentException("byte array must not be null");
        }
        if (maxLength > bytes.length)
        {
            throw new IllegalArgumentException("maxLength must be <= bytes.length");
        }
        checkReadable();
        int count = (_typedBytesContentReader.remaining() >= maxLength ? maxLength : _typedBytesContentReader.remaining());
        if (count == 0)
        {
            return -1;
        }
        else
        {
            _typedBytesContentReader.readRawBytes(bytes, 0, count);
            return count;
        }
    }


    public void writeBoolean(boolean b) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeBooleanImpl(b);
    }

    public void writeByte(byte b) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeByteImpl(b);
    }

    public void writeShort(short i) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeShortImpl(i);
    }

    public void writeChar(char c) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeCharImpl(c);
    }

    public void writeInt(int i) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeIntImpl(i);
    }

    public void writeLong(long l) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeLongImpl(l);
    }

    public void writeFloat(float v) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeFloatImpl(v);
    }

    public void writeDouble(double v) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeDoubleImpl(v);
    }

    public void writeUTF(String string) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeLengthPrefixedUTF(string);
    }

    public void writeBytes(byte[] bytes) throws JMSException
    {
        writeBytes(bytes, 0, bytes.length);
    }

    public void writeBytes(byte[] bytes, int offset, int length) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeBytesRaw(bytes, offset, length);
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
            writeUTF((String) object);
        }
        else
        {
            throw new MessageFormatException("Only primitives plus byte arrays and String are valid types");
        }
    }
}
