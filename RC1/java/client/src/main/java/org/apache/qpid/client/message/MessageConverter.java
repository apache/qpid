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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageEOFException;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import java.util.Enumeration;

public class MessageConverter
{

    /**
     * Log4J logger
     */
    protected final Logger _logger = LoggerFactory.getLogger(getClass());

    /**
     * AbstractJMSMessage which will hold the converted message
     */
    private AbstractJMSMessage _newMessage;

    public MessageConverter(AbstractJMSMessage message) throws JMSException
    {
        _newMessage = message;
    }

    public MessageConverter(BytesMessage message) throws JMSException
    {
        BytesMessage bytesMessage = (BytesMessage) message;
        bytesMessage.reset();

        JMSBytesMessage nativeMsg = new JMSBytesMessage();

        byte[] buf = new byte[1024];

        int len;

        while ((len = bytesMessage.readBytes(buf)) != -1)
        {
            nativeMsg.writeBytes(buf, 0, len);
        }

        _newMessage = nativeMsg;
        setMessageProperties(message);
    }

    public MessageConverter(MapMessage message) throws JMSException
    {
        MapMessage nativeMessage = new JMSMapMessage();

        Enumeration mapNames = message.getMapNames();
        while (mapNames.hasMoreElements())
        {
            String name = (String) mapNames.nextElement();
            nativeMessage.setObject(name, message.getObject(name));
        }

        _newMessage = (AbstractJMSMessage) nativeMessage;
        setMessageProperties(message);
    }

    public MessageConverter(ObjectMessage message) throws JMSException
    {
        ObjectMessage origMessage = (ObjectMessage) message;
        ObjectMessage nativeMessage = new JMSObjectMessage();

        nativeMessage.setObject(origMessage.getObject());

        _newMessage = (AbstractJMSMessage) nativeMessage;
        setMessageProperties(message);

    }

    public MessageConverter(TextMessage message) throws JMSException
    {
        TextMessage nativeMessage = new JMSTextMessage();

        nativeMessage.setText(message.getText());

        _newMessage = (AbstractJMSMessage) nativeMessage;
        setMessageProperties(message);
    }

    public MessageConverter(StreamMessage message) throws JMSException
    {
        StreamMessage nativeMessage = new JMSStreamMessage();

        try
        {
            message.reset();
            while (true)
            {
                nativeMessage.writeObject(message.readObject());
            }
        }
        catch (MessageEOFException e)
        {
            // we're at the end so don't mind the exception
        }

        _newMessage = (AbstractJMSMessage) nativeMessage;
        setMessageProperties(message);
    }

    public MessageConverter(Message message) throws JMSException
    {
        // Send a message with just properties.
        // Throwing away content
        BytesMessage nativeMessage = new JMSBytesMessage();

        _newMessage = (AbstractJMSMessage) nativeMessage;
        setMessageProperties(message);
    }

    public AbstractJMSMessage getConvertedMessage()
    {
        return _newMessage;
    }

    /**
     * Sets all message properties
     */
    protected void setMessageProperties(Message message) throws JMSException
    {
        setNonJMSProperties(message);
        setJMSProperties(message);
    }

    /**
     * Sets all non-JMS defined properties on converted message
     */
    protected void setNonJMSProperties(Message message) throws JMSException
    {
        Enumeration propertyNames = message.getPropertyNames();
        while (propertyNames.hasMoreElements())
        {
            String propertyName = String.valueOf(propertyNames.nextElement());
            // TODO: Shouldn't need to check for JMS properties here as don't think getPropertyNames() should return them
            if (!propertyName.startsWith("JMSX_"))
            {
                Object value = message.getObjectProperty(propertyName);
                _newMessage.setObjectProperty(propertyName, value);
            }
        }
    }

    /**
     * Exposed JMS defined properties on converted message:
     * JMSDestination   - we don't set here
     * JMSDeliveryMode  - set
     * JMSExpiration    - we don't set here
     * JMSPriority      - we don't set here
     * JMSMessageID     - we don't set here
     * JMSTimestamp     - we don't set here
     * JMSCorrelationID - set
     * JMSReplyTo       - set
     * JMSType          - set
     * JMSRedlivered    - we don't set here
     */
    protected void setJMSProperties(Message message) throws JMSException
    {
        _newMessage.setJMSDeliveryMode(message.getJMSDeliveryMode());

        if (message.getJMSReplyTo() != null)
        {
            _newMessage.setJMSReplyTo(message.getJMSReplyTo());
        }

        _newMessage.setJMSType(message.getJMSType());

        _newMessage.setJMSCorrelationID(message.getJMSCorrelationID());
    }

}
