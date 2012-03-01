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
package org.apache.qpid.client.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQInternalException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.filter.FilterableMessage;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.filter.selector.SelectorParser;
import org.apache.qpid.filter.BooleanExpression;
import org.apache.qpid.filter.selector.TokenMgrError;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;


public class JMSSelectorFilter implements MessageFilter
{
    private static final Logger _logger = LoggerFactory.getLogger(JMSSelectorFilter.class);

    private final String _selector;
    private final BooleanExpression _matcher;

    public JMSSelectorFilter(String selector) throws AMQInternalException
    {
        if (selector == null || "".equals(selector))
        {
            throw new IllegalArgumentException("Cannot create a JMSSelectorFilter with a null or empty selector string");
        }
        _selector = selector;
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Created JMSSelectorFilter with selector:" + _selector);
        }
        try
        {
            _matcher = new SelectorParser().parse(selector);
        }
        catch (SelectorParsingException e)
        {
            throw new AMQInternalException("Unable to parse selector \""+selector+"\"", e);
        }
        catch (TokenMgrError e)
        {
            throw new AMQInternalException("Unable to parse selector \""+selector+"\"", e);
        }
    }

    public boolean matches(AbstractJMSMessage message)
    {
        try
        {
            boolean match = _matcher.matches(wrap(message));
            if (_logger.isDebugEnabled())
            {
                _logger.debug(message + " match(" + match + ") selector(" + _selector + "): " + _selector);
            }
            return match;
        }
        catch (SelectorParsingException e)
        {
            _logger.warn("Caught exception when evaluating message selector for message  " + message, e);
        }
        return false;
    }

    private FilterableMessage wrap(final AbstractJMSMessage message)
    {
        return new FilterableMessage()
        {
            public boolean isPersistent()
            {
                try
                {
                    return message.getJMSDeliveryMode() == DeliveryMode.PERSISTENT;
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public boolean isRedelivered()
            {
                try
                {
                    return message.getJMSRedelivered();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public Object getHeader(String name)
            {
                try
                {
                    return message.getObjectProperty(name);
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public String getReplyTo()
            {
                return message.getReplyToString();
            }

            public String getType()
            {
                try
                {
                    return message.getJMSType();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public byte getPriority()
            {
                try
                {
                    return (byte) message.getJMSPriority();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public String getMessageId()
            {
                try
                {
                    return message.getJMSMessageID();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public long getTimestamp()
            {
                try
                {
                    return message.getJMSTimestamp();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public String getCorrelationId()
            {
                try
                {
                    return message.getJMSCorrelationID();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }

            public long getExpiration()
            {
                try
                {
                    return message.getJMSExpiration();
                }
                catch (JMSException e)
                {
                    throw new SelectorParsingException(e);
                }
            }
        };
    }

    public String getSelector()
    {
        return _selector;
    }
}
