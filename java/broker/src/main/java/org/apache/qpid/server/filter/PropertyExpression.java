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
         
//import java.io.IOException;
import java.util.HashMap;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.AMQException;
import org.apache.log4j.Logger;

/**
 * Represents a property  expression
 *
 * @version $Revision$
 */
public class PropertyExpression implements Expression
{
    // Constants - defined the same as JMS
    private static final int NON_PERSISTENT = 1;
    private static final int PERSISTENT = 2;
    private static final int DEFAULT_PRIORITY = 4;
    
    private final static Logger _logger = org.apache.log4j.Logger.getLogger(PropertyExpression.class);


    static final private HashMap JMS_PROPERTY_EXPRESSIONS = new HashMap();

    static
    {
        JMS_PROPERTY_EXPRESSIONS.put("JMSDestination",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getDestination();
                    }
                }
        );
        JMS_PROPERTY_EXPRESSIONS.put("JMSReplyTo",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getReplyTo();
                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSType",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getType();
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
                            int mode = message.isPersistent() ? PERSISTENT : NON_PERSISTENT;
                            _logger.info("JMSDeliveryMode is :" + mode);
                            return mode;
                        }
                        catch (AMQException e)
                        {
                            //shouldn't happen
                        }

                        return NON_PERSISTENT;
                    }
                });

        JMS_PROPERTY_EXPRESSIONS.put("JMSPriority",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getPriority();
                    }
                }
        );


        JMS_PROPERTY_EXPRESSIONS.put("AMQMessageID",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getMessageId();
                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSTimestamp",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getTimestamp();
                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSCorrelationID",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getCorrelationId();
                    }
                }
        );

        JMS_PROPERTY_EXPRESSIONS.put("JMSExpiration",
                new Expression()
                {
                    public Object evaluate(AMQMessage message)
                    {
                        return message.getExpiration();
                    }
                }
        );

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

    private final AMQShortString name;
    private final Expression jmsPropertyExpression;

    public PropertyExpression(AMQShortString name)
    {
        this.name = name;
        jmsPropertyExpression = (Expression) JMS_PROPERTY_EXPRESSIONS.get(name);
    }

    public Object evaluate(AMQMessage message) throws AMQException
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

            FieldTable headers = message.getApplicationHeaders();

            _logger.info("Looking up property:" + name);
            _logger.info("Properties are:" + headers.keySet());

            return headers.get(name);
        }
//            catch (IOException ioe)
//            {
//                AMQException exception = new AMQException("Could not get property: " + name + " reason: " + ioe.getMessage());
//                exception.initCause(ioe);
//                throw exception;
//            }
//        }
//        catch (IOException e)
//        {
//            AMQException exception = new AMQException(e.getMessage());
//            exception.initCause(e);
//            throw exception;
//        }

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
        return name.asString();
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
