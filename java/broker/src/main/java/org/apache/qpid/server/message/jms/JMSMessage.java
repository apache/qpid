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

import javax.jms.Message;
import javax.jms.JMSException;
import javax.jms.Destination;
import javax.jms.MessageNotWriteableException;
import java.util.Enumeration;

public class JMSMessage implements MessageDecorator
{

    private AMQMessage _message;

    public JMSMessage(AMQMessage message)
    {
        _message = message;
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
        return _message.getXXXMessageId();
    }

    public void setJMSMessageID(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setXXXMessageId(string);
    }

    public long getJMSTimestamp()
    {
        return _message.getTimestamp();
    }

    public void setJMSTimestamp(long l) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setTimestamp(l);
    }

    public byte[] getJMSCorrelationIDAsBytes()
    {
        return _message.getCorrelationId().getBytes();
    }

//    public void setJMSCorrelationIDAsBytes(byte[] bytes)
//    {
//    }

    public void setJMSCorrelationID(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setCorrelationId(string);
    }

    public String getJMSCorrelationID()
    {
        return _message.getCorrelationId();
    }

    public String getJMSReplyTo()
    {
        return _message.getReplyTo();
    }

    public void setJMSReplyTo(Destination destination) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setReplyTo(destination.toString());
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
        return _message.getDeliveryMode();
    }

    public void setJMSDeliveryMode(byte i) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setDeliveryMode(i);
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
        return _message.getType();
    }

    public void setJMSType(String string) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setType(string);
    }

    public long getJMSExpiration()
    {
        return _message.getExpiration();
    }

    public void setJMSExpiration(long l) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setExpiration(l);
    }

    public int getJMSPriority()
    {
        return _message.getPriority();
    }

    public void setJMSPriority(byte i) throws MessageNotWriteableException
    {
        checkWriteable();
        _message.setPriority(i);
    }

    public void clearProperties() throws MessageNotWriteableException
    {
        checkWriteable();
        _message.getApplicationHeaders().clear();
    }

    public boolean propertyExists(String string)
    {
        return _message.getApplicationHeaders().propertyExists(string);
    }

    public boolean getBooleanProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getBoolean(string);
    }

    public byte getByteProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getByte(string);
    }

    public short getShortProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getShort(string);
    }

    public int getIntProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getInteger(string);
    }

    public long getLongProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getLong(string);
    }

    public float getFloatProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getFloat(string);
    }

    public double getDoubleProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getDouble(string);
    }

    public String getStringProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getString(string);
    }

    public Object getObjectProperty(String string) throws JMSException
    {
        return _message.getApplicationHeaders().getObject(string);
    }

    public Enumeration getPropertyNames()
    {
        return _message.getApplicationHeaders().getPropertyNames();
    }

    public void setBooleanProperty(String string, boolean b) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setBoolean(string, b);
    }

    public void setByteProperty(String string, byte b) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setByte(string, b);
    }

    public void setShortProperty(String string, short i) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setShort(string, i);
    }

    public void setIntProperty(String string, int i) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setInteger(string, i);
    }

    public void setLongProperty(String string, long l) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setLong(string, l);
    }

    public void setFloatProperty(String string, float v) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setFloat(string, v);
    }

    public void setDoubleProperty(String string, double v) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setDouble(string, v);
    }

    public void setStringProperty(String string, String string1) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setString(string, string1);
    }

    public void setObjectProperty(String string, Object object) throws JMSException
    {
        checkWriteable();
        _message.getApplicationHeaders().setObject(string, object);
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
