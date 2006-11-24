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

import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.AMQException;
import org.apache.mina.common.ByteBuffer;

import javax.jms.JMSException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;
import javax.jms.MessageEOFException;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

public class JMSBytesMessage extends AbstractJMSMessage implements javax.jms.BytesMessage
{
    private static final String MIME_TYPE = "application/octet-stream";

    private boolean _readable = false;

    /**
     * The default initial size of the buffer. The buffer expands automatically.
     */
    private static final int DEFAULT_BUFFER_INITIAL_SIZE = 1024;

    JMSBytesMessage()
    {
        this(null);
    }

    /**
     * Construct a bytes message with existing data.
     *
     * @param data the data that comprises this message. If data is null, you get a 1024 byte buffer that is
     *             set to auto expand
     */
    JMSBytesMessage(ByteBuffer data)
    {
        super(data); // this instanties a content header
        getJmsContentHeaderProperties().setContentType(MIME_TYPE);

        if (_data == null)
        {
            _data = ByteBuffer.allocate(DEFAULT_BUFFER_INITIAL_SIZE);
            _data.setAutoExpand(true);
        }
        _readable = (data != null);
    }

    JMSBytesMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data)
            throws AMQException
    {
        // TODO: this casting is ugly. Need to review whole ContentHeaderBody idea
        super(messageNbr, (BasicContentHeaderProperties) contentHeader.properties, data);
        getJmsContentHeaderProperties().setContentType(MIME_TYPE);
        _readable = true;
    }

    public void clearBody() throws JMSException
    {
        _data.clear();
        _readable = false;
    }

    public String toBodyString() throws JMSException
    {
        checkReadable();
        try
        {
            return getText();
        }
        catch (IOException e)
        {
            throw new JMSException(e.toString());
        }
    }

    /**
     * We reset the stream before and after reading the data. This means that toString() will always output
     * the entire message and also that the caller can then immediately start reading as if toString() had
     * never been called.
     *
     * @return
     * @throws IOException
     */
    private String getText() throws IOException
    {
        // this will use the default platform encoding
        if (_data == null)
        {
            return null;
        }
        int pos = _data.position();
        _data.rewind();
        // one byte left is for the end of frame marker
        if (_data.remaining() == 0)
        {
            // this is really redundant since pos must be zero
            _data.position(pos);
            return null;
        }
        else
        {
            String data = _data.getString(Charset.forName("UTF8").newDecoder());
            _data.position(pos);
            return data;
        }
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    public long getBodyLength() throws JMSException
    {
        checkReadable();
        return _data.limit();
    }

    private void checkReadable() throws MessageNotReadableException
    {
        if (!_readable)
        {
            throw new MessageNotReadableException("You need to call reset() to make the message readable");
        }
    }

    /**
     * Check that there is at least a certain number of bytes available to read
     *
     * @param len the number of bytes
     * @throws MessageEOFException if there are less than len bytes available to read
     */
    private void checkAvailable(int len) throws MessageEOFException
    {
        if (_data.remaining() < len)
        {
            throw new MessageEOFException("Unable to read " + len + " bytes");
        }
    }

    private void checkWritable() throws MessageNotWriteableException
    {
        if (_readable)
        {
            throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
        }
    }

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        return _data.get() != 0;
    }

    public byte readByte() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        return _data.get();
    }

    public int readUnsignedByte() throws JMSException
    {
        checkReadable();
        checkAvailable(1);
        return _data.getUnsigned();
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        return _data.getShort();
    }

    public int readUnsignedShort() throws JMSException
    {
        checkReadable();
        checkAvailable(2);
        return _data.getUnsignedShort();
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
        return _data.getChar();
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        checkAvailable(4);
        return _data.getInt();
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        checkAvailable(8);
        return _data.getLong();
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        checkAvailable(4);
        return _data.getFloat();
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        checkAvailable(8);
        return _data.getDouble();
    }

    public String readUTF() throws JMSException
    {
        checkReadable();
        // we check only for one byte since theoretically the string could be only a
        // single byte when using UTF-8 encoding
        checkAvailable(1);
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
        int count = (_data.remaining() >= maxLength ? maxLength : _data.remaining());
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

    public void writeBoolean(boolean b) throws JMSException
    {
        checkWritable();
        _data.put(b ? (byte) 1 : (byte) 0);
    }

    public void writeByte(byte b) throws JMSException
    {
        checkWritable();
        _data.put(b);
    }

    public void writeShort(short i) throws JMSException
    {
        checkWritable();
        _data.putShort(i);
    }

    public void writeChar(char c) throws JMSException
    {
        checkWritable();
        _data.putChar(c);
    }

    public void writeInt(int i) throws JMSException
    {
        checkWritable();
        _data.putInt(i);
    }

    public void writeLong(long l) throws JMSException
    {
        checkWritable();
        _data.putLong(l);
    }

    public void writeFloat(float v) throws JMSException
    {
        checkWritable();
        _data.putFloat(v);
    }

    public void writeDouble(double v) throws JMSException
    {
        checkWritable();
        _data.putDouble(v);
    }

    public void writeUTF(String string) throws JMSException
    {
        checkWritable();
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

    public void reset() throws JMSException
    {
        //checkWritable();
        _data.flip();
        _readable = true;
    }

    public boolean isReadable()
    {
        return _readable;
    }
}
