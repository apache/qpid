/*
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
 */

package org.apache.qpid.amqp_1_0.jms.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageFormatException;

import org.apache.qpid.amqp_1_0.jms.BytesMessage;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class BytesMessageImpl extends MessageImpl implements BytesMessage
{
    private DataInputStream _dataAsInput;
    private DataOutputStream _dataAsOutput;
    private ByteArrayOutputStream _bytesOut;
    private Data _dataIn;

    // message created for reading
    protected BytesMessageImpl(Header header,
                               DeliveryAnnotations deliveryAnnotations,
                               MessageAnnotations messageAnnotations,
                               Properties properties,
                               ApplicationProperties appProperties,
                               Data data,
                               Footer footer,
                               SessionImpl session)
    {
        super(header, deliveryAnnotations, messageAnnotations, properties, appProperties, footer, session);
        _dataIn = data;
        final Binary dataBuffer = data.getValue();
        _dataAsInput = new DataInputStream(new ByteArrayInputStream(dataBuffer.getArray(),dataBuffer.getArrayOffset(),dataBuffer.getLength()));

    }

    // message created to be sent
    protected BytesMessageImpl(final SessionImpl session)
    {
        super(new Header(),
              new DeliveryAnnotations(new HashMap()), new MessageAnnotations(new HashMap()),
              new Properties(),
              new ApplicationProperties(new HashMap()),
              new Footer(Collections.EMPTY_MAP),
              session);

        _bytesOut = new ByteArrayOutputStream();
        _dataAsOutput = new DataOutputStream(_bytesOut);
    }


    private Data getDataSection()
    {
        if(_bytesOut != null)
        {
            return new Data(new Binary(_bytesOut.toByteArray()));
        }
        else
        {
            return _dataIn;
        }
    }

    @Override
    protected boolean isReadOnly()
    {
        return _dataIn != null;
    }

    public long getBodyLength() throws JMSException
    {
        checkReadable();
        return getDataSection().getValue().getLength();
    }

    public boolean readBoolean() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readBoolean();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }


    public byte readByte() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readByte();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public int readUnsignedByte() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readUnsignedByte();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public short readShort() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readShort();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public int readUnsignedShort() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readUnsignedShort();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public char readChar() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readChar();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public int readInt() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readInt();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public long readLong() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readLong();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public float readFloat() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readFloat();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public double readDouble() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readDouble();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public String readUTF() throws JMSException
    {
        checkReadable();
        try
        {
            return _dataAsInput.readUTF();
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public int readBytes(byte[] bytes) throws JMSException
    {

        return readBytes(bytes, bytes.length);
    }

    public int readBytes(byte[] bytes, int length) throws JMSException
    {
        checkReadable();

        try
        {
            int offset = 0;
            while(offset < length)
            {
                int read = _dataAsInput.read(bytes, offset, length - offset);
                if(read < 0)
                {
                    break;
                }
                offset += read;
            }

            if(offset == 0 && length != 0)
            {
                return -1;
            }
            else
            {
                return offset;
            }
        }
        catch (IOException e)
        {
            throw handleInputException(e);
        }
    }

    public void writeBoolean(boolean b) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeBoolean(b);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }

    }

    public void writeByte(byte b) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeByte(b);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeShort(short i) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeShort(i);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeChar(char c) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeChar(c);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeInt(int i) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeInt(i);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeLong(long l) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeLong(l);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeFloat(float v) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeFloat(v);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeDouble(double v) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeDouble(v);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeUTF(String s) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.writeUTF(s);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeBytes(byte[] bytes) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.write(bytes);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeBytes(byte[] bytes, int off, int len) throws JMSException
    {
        checkWritable();
        try
        {
            _dataAsOutput.write(bytes, off, len);
        }
        catch (IOException e)
        {
            throw handleOutputException(e);
        }
    }

    public void writeObject(Object o) throws JMSException
    {
        checkWritable();
        if(o == null)
        {
            throw new NullPointerException("Value passed to BytesMessage.writeObject() must be non null");
        }
        else if (o instanceof Boolean)
        {
            writeBoolean((Boolean)o);
        }
        else if (o instanceof Byte)
        {
            writeByte((Byte)o);
        }
        else if (o instanceof Short)
        {
            writeShort((Short)o);
        }
        else if (o instanceof Character)
        {
            writeChar((Character)o);
        }
        else if (o instanceof Integer)
        {
            writeInt((Integer)o);
        }
        else if(o instanceof Long)
        {
            writeLong((Long)o);
        }
        else if(o instanceof Float)
        {
            writeFloat((Float) o);
        }
        else if(o instanceof Double)
        {
            writeDouble((Double) o);
        }
        else if(o instanceof String)
        {
            writeUTF((String) o);
        }
        else if(o instanceof byte[])
        {
            writeBytes((byte[])o);
        }
        else
        {
            throw new MessageFormatException("Value passed to BytesMessage.writeObject() must be of primitive type.  Type passed was " + o.getClass().getName());
        }
    }

    public void reset() throws JMSException
    {
        if(_bytesOut != null)
        {
            byte[] data = _bytesOut.toByteArray();
            _dataIn = new Data(new Binary(data));
            _dataAsInput = new DataInputStream(new ByteArrayInputStream(data));
            _dataAsOutput = null;
            _bytesOut = null;
        }
        else
        {

            final Binary dataBuffer = _dataIn.getValue();
            _dataAsInput = new DataInputStream(new ByteArrayInputStream(dataBuffer.getArray(),dataBuffer.getArrayOffset(),dataBuffer.getLength()));

        }
    }

    private JMSException handleInputException(final IOException e)
    {
        JMSException ex;
        if(e instanceof EOFException)
        {
            ex = new MessageEOFException(e.getMessage());
        }
        else
        {
            ex = new MessageFormatException(e.getMessage());
        }
        ex.initCause(e);
        ex.setLinkedException(e);
        return ex;
    }

    private JMSException handleOutputException(final IOException e)
    {
        JMSException ex = new JMSException(e.getMessage());
        ex.initCause(e);
        ex.setLinkedException(e);
        return ex;
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _bytesOut = new ByteArrayOutputStream();
        _dataAsOutput = new DataOutputStream(_bytesOut);
        _dataAsInput = null;
        _dataIn = null;
    }

    @Override Collection<Section> getSections()
    {
        List<Section> sections = new ArrayList<Section>();
        sections.add(getHeader());
        if(getDeliveryAnnotations() != null && getDeliveryAnnotations().getValue() != null && !getDeliveryAnnotations().getValue().isEmpty())
        {
            sections.add(getDeliveryAnnotations());
        }
        if(getMessageAnnotations() != null && getMessageAnnotations().getValue() != null && !getMessageAnnotations().getValue().isEmpty())
        {
            sections.add(getMessageAnnotations());
        }
        sections.add(getProperties());
        sections.add(getApplicationProperties());
        sections.add(getDataSection());
        sections.add(getFooter());
        return sections;
    }

}
