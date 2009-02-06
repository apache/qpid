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
package org.apache.qpid.server.filter;
//
// Based on like named file from r450141 of the Apache ActiveMQ project <http://www.activemq.org/site/home.html>
//

import java.util.HashMap;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.CommonContentHeaderProperties;
import org.apache.qpid.server.queue.Filterable;

/**
 * Represents a property  expression
 */
public class PropertyExpression<E extends Exception> implements Expression<E>
{
    // Constants - defined the same as JMS
    private static final int NON_PERSISTENT = 1;
    private static final int PERSISTENT = 2;
    private static final int DEFAULT_PRIORITY = 4;

    private static final Logger _logger = org.apache.log4j.Logger.getLogger(PropertyExpression.class);

    private static final HashMap<String, Expression<? extends Exception>> JMS_PROPERTY_EXPRESSIONS = new HashMap<String, Expression<? extends Exception>>();

    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination", new Expression<E>()
                                     {
                                         public Object evaluate(Filterable<E> message)
                                         {
                                             //TODO
                                             return null;
                                         }
                                     });
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo", new ReplyToExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSType", new TypeExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSDeliveryMode", new DeliveryModeExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority", new PriorityExpression());

        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID", new MessageIDExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp", new TimestampExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID", new CorrelationIdExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration", new ExpirationExpression());

        JMS_PROPERTY_EXPRESSIONS.put("JMSRedelivered", new RedeliveredExpression());
    }

    private final String name;
    private final Expression<E> jmsPropertyExpression;

    public boolean outerTest()
    {
        return false;
    }

    public PropertyExpression(String name)
    {
        this.name = name;

        

        jmsPropertyExpression = (Expression<E>) JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(Filterable<E> message) throws E
    {

        if (jmsPropertyExpression != null)
        {
            return jmsPropertyExpression.evaluate(message);
        }
        else
        {

            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties) message.getContentHeaderBody().properties;

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Looking up property:" + name);
                _logger.debug("Properties are:" + _properties.getHeaders().keySet());
            }

            return _properties.getHeaders().getObject(name);
        }
    }

    public String getName()
    {
        return name;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return name;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode()
    {
        return name.hashCode();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object o)
    {

        if ((o == null) || !this.getClass().equals(o.getClass()))
        {
            return false;
        }

        return name.equals(((PropertyExpression) o).name);

    }

    private static class ReplyToExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {

            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;
            AMQShortString replyTo = _properties.getReplyTo();

            return (replyTo == null) ? null : replyTo.toString();

        }

    }

    private static class TypeExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
                CommonContentHeaderProperties _properties =
                    (CommonContentHeaderProperties)
                        message.getContentHeaderBody().properties;
                AMQShortString type = _properties.getType();

                return (type == null) ? null : type.toString();

        }
    }

    private static class DeliveryModeExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
                int mode = message.isPersistent() ? PERSISTENT : NON_PERSISTENT;
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("JMSDeliveryMode is :" + mode);
                }

                return mode;
        }
    }

    private static class PriorityExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;

            return (int) _properties.getPriority();
        }
    }

    private static class MessageIDExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {

            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;
            AMQShortString messageId = _properties.getMessageId();

            return (messageId == null) ? null : messageId;

        }
    }

    private static class TimestampExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;

            return _properties.getTimestamp();
        }
    }

    private static class CorrelationIdExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;
            AMQShortString correlationId = _properties.getCorrelationId();

            return (correlationId == null) ? null : correlationId.toString();
        }
    }

    private static class ExpirationExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {

            CommonContentHeaderProperties _properties =
                (CommonContentHeaderProperties)
                    message.getContentHeaderBody().properties;

            return _properties.getExpiration();

        }
    }
        
    private static class RedeliveredExpression<E extends Exception> implements Expression<E>
    {
        public Object evaluate(Filterable<E> message) throws E
        {
            return message.isRedelivered();
        }
    }

}
