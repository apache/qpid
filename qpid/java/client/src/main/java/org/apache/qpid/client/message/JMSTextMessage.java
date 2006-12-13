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
import org.apache.qpid.AMQException;
import org.apache.mina.common.ByteBuffer;

import javax.jms.JMSException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;

public class JMSTextMessage extends AbstractJMSMessage implements javax.jms.TextMessage
{
    private static final String MIME_TYPE = "text/plain";

    private String _decodedValue;

    /**
     * This constant represents the name of a property that is set when the message payload is null.
     */
    private static final String PAYLOAD_NULL_PROPERTY = "JMS_QPID_NULL";

    JMSTextMessage() throws JMSException
    {
        this(null, null);
    }

    JMSTextMessage(ByteBuffer data, String encoding) throws JMSException
    {
        super(data); // this instantiates a content header
        getJmsContentHeaderProperties().setContentType(MIME_TYPE);
        getJmsContentHeaderProperties().setEncoding(encoding);
    }

    JMSTextMessage(long deliveryTag, BasicContentHeaderProperties contentHeader, ByteBuffer data)
            throws AMQException
    {
        super(deliveryTag, contentHeader, data);
        contentHeader.setContentType(MIME_TYPE);
        _data = data;
    }

    JMSTextMessage(ByteBuffer data) throws JMSException
    {
        this(data, null);
    }

    JMSTextMessage(String text) throws JMSException
    {
        super((ByteBuffer) null);
        setText(text);
    }

    public void clearBodyImpl() throws JMSException
    {
        if (_data != null)
        {
            _data.release();
        }
        _data = null;
        _decodedValue = null;
    }

    public String toBodyString() throws JMSException
    {
        return getText();
    }

    public void setData(ByteBuffer data)
    {
        _data = data;
    }

    public String getMimeType()
    {
        return MIME_TYPE;
    }

    public void setText(String text) throws JMSException
    {
        checkWritable();

        clearBody();
        try
        {
            if (text != null)
            {                
                _data = ByteBuffer.allocate(text.length());
                _data.limit(text.length()) ;
                //_data.sweep();
                _data.setAutoExpand(true);
                if (getJmsContentHeaderProperties().getEncoding() == null)
                {
                    _data.put(text.getBytes());
                }
                else
                {
                    _data.put(text.getBytes(getJmsContentHeaderProperties().getEncoding()));
                }
            }
            _decodedValue = text;
        }
        catch (UnsupportedEncodingException e)
        {
            // should never occur
            JMSException jmse = new JMSException("Unable to decode text data");
            jmse.setLinkedException(e);
        }
    }

    public String getText() throws JMSException
    {
        if (_data == null && _decodedValue == null)
        {
            return null;
        }
        else if (_decodedValue != null)
        {
            return _decodedValue;
        }
        else
        {
            _data.rewind();

            if (propertyExists(PAYLOAD_NULL_PROPERTY) && getBooleanProperty(PAYLOAD_NULL_PROPERTY))
            {
                return null;
            }
            if (getJmsContentHeaderProperties().getEncoding() != null)
            {
                try
                {
                    _decodedValue = _data.getString(Charset.forName(getJmsContentHeaderProperties().getEncoding()).newDecoder());
                }
                catch (CharacterCodingException e)
                {
                    JMSException je = new JMSException("Could not decode string data: " + e);
                    je.setLinkedException(e);
                    throw je;
                }
            }
            else
            {
                try
                {
                    _decodedValue = _data.getString(Charset.defaultCharset().newDecoder());
                }
                catch (CharacterCodingException e)
                {
                    JMSException je = new JMSException("Could not decode string data: " + e);
                    je.setLinkedException(e);
                    throw je;
                }
            }
            return _decodedValue;
        }
    }

    @Override
    public void prepareForSending() throws JMSException
    {
        super.prepareForSending();
        if (_data == null)
        {
            setBooleanProperty(PAYLOAD_NULL_PROPERTY, true);
        }
        else
        {
            removeProperty(PAYLOAD_NULL_PROPERTY);
        }
    }
}
