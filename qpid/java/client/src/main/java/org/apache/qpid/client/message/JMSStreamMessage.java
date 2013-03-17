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

import java.io.EOFException;
import java.nio.ByteBuffer;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;
import javax.jms.StreamMessage;
import org.apache.qpid.AMQException;
import org.apache.qpid.typedmessage.TypedBytesContentReader;
import org.apache.qpid.typedmessage.TypedBytesContentWriter;
import org.apache.qpid.typedmessage.TypedBytesFormatException;

/**
 * @author Apache Software Foundation
 */
public class JMSStreamMessage extends AbstractBytesTypedMessage implements StreamMessage
{
    public static final String MIME_TYPE="jms/stream-message";


    private TypedBytesContentReader _typedBytesContentReader;
    private TypedBytesContentWriter _typedBytesContentWriter;

    public JMSStreamMessage(AMQMessageDelegateFactory delegateFactory)
    {
        super(delegateFactory,false);
        _typedBytesContentWriter = new TypedBytesContentWriter();

    }

    JMSStreamMessage(AMQMessageDelegateFactory delegateFactory, ByteBuffer data) throws AMQException
    {
        super(delegateFactory, data!=null);
        _typedBytesContentWriter = new TypedBytesContentWriter();

    }

    JMSStreamMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException
    {
        super(delegate, data!=null);
        _typedBytesContentReader = new TypedBytesContentReader(data);
    }

    public void reset()
    {
        setReadable(true);

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

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readBoolean();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }


    public byte readByte() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readByte();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readShort();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
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
        try
        {
            return _typedBytesContentReader.readChar();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readInt();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readLong();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readFloat();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readDouble();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public String readString() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readString();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public int readBytes(byte[] bytes) throws JMSException
    {
        if(bytes == null)
        {
            throw new IllegalArgumentException("Must provide non-null array to read into");
        }

        checkReadable();
        try
        {
            return _typedBytesContentReader.readBytes(bytes);
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }


    public Object readObject() throws JMSException
    {
        checkReadable();
        try
        {
            return _typedBytesContentReader.readObject();
        }
        catch (EOFException e)
        {
            throw new MessageEOFException(e.getMessage());
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }

    public void writeBoolean(boolean b) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeBoolean(b);
    }

    public void writeByte(byte b) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeByte(b);
    }

    public void writeShort(short i) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeShort(i);
    }

    public void writeChar(char c) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeChar(c);
    }

    public void writeInt(int i) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeInt(i);
    }

    public void writeLong(long l) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeLong(l);
    }

    public void writeFloat(float v) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeFloat(v);
    }

    public void writeDouble(double v) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeDouble(v);
    }

    public void writeString(String string) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeString(string);
    }

    public void writeBytes(byte[] bytes) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeBytes(bytes);
    }

    public void writeBytes(byte[] bytes, int offset, int length) throws JMSException
    {
        checkWritable();
        _typedBytesContentWriter.writeBytes(bytes, offset, length);
    }

    public void writeObject(Object object) throws JMSException
    {
        checkWritable();
        try
        {
            _typedBytesContentWriter.writeObject(object);
        }
        catch (TypedBytesFormatException e)
        {
            throw new MessageFormatException(e.getMessage());
        }
    }
}
