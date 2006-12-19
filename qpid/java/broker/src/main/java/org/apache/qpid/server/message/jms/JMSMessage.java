/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.message.jms;

import org.apache.qpid.server.message.MessageDecorator;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.ContentHeaderBody;

import javax.jms.Message;
import javax.jms.JMSException;
import javax.jms.Destination;
import javax.jms.MessageNotWriteableException;
import java.util.Enumeration;

public class JMSMessage implements MessageDecorator
{

    private AMQMessage _message;
    private BasicContentHeaderProperties _properties;

    public JMSMessage(AMQMessage message)
    {
        _message = message;
        ContentHeaderBody contentHeader = message.getContentHeaderBody();
        _properties = (BasicContentHeaderProperties) contentHeader.properties;
    }

    protected void checkWriteable() throws MessageNotWriteableException
    {
        //The broker should not modify a message.
//        if (_readableMessage)
        {
          throw new MessageNotWriteableException("The broker should not modify a message.");
        }
    }


    public String getJMSMessageID()
    {
        return _properties.getMessageId();
    }

    public void setJMSMessageID(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setMessageId(string);
    }

    public long getJMSTimestamp()
    {
        return _properties.getTimestamp();
    }

    public void setJMSTimestamp(long l) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setTimestamp(l);
    }

    public byte[] getJMSCorrelationIDAsBytes()
    {
        return _properties.getCorrelationId().getBytes();
    }

//    public void setJMSCorrelationIDAsBytes(byte[] bytes)
//    {
//    }

    public void setJMSCorrelationID(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setCorrelationId(string);
    }

    public String getJMSCorrelationID()
    {
        return _properties.getCorrelationId();
    }

    public String getJMSReplyTo()
    {
        return _properties.getReplyTo();
    }

    public void setJMSReplyTo(Destination destination) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setReplyTo(destination.toString());
    }

    public String getJMSDestination()
    {
        //fixme should be a deestination
        return "";
    }

    public void setJMSDestination(Destination destination) throws MessageNotWriteableException
    {
        checkWriteable();
        //_properties.setDestination(destination.toString());
    }

    public int getJMSDeliveryMode()
    {
        return _properties.getDeliveryMode();
    }

    public void setJMSDeliveryMode(byte i) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setDeliveryMode(i);
    }

    public boolean getJMSRedelivered()
    {
        return _message.isRedelivered();
    }

    public void setJMSRedelivered(boolean b) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setRedelivered(b);
    }

    public String getJMSType()
    {
        return _properties.getType();
    }

    public void setJMSType(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setType(string);
    }

    public long getJMSExpiration()
    {
        return _properties.getExpiration();
    }

    public void setJMSExpiration(long l) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setExpiration(l);
    }

    public int getJMSPriority()
    {
        return _properties.getPriority();
    }

    public void setJMSPriority(byte i) throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.setPriority(i);
    }

    public void clearProperties() throws MessageNotWriteableException
    {
        checkWriteable();
        _properties.getJMSHeaders().clear();
    }

    public boolean propertyExists(String string)
    {
        return _properties.getJMSHeaders().propertyExists(string);
    }

    public boolean getBooleanProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getBoolean(string);
    }

    public byte getByteProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getByte(string);
    }

    public short getShortProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getShort(string);
    }

    public int getIntProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getInteger(string);
    }

    public long getLongProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getLong(string);
    }

    public float getFloatProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getFloat(string);
    }

    public double getDoubleProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getDouble(string);
    }

    public String getStringProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getString(string);
    }

    public Object getObjectProperty(String string) throws JMSException
    {
        return _properties.getJMSHeaders().getObject(string);
    }

    public Enumeration getPropertyNames()
    {
        return _properties.getJMSHeaders().getPropertyNames();
    }

    public void setBooleanProperty(String string, boolean b) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setBoolean(string, b);
    }

    public void setByteProperty(String string, byte b) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setByte(string, b);
    }

    public void setShortProperty(String string, short i) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setShort(string, i);
    }

    public void setIntProperty(String string, int i) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setInteger(string, i);
    }

    public void setLongProperty(String string, long l) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setLong(string, l);
    }

    public void setFloatProperty(String string, float v) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setFloat(string, v);
    }

    public void setDoubleProperty(String string, double v) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setDouble(string, v);
    }

    public void setStringProperty(String string, String string1) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setString(string, string1);
    }

    public void setObjectProperty(String string, Object object) throws JMSException
    {
        checkWriteable();
        _properties.getJMSHeaders().setObject(string, object);
    }

    public void acknowledge() throws MessageNotWriteableException
    {
        checkWriteable();
    }

    public void clearBody() throws MessageNotWriteableException
    {
        checkWriteable();
    }

    public AMQMessage getAMQMessage()
    {
        return _message;
    }
}
