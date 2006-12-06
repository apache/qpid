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
    private Destination _replyTo;

    public JMSMessage(AMQMessage message)
    {
        _message = message;
        ContentHeaderBody contentHeader = message.getContentHeaderBody();
        _properties = (BasicContentHeaderProperties) contentHeader.properties;
    }

    protected void checkWriteable()
    {
//      The broker should not modify a message.
//        if (_readableMessage)
//        {
//        throw new MessageNotWriteableException("You need to call clearBody() to make the message writable");
//        }
    }


    public String getJMSMessageID()
    {
        return _properties.getMessageId();
    }

    public void setJMSMessageID(String string)
    {
        checkWriteable();
    }

    public long getJMSTimestamp()
    {
        return _properties.getTimestamp();
    }

    public void setJMSTimestamp(long l)
    {
        checkWriteable();
    }

    public byte[] getJMSCorrelationIDAsBytes()
    {
        return _properties.getCorrelationId().getBytes();
    }

    public void setJMSCorrelationIDAsBytes(byte[] bytes)
    {
        checkWriteable();
    }

    public void setJMSCorrelationID(String string)
    {
        checkWriteable();
    }

    public String getJMSCorrelationID()
    {
        return _properties.getCorrelationId();
    }

    public String getJMSReplyTo()
    {
        return _properties.getReplyTo();
    }

    public void setJMSReplyTo(Destination destination)
    {
        checkWriteable();
    }

    public String getJMSDestination()
    {
        //FIXME Currently the Destination has not been defined. 
        return "";
    }

    public void setJMSDestination(Destination destination)
    {
        checkWriteable();
    }

    public int getJMSDeliveryMode()
    {
        return _properties.getDeliveryMode();
    }

    public void setJMSDeliveryMode(int i)
    {
        checkWriteable();
    }

    public boolean getJMSRedelivered()
    {
        return _message.isRedelivered();
    }

    public void setJMSRedelivered(boolean b)
    {
        checkWriteable();
    }

    public String getJMSType()
    {
        return _properties.getType();
    }

    public void setJMSType(String string)
    {
        checkWriteable();
    }

    public long getJMSExpiration()
    {
        return _properties.getExpiration();
    }

    public void setJMSExpiration(long l)
    {
        checkWriteable();
    }

    public int getJMSPriority()
    {
        return _properties.getPriority();
    }

    public void setJMSPriority(int i)
    {
        checkWriteable();
    }

    public void clearProperties()
    {
        checkWriteable();
    }

    public boolean propertyExists(String string)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean getBooleanProperty(String string)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public byte getByteProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public short getShortProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getIntProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getLongProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public float getFloatProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public double getDoubleProperty(String string)
    {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getStringProperty(String string)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Object getObjectProperty(String string)
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Enumeration getPropertyNames()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setBooleanProperty(String string, boolean b)
    {
        checkWriteable();
    }

    public void setByteProperty(String string, byte b)
    {
        checkWriteable();
    }

    public void setShortProperty(String string, short i)
    {
        checkWriteable();
    }

    public void setIntProperty(String string, int i)
    {
        checkWriteable();
    }

    public void setLongProperty(String string, long l)
    {
        checkWriteable();
    }

    public void setFloatProperty(String string, float v)
    {
        checkWriteable();
    }

    public void setDoubleProperty(String string, double v)
    {
        checkWriteable();
    }

    public void setStringProperty(String string, String string1)
    {
        checkWriteable();
    }

    public void setObjectProperty(String string, Object object)
    {
        checkWriteable();
    }

    public void acknowledge()
    {
        checkWriteable();
    }

    public void clearBody()
    {
        checkWriteable();
    }

    public AMQMessage getAMQMessage()
    {
        return _message;
    }
}
