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
package org.apache.qpid.disttest.client;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.qpid.disttest.DistributedTestException;
import org.apache.qpid.disttest.client.property.PropertyValue;
import org.apache.qpid.disttest.message.CreateProducerCommand;

public class MessageProvider
{
    public static final String TTL = "ttl";

    public static final String DELIVERY_MODE = "deliveryMode";

    public static final String PRIORITY = "priority";

    public static final String[] STANDARD_JMS_PROPERTIES = { "correlationID", DELIVERY_MODE,
            "expiration", "messageID", PRIORITY, "redelivered", "replyTo", "timestamp", "type", TTL };

    private Map<String, PropertyValue> _messageProperties;
    private ConcurrentMap<Integer, Future<String>> _payloads;

    public MessageProvider(Map<String, PropertyValue> messageProperties)
    {
        _messageProperties = messageProperties;
        _payloads = new ConcurrentHashMap<Integer, Future<String>>();
    }

    public Message nextMessage(Session session, CreateProducerCommand command) throws JMSException
    {
        Message message = createTextMessage(session, command);
        setMessageProperties(message);
        return message;
    }

    public boolean isPropertySet(String name)
    {
        return _messageProperties != null && _messageProperties.containsKey(name);
    }

    public void setMessageProperties(Message message) throws JMSException
    {
        if (_messageProperties != null)
        {
            for (Entry<String, PropertyValue> entry : _messageProperties.entrySet())
            {
                String propertyName = entry.getKey();
                Object propertyValue = entry.getValue().getValue();
                if (isStandardProperty(propertyName))
                {
                    setStandardProperty(message, propertyName, propertyValue);
                }
                else
                {
                    setCustomProperty(message, propertyName, propertyValue);
                }
            }
        }
    }

    protected void setCustomProperty(Message message, String propertyName, Object propertyValue) throws JMSException
    {
        if (propertyValue instanceof Integer)
        {
            message.setIntProperty(propertyName, ((Integer) propertyValue).intValue());
        }
        else if (propertyValue instanceof Long)
        {
            message.setLongProperty(propertyName, ((Long) propertyValue).longValue());
        }
        else if (propertyValue instanceof Boolean)
        {
            message.setBooleanProperty(propertyName, ((Boolean) propertyValue).booleanValue());
        }
        else if (propertyValue instanceof Byte)
        {
            message.setByteProperty(propertyName, ((Byte) propertyValue).byteValue());
        }
        else if (propertyValue instanceof Double)
        {
            message.setDoubleProperty(propertyName, ((Double) propertyValue).doubleValue());
        }
        else if (propertyValue instanceof Float)
        {
            message.setFloatProperty(propertyName, ((Float) propertyValue).floatValue());
        }
        else if (propertyValue instanceof Short)
        {
            message.setShortProperty(propertyName, ((Short) propertyValue).shortValue());
        }
        else if (propertyValue instanceof String)
        {
            message.setStringProperty(propertyName, (String) propertyValue);
        }
        else
        {
            message.setObjectProperty(propertyName, propertyValue);
        }
    }

    protected void setStandardProperty(Message message, String property, Object propertyValue) throws JMSException
    {
        String propertyName = "JMS" + StringUtils.capitalize(property);
        try
        {
            BeanUtils.setProperty(message, propertyName, propertyValue);
        }
        catch (IllegalAccessException e)
        {
            throw new DistributedTestException("Unable to set property " + propertyName + " :" + e.getMessage(), e);
        }
        catch (InvocationTargetException e)
        {
            if (e.getCause() instanceof JMSException)
            {
                throw ((JMSException) e.getCause());
            }
            else
            {
                throw new DistributedTestException("Unable to set property " + propertyName + " :" + e.getMessage(), e);
            }
        }
    }

    protected boolean isStandardProperty(String propertyName)
    {
        for (int i = 0; i < STANDARD_JMS_PROPERTIES.length; i++)
        {
            if (propertyName.equals(STANDARD_JMS_PROPERTIES[i]))
            {
                return true;
            }
        }
        return false;
    }

    protected Message createTextMessage(Session ssn, final CreateProducerCommand command) throws JMSException
    {
        String payload = getMessagePayload(command);
        TextMessage msg = ssn.createTextMessage();
        msg.setText(payload);
        return msg;
    }

    protected String getMessagePayload(final CreateProducerCommand command)
    {
        FutureTask<String> createTextFuture = new FutureTask<String>(new Callable<String>()
        {
            @Override
            public String call() throws Exception
            {
                return StringUtils.repeat("a", command.getMessageSize());
            }
        });

        Future<String> future = _payloads.putIfAbsent(command.getMessageSize(), createTextFuture);
        if (future == null)
        {
            createTextFuture.run();
            future = createTextFuture;
        }
        String payload = null;
        try
        {
            payload = future.get();
        }
        catch (Exception e)
        {
            throw new DistributedTestException("Unable to create message payload :" + e.getMessage(), e);
        }
        return payload;
    }

    @Override
    public String toString()
    {
        return "MessageProvider [_messageProperties=" + _messageProperties + "]";
    }

}
