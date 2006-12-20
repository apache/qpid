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

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import java.io.*;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;

public class JMSObjectMessage extends AbstractJMSMessage implements ObjectMessage
{
    static final String MIME_TYPE = "application/java-object-stream";

    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Creates empty, writable message for use by producers
     */
    JMSObjectMessage()
    {
        this(null);
    }

    private JMSObjectMessage(ByteBuffer data)
    {
        super(data);
        if (data == null)
        {
            _data = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            _data.setAutoExpand(true);
        }
        getJmsContentHeaderProperties().setContentType(MIME_TYPE);
    }

    /**
     * Creates read only message for delivery to consumers
     */
    JMSObjectMessage(long messageNbr, ContentHeaderBody contentHeader, ByteBuffer data) throws AMQException
    {
        super(messageNbr, (BasicContentHeaderProperties) contentHeader.properties, data);
    }

    public void clearBodyImpl() throws JMSException
    {
        if (_data != null)
        {
            _data.release();
        }
        _data = null;

    }

    public String toBodyString() throws JMSException
    {
        return toString(_data);
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    public void setObject(Serializable serializable) throws JMSException
    {
        checkWritable();

        if (_data == null)
        {
            _data = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE);
            _data.setAutoExpand(true);
        }
        else
        {
            _data.rewind();
        }

        try
        {
            ObjectOutputStream out = new ObjectOutputStream(_data.asOutputStream());
            out.writeObject(serializable);
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            throw new MessageFormatException("Message not serializable: " + e);
        }

    }
  
    public Serializable getObject() throws JMSException
    {
        ObjectInputStream in = null;
        if (_data == null)
        {
            return null;
        }

        try
        {
            _data.rewind();
            in = new ObjectInputStream(_data.asInputStream());
            return (Serializable) in.readObject();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new MessageFormatException("Could not deserialize message: " + e);
        }
        catch (ClassNotFoundException e)
        {
            e.printStackTrace();
            throw new MessageFormatException("Could not deserialize message: " + e);
        }
        finally
        {
            _data.rewind();
            close(in);
        }
    }

    private static void close(InputStream in)
    {
        try
        {
            if (in != null)
            {
                in.close();
            }
        }
        catch (IOException ignore)
        {
        }
    }

    private static String toString(ByteBuffer data)
    {
        if (data == null)
        {
            return null;
        }
        int pos = data.position();
        try
        {
            return data.getString(Charset.forName("UTF8").newDecoder());
        }
        catch (CharacterCodingException e)
        {
            return null;
        }
        finally
        {
            data.position(pos);
        }
    }
}
