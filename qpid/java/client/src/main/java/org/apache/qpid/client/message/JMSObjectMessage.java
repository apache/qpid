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
import org.apache.qpid.client.util.ClassLoadingAwareObjectInputStream;
import org.apache.qpid.util.ByteBufferInputStream;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import javax.jms.ObjectMessage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

public class JMSObjectMessage extends AbstractJMSMessage implements ObjectMessage
{
    public static final String MIME_TYPE = "application/java-object-stream";
    private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 256;

    private Serializable _readData;
    private ByteBuffer _data;

    private Exception _exception;

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);


    /**
     * Creates empty, writable message for use by producers
     * @param delegateFactory
     */
    public JMSObjectMessage(AMQMessageDelegateFactory delegateFactory)
    {
        super(delegateFactory, false);
    }

    /**
     * Creates read only message for delivery to consumers
     */

      JMSObjectMessage(AMQMessageDelegate delegate, final ByteBuffer data) throws AMQException
      {
          super(delegate, data!=null);

          try
          {
              _readData = read(data);
          }
          catch (IOException e)
          {
              _exception = e;
          }
          catch (ClassNotFoundException e)
          {
              _exception = e;
          }
      }

    public void clearBody() throws JMSException
    {
        super.clearBody();
        _exception = null;
        _readData = null;
        _data = null;
    }

    public String toBodyString() throws JMSException
    {
        return String.valueOf(getObject());
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    @Override
    public ByteBuffer getData() throws JMSException
    {
        if(_exception != null)
        {
            final MessageFormatException messageFormatException =
                    new MessageFormatException("Unable to deserialize message");
            messageFormatException.setLinkedException(_exception);
            throw messageFormatException;
        }
        if(_readData == null)
        {

            return _data == null ? EMPTY_BYTE_BUFFER : _data.duplicate();
        }
        else
        {
            try
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_OUTPUT_BUFFER_SIZE);
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(_readData);
                oos.flush();
                return ByteBuffer.wrap(baos.toByteArray());
            }
            catch (IOException e)
            {
                final JMSException jmsException = new JMSException("Unable to encode object of type: " +
                        _readData.getClass().getName() + ", value " + _readData);
                jmsException.setLinkedException(e);
                throw jmsException;
            }
        }
    }

    public void setObject(Serializable serializable) throws JMSException
    {
        checkWritable();
        clearBody();

        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(DEFAULT_OUTPUT_BUFFER_SIZE);
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(serializable);
            oos.flush();
            _data = ByteBuffer.wrap(baos.toByteArray());
        }
        catch (IOException e)
        {
            final JMSException jmsException = new JMSException("Unable to encode object of type: " +
                    serializable.getClass().getName() + ", value " + serializable);
            jmsException.setLinkedException(e);
            throw jmsException;
        }

    }

    public Serializable getObject() throws JMSException
    {
        if(_exception != null)
        {
            final MessageFormatException messageFormatException = new MessageFormatException("Unable to deserialize message");
            messageFormatException.setLinkedException(_exception);
            throw messageFormatException;
        }
        else if(_readData != null || _data == null)
        {
            return _readData;
        }
        else
        {
            Exception exception = null;

            final ByteBuffer data = _data.duplicate();
            try
            {
                return read(data);
            }
            catch (ClassNotFoundException e)
            {
                exception = e;
            }
            catch (IOException e)
            {
                exception = e;
            }

            JMSException jmsException = new JMSException("Could not deserialize object");
            jmsException.setLinkedException(exception);
            throw jmsException;
        }

    }

    private Serializable read(final ByteBuffer data) throws IOException, ClassNotFoundException
    {
        Serializable result = null;
        if (data != null && data.hasRemaining())
        {
            ClassLoadingAwareObjectInputStream in = new ClassLoadingAwareObjectInputStream(new ByteBufferInputStream(data));
            result = (Serializable) in.readObject();
        }
        return result;
    }
}
