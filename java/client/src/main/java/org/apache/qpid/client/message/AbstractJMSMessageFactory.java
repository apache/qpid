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
package org.apache.qpid.client.message;

import org.apache.mina.common.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpidity.transport.Struct;
import org.apache.qpidity.transport.MessageProperties;
import org.apache.qpidity.transport.DeliveryProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractJMSMessageFactory implements MessageFactory
{
    private static final Logger _logger = LoggerFactory.getLogger(AbstractJMSMessageFactory.class);

    protected abstract AbstractJMSMessage createMessage(long messageNbr, ByteBuffer data, AMQShortString exchange,
                                                        AMQShortString routingKey,
                                                        BasicContentHeaderProperties contentHeader) throws AMQException;

    protected AbstractJMSMessage create08MessageWithBody(long messageNbr, ContentHeaderBody contentHeader,
                                                         AMQShortString exchange, AMQShortString routingKey,
                                                         List bodies) throws AMQException
    {
        ByteBuffer data;
        final boolean debug = _logger.isDebugEnabled();

        // we optimise the non-fragmented case to avoid copying
        if ((bodies != null) && (bodies.size() == 1))
        {
            if (debug)
            {
                _logger.debug("Non-fragmented message body (bodySize=" + contentHeader.bodySize + ")");
            }

            data = ((ContentBody) bodies.get(0)).payload;
        }
        else if (bodies != null)
        {
            if (debug)
            {
                _logger.debug("Fragmented message body (" + bodies
                        .size() + " frames, bodySize=" + contentHeader.bodySize + ")");
            }

            data = ByteBuffer.allocate((int) contentHeader.bodySize); // XXX: Is cast a problem?
            final Iterator it = bodies.iterator();
            while (it.hasNext())
            {
                ContentBody cb = (ContentBody) it.next();
                data.put(cb.payload);
                cb.payload.release();
            }

            data.flip();
        }
        else // bodies == null
        {
            data = ByteBuffer.allocate(0);
        }

        if (debug)
        {
            _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data
                    .remaining());
        }

        return createMessage(messageNbr, data, exchange, routingKey,
                             (BasicContentHeaderProperties) contentHeader.properties);
    }

    protected AbstractJMSMessage create010MessageWithBody(long messageNbr, Struct[] contentHeader,
                                                          AMQShortString exchange, AMQShortString routingKey,
                                                          List bodies, String replyToURL) throws AMQException
    {
        ByteBuffer data;
        final boolean debug = _logger.isDebugEnabled();

        // we optimise the non-fragmented case to avoid copying
        if ((bodies != null))
        {
            data = ByteBuffer.wrap((java.nio.ByteBuffer) bodies.get(0));
        }
        else // bodies == null
        {
            data = ByteBuffer.allocate(0);
        }

        if (debug)
        {
            _logger.debug("Creating message from buffer with position=" + data.position() + " and remaining=" + data
                    .remaining());
        }
        BasicContentHeaderProperties props = new BasicContentHeaderProperties();
        // set the properties of this message
        MessageProperties mprop = (MessageProperties) contentHeader[0];
        DeliveryProperties devprop = (DeliveryProperties) contentHeader[1];
        props.setContentType(mprop.getContentType());
        props.setCorrelationId(asString(mprop.getCorrelationId()));
        String encoding = mprop.getContentEncoding();
        if (encoding != null && !encoding.equals(""))
        {
            props.setEncoding(encoding);
        }
        if (devprop.hasDeliveryMode())
        {
            props.setDeliveryMode((byte) devprop.getDeliveryMode().getValue());
        }
        props.setExpiration(devprop.getExpiration());
        UUID mid = mprop.getMessageId();
        props.setMessageId(mid == null ? null : "ID:" + mid.toString());
        if (devprop.hasPriority())
        {
            props.setPriority((byte) devprop.getPriority().getValue());
        }
        props.setReplyTo(replyToURL);
        props.setTimestamp(devprop.getTimestamp());
        String type = null;
        Map<String,Object> map = mprop.getApplicationHeaders();
        if (map != null)
        {
            type = (String) map.get(AbstractJMSMessage.JMS_TYPE);
        }
        props.setType(type);
        props.setUserId(asString(mprop.getUserId()));
        props.setHeaders(FiledTableSupport.convertToFieldTable(mprop.getApplicationHeaders()));        
        AbstractJMSMessage message = createMessage(messageNbr, data, exchange, routingKey, props);        
        return message;
    }

    private static final String asString(byte[] bytes)
    {
        if (bytes == null)
        {
            return null;
        }
        else
        {
            return new String(bytes);
        }
    }


    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered, ContentHeaderBody contentHeader,
                                            AMQShortString exchange, AMQShortString routingKey, List bodies)
            throws JMSException, AMQException
    {
        final AbstractJMSMessage msg = create08MessageWithBody(messageNbr, contentHeader, exchange, routingKey, bodies);
        msg.setJMSRedelivered(redelivered);
        msg.receivedFromServer();
        return msg;
    }

    public AbstractJMSMessage createMessage(long messageNbr, boolean redelivered, Struct[] contentHeader,
                                            AMQShortString exchange, AMQShortString routingKey, List bodies,
                                            String replyToURL)
            throws JMSException, AMQException
    {
        final AbstractJMSMessage msg =
                create010MessageWithBody(messageNbr, contentHeader, exchange, routingKey, bodies, replyToURL);
        msg.setJMSRedelivered(redelivered);
        msg.receivedFromServer();
        return msg;
    }

}
