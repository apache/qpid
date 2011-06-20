/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpid.filter;

import java.util.HashMap;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;

import org.apache.qpid.AMQInternalException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a property  expression
 */
public class PropertyExpression implements Expression
{
    // Constants - defined the same as JMS
    private static enum JMSDeliveryMode { NON_PERSISTENT, PERSISTENT }
    private static final int DEFAULT_PRIORITY = 4;

    private static final Logger _logger = LoggerFactory.getLogger(PropertyExpression.class);

    private static final HashMap<String, Expression> JMS_PROPERTY_EXPRESSIONS = new HashMap<String, Expression>();

    static
    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             //TODO
                                             return null;
                                         }
                                     });
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             return message.getReplyToString();
                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSType", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             try
                                             {
                                                 return message.getJMSType();
                                             }
                                             catch (JMSException e)
                                             {
                                                 _logger.warn("Error evaluating property", e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSDeliveryMode", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {

                                             JMSDeliveryMode mode = JMSDeliveryMode.NON_PERSISTENT;
                                             try
                                             {
                                                 mode = message.getJMSDeliveryMode() == DeliveryMode.PERSISTENT ?
                                                         JMSDeliveryMode.PERSISTENT : JMSDeliveryMode.NON_PERSISTENT;

                                                 if (_logger.isDebugEnabled())
                                                 {
                                                     _logger.debug("JMSDeliveryMode is :" + mode);
                                                 }
                                             }
                                             catch (JMSException e)
                                             {
                                                 _logger.warn("Error evaluating property",e);
                                             }

                                             return mode.toString();
                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             try
                                             {
                                                 return message.getJMSPriority();
                                             }
                                             catch (Exception e)
                                             {
                                                 _logger.warn("Error evaluating property",e);
                                             }

                                             return DEFAULT_PRIORITY;
                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {

                                             try
                                             {
                                                 return message.getJMSMessageID();
                                             }
                                             catch (JMSException e)
                                             {
                                                 _logger.warn("Error evaluating property",e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             try
                                             {
                                                 return message.getJMSTimestamp();
                                             }
                                             catch (Exception e)
                                             {
                                                 _logger.warn("Error evaluating property",e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {

                                             try
                                             {
                                                 return message.getJMSCorrelationID();
                                             }
                                             catch (JMSException e)
                                             {
                                                 _logger.warn("Error evaluating property",e);

                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {

                                             try
                                             {
                                                 return message.getJMSExpiration();
                                             }
                                             catch (JMSException e)
                                             {
                                                 _logger.warn("Error evaluating property",e);
                                                 return null;
                                             }

                                         }
                                     });

        JMS_PROPERTY_EXPRESSIONS.put("JMSRedelivered", new Expression()
                                     {
                                         public Object evaluate(AbstractJMSMessage message)
                                         {
                                             try
                                             {
                                                 return message.getJMSRedelivered();
                                             }
                                             catch (JMSException e)
                                             {
                                                _logger.warn("Error evaluating property",e);
                                                 return null;
                                             }
                                         }
                                     });
        
        JMS_PROPERTY_EXPRESSIONS.put("JMSMessageID", new Expression()
                                    {
                                        public Object evaluate(AbstractJMSMessage message)
                                        {
                                            try
                                            {
                                                return message.getJMSMessageID();
                                            }
                                            catch (Exception e)
                                            {
                                                _logger.warn("Error evaluating property",e);
                            
                                                return null;
                                            }
                            
                                        }
                                    });

    }

    private final String name;
    private final Expression jmsPropertyExpression;

    public PropertyExpression(String name)
    {
        this.name = name;
        jmsPropertyExpression = JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(AbstractJMSMessage message) throws AMQInternalException
    {

        if (jmsPropertyExpression != null)
        {
            return jmsPropertyExpression.evaluate(message);
        }
        else
        {

            try
            {

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Looking up property:" + name);
                    _logger.debug("Properties are:" + message.getPropertyNames());
                }
                return message.getObjectProperty(name);
            }
            catch(JMSException e)
            {
                throw new AMQInternalException("Exception evaluating properties for filter", e);
            }
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

}
