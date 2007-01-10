/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.qpid.server.filter;
//
// Based on like named file from r450141 of the Apache ActiveMQ project <http://www.activemq.org/site/home.html>
//
         
import java.io.IOException;
import java.util.HashMap;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;

//import org.apache.activemq.command.ActiveMQDestination;
//import org.apache.activemq.command.Message;
//import org.apache.activemq.command.TransactionId;
//import org.apache.activemq.util.JMSExceptionSupport;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.message.jms.JMSMessage;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.log4j.Logger;

/**
 * Represents a property  expression
 *
 * @version $Revision$
 */
public class PropertyExpression implements Expression
{



    private final static Logger _logger = org.apache.log4j.Logger.getLogger(PropertyExpression.class);


    static final private HashMap<String, Expression> JMS_PROPERTY_EXPRESSIONS = new HashMap<String, Expression>();

    static
    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        //TODO
                        return null;
                    }
                }
        );
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo", new Expression()
        {
                    public Object evaluate(AMQMessage message)
                    {
                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getReplyTo();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }

        });

        JMS_PROPERTY_EXPRESSIONS.put("JMSType",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getType();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSDeliveryMode",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        try
                        {
                            int mode = message.isPersistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
                            if(_logger.isDebugEnabled())
                            {
                                _logger.debug("JMSDeliveryMode is :" + mode);
                            }
                            return mode;
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                        }

                        return DeliveryMode.NON_PERSISTENT;
                    }
                });

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return (int) _properties.getPriority();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                        }
                        return Message.DEFAULT_PRIORITY;
                    }
                }
        );


        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID", 
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {

                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getMessageId();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {

                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getTimestamp();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {

                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getCorrelationId();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {

                        try
                        {
                            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;
                            return _properties.getExpiration();
                        }
                        catch (AMQException e)
                        {
                            _logger.warn(e);
                            return null;
                        }

                    }
                });

        JMS_PROPERTY_EXPRESSIONS.put("JMSRedelivered",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.isRedelivered();
                    }
                }
        );

    }

    private final String name;
    private final Expression jmsPropertyExpression;

    public PropertyExpression(String name)
    {
        this.name = name;
        jmsPropertyExpression =  JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(AMQMessage message) throws AMQException
    {

        if (jmsPropertyExpression != null)
        {
            return jmsPropertyExpression.evaluate(message);
        }

        else
        {

            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;

            if(_logger.isDebugEnabled())
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

        if (o == null || !this.getClass().equals(o.getClass()))
        {
            return false;
        }
        return name.equals(((PropertyExpression) o).name);

    }

}
