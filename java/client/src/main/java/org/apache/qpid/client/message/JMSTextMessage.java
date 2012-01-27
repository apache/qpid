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
import org.apache.qpid.client.CustomJMSXProperty;

import javax.jms.JMSException;
import javax.jms.MessageFormatException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class JMSTextMessage extends AbstractJMSMessage implements javax.jms.TextMessage
{
    private static final String MIME_TYPE = "text/plain";

    private Exception _exception;
    private String _decodedValue;

    /**
     * This constant represents the name of a property that is set when the message payload is null.
     */
    private static final String PAYLOAD_NULL_PROPERTY = CustomJMSXProperty.JMS_AMQP_NULL.toString();
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private CharsetDecoder _decoder = DEFAULT_CHARSET.newDecoder();
    private CharsetEncoder _encoder = DEFAULT_CHARSET.newEncoder();

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    public JMSTextMessage(AMQMessageDelegateFactory delegateFactory) throws JMSException
    {
        super(delegateFactory, false); // this instantiates a content header
    }

    JMSTextMessage(AMQMessageDelegate delegate, ByteBuffer data)
            throws AMQException
    {
        super(delegate, data!=null);

        try
        {
            if(propertyExists(PAYLOAD_NULL_PROPERTY))
            {
                _decodedValue = null;
            }
            else
            {
                _decodedValue = _decoder.decode(data).toString();
            }
        }
        catch (CharacterCodingException e)
        {
            _exception = e;
        }
        catch (JMSException e)
        {
            _exception = e;
        }

    }

    public String toBodyString() throws JMSException
    {
        return getText();
    }

    protected String getMimeType()
    {
        return MIME_TYPE;
    }

    @Override
    public ByteBuffer getData() throws JMSException
    {
        _encoder.reset();
        try
        {
            if(_exception != null)
            {
                final MessageFormatException messageFormatException = new MessageFormatException("Cannot decode original message");
                messageFormatException.setLinkedException(_exception);
                throw messageFormatException;
            }
            else if(_decodedValue == null)
            {
                return EMPTY_BYTE_BUFFER;
            }
            else
            {
                return _encoder.encode(CharBuffer.wrap(_decodedValue));
            }
        }
        catch (CharacterCodingException e)
        {
            final JMSException jmsException = new JMSException("Cannot encode string in UFT-8: " + _decodedValue);
            jmsException.setLinkedException(e);
            throw jmsException;
        }
    }

    @Override
    public void clearBody() throws JMSException
    {
        super.clearBody();
        _decodedValue = null;
        _exception = null;
    }

    public void setText(String text) throws JMSException
    {
        checkWritable();

        clearBody();
        _decodedValue = text;

    }

    public String getText() throws JMSException
    {
        return _decodedValue;
    }

    @Override
    public void prepareForSending() throws JMSException
    {
        super.prepareForSending();
        if (_decodedValue == null)
        {
            setBooleanProperty(PAYLOAD_NULL_PROPERTY, true);
        }
        else
        {
            removeProperty(PAYLOAD_NULL_PROPERTY);
        }
    }


}
