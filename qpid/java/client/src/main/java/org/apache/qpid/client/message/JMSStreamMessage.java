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

import javax.jms.JMSException;
import javax.jms.StreamMessage;
import java.nio.ByteBuffer;

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
        return _typedBytesContentReader.readBoolean();
    }


    public byte readByte() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readByte();
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readShort();
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
        return _typedBytesContentReader.readChar();
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readInt();
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readLong();
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readFloat();
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readDouble();
    }

    public String readString() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readString();
    }

    public int readBytes(byte[] bytes) throws JMSException
    {
        if(bytes == null)
        {
            throw new IllegalArgumentException("Must provide non-null array to read into");
        }

        checkReadable();
        return _typedBytesContentReader.readBytes(bytes);
    }


    public Object readObject() throws JMSException
    {
        checkReadable();
        return _typedBytesContentReader.readObject();
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
        _typedBytesContentWriter.writeObject(object);
    }
}
