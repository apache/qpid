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
import org.apache.qpid.server.queue.AMQMessage;

/**
 * Represents a property  expression
 */
public class PropertyExpression implements Expression
{
    // Constants - defined the same as JMS
    private static final int NON_PERSISTENT = 1;
    private static final int PERSISTENT = 2;
    private static final int DEFAULT_PRIORITY = 4;

    private static final Logger _logger = org.apache.log4j.Logger.getLogger(PropertyExpression.class);

    private static final HashMap<String, Expression> JMS_PROPERTY_EXPRESSIONS = new HashMap<String, Expression>();

    static
    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             //TODO
                                             return null;
                                         }
                                     });
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;
                                                 AMQShortString replyTo = _properties.getReplyTo();

                                                 return (replyTo == null) ? null : replyTo;
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }

                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSType", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;
                                                 AMQShortString type = _properties.getType();

                                                 return (type == null) ? null : type;
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSDeliveryMode", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             try
                                             {
                                                 int mode = message.isPersistent() ? PERSISTENT : NON_PERSISTENT;
                                                 if (_logger.isDebugEnabled())
                                                 {
                                                     _logger.debug("JMSDeliveryMode is :" + mode);
                                                 }

                                                 return mode;
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);
                                             }

                                             return NON_PERSISTENT;
                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;

                                                 return (int) _properties.getPriority();
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);
                                             }

                                             return DEFAULT_PRIORITY;
                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {

                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;
                                                 AMQShortString messageId = _properties.getMessageId();

                                                 return (messageId == null) ? null : messageId;
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {

                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;

                                                 return _properties.getTimestamp();
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {

                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;
                                                 AMQShortString correlationId = _properties.getCorrelationId();

                                                 return (correlationId == null) ? null : correlationId;
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {

                                             try
                                             {
                                                 CommonContentHeaderProperties _properties =
                                                     (CommonContentHeaderProperties)
                                                         message.getContentHeaderBody().properties;

                                                 return _properties.getExpiration();
                                             }
                                             catch (AMQException e)
                                             {
                                                 _logger.warn(e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSRedelivered", new Expression()
                                     {
                                         public Object evaluate(AMQMessage message)
                                         {
                                             return message.isRedelivered();
                                         }
                                     });

    }

    private final AMQShortString name;
    private final Expression jmsPropertyExpression;

    public PropertyExpression(String name)
    {
        this.name = new AMQShortString(name);
        jmsPropertyExpression = JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(AMQMessage message) throws AMQException
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

    public AMQShortString getName()
    {
        return name;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return name.toString();
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

}
