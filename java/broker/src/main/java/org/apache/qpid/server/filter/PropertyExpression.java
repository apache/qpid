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

    interface SubExpression
    {
        public Object evaluate(AMQMessage message);
    }

    interface JMSExpression
    {
        public abstract Object evaluate(JMSMessage message);
    }

    static class SubJMSExpression implements SubExpression
    {
        JMSExpression _expression;

        SubJMSExpression(JMSExpression expression)
        {
            _expression = expression;
        }


        public Object evaluate(AMQMessage message)
        {
            JMSMessage msg = (JMSMessage) message.getDecodedMessage(AMQMessage.JMS_MESSAGE);
            if (msg != null)
            {
                return _expression.evaluate(msg);
            }
            else
            {
                return null;
            }
        }
    }

    private final static Logger _logger = org.apache.log4j.Logger.getLogger(PropertyExpression.class);


    static final private HashMap JMS_PROPERTY_EXPRESSIONS = new HashMap();

    static
    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSDestination();
                    }
                }
        ));
//
//            public Object evaluate(AMQMessage message)
//            {
//                //fixme
//
//
////                AMQDestination dest = message.getOriginalDestination();
////                if (dest == null)
////                {
////                    dest = message.getDestination();
////                }
////                if (dest == null)
////                {
////                    return null;
////                }
////                return dest.toString();
//                return "";
//            }
//        });
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSReplyTo();
                    }
                })
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSType", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSType();
                    }
                }
        ));

        JMS_PROPERTY_EXPRESSIONS.put("JMSDeliveryMode", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        try
                        {
                            Integer mode = new Integer(message.getAMQMessage().isPersistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                            _logger.info("JMSDeliveryMode is :" + mode);
                            return mode;
                        }
                        catch (AMQException e)
                        {
                            //shouldn't happen
                        }

                        return DeliveryMode.NON_PERSISTENT;
                    }
                }));

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSPriority();
                    }
                }
        ));


        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getAMQMessage().getMessageId();
                    }
                }
        ));

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSTimestamp();
                    }
                }
        ));

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSCorrelationID();
                    }
                }
        ));

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getJMSExpiration();
                    }
                }
        ));

        JMS_PROPERTY_EXPRESSIONS.put("JMSRedelivered", new SubJMSExpression(
                new JMSExpression()
                {
                    public Object evaluate(JMSMessage message)
                    {
                        return message.getAMQMessage().isRedelivered();
                    }
                }
        ));

    }

    private final String name;
    private final SubExpression jmsPropertyExpression;

    public PropertyExpression(String name)
    {
        this.name = name;
        jmsPropertyExpression = (SubExpression) JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(AMQMessage message) throws JMSException
    {
//        try
//        {
//            if (message.isDropped())
//            {
//                return null;
//            }

        if (jmsPropertyExpression != null)
        {
            return jmsPropertyExpression.evaluate(message);
        }
//            try
        else
        {

            BasicContentHeaderProperties _properties = (BasicContentHeaderProperties) message.getContentHeaderBody().properties;

            _logger.info("Looking up property:" + name);
            _logger.info("Properties are:" + _properties.getHeaders().keySet());

            return _properties.getHeaders().get(name);
        }
//            catch (IOException ioe)
//            {
//                JMSException exception = new JMSException("Could not get property: " + name + " reason: " + ioe.getMessage());
//                exception.initCause(ioe);
//                throw exception;
//            }
//        }
//        catch (IOException e)
//        {
//            JMSException exception = new JMSException(e.getMessage());
//            exception.initCause(e);
//            throw exception;
//        }

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
