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
package org.apache.qpid.client;

import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.FiledTableSupport;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpidity.njms.ExceptionHelper;
import org.apache.qpidity.nclient.util.ByteBufferMessage;
import org.apache.qpidity.transport.ReplyTo;
import org.apache.qpidity.transport.DeliveryProperties;

import javax.jms.Message;
import javax.jms.JMSException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is a 0_10 message producer.
 */
public class BasicMessageProducer_0_10 extends BasicMessageProducer
{

    BasicMessageProducer_0_10(AMQConnection connection, AMQDestination destination, boolean transacted, int channelId,
                              AMQSession session, AMQProtocolHandler protocolHandler, long producerId,
                              boolean immediate, boolean mandatory, boolean waitUntilSent)
    {
        super(connection, destination, transacted, channelId, session, protocolHandler, producerId, immediate,
              mandatory, waitUntilSent);
    }

    public void declareDestination(AMQDestination destination)
    {
        ((AMQSession_0_10) getSession()).getQpidSession().exchangeDeclare(destination.getExchangeName().toString(),
                                                                          destination.getExchangeClass().toString(),
                                                                          null, null);
    }

    //--- Overwritten methods

    /**
     * Sends a message to a given destination
     */
    public void sendMessage(AMQDestination destination, Message origMessage, AbstractJMSMessage message,
                            int deliveryMode, int priority, long timeToLive, boolean mandatory, boolean immediate,
                            boolean wait) throws JMSException
    {
        message.prepareForSending();
        if (message.get010Message() == null)
        {
            message.set010Message(new ByteBufferMessage());
            if (message.getData() == null)
            {
                try
                {
                    message.get010Message().appendData(ByteBuffer.allocate(0));
                }
                catch (IOException e)
                {
                    throw new JMSException(e.getMessage());
                }
            }
        }

        DeliveryProperties deliveryProp = message.get010Message().getDeliveryProperties();
        // set the delivery properties
        if (!_disableTimestamps)
        {
            final long currentTime = System.currentTimeMillis();
            deliveryProp.setTimestamp(currentTime);
            if (timeToLive > 0)
            {
                deliveryProp.setExpiration(currentTime + timeToLive);
                message.setJMSExpiration(currentTime + timeToLive);
            }
            else
            {
               deliveryProp.setExpiration(0);
               message.setJMSExpiration(0);
            }
            message.setJMSTimestamp(currentTime);
        }

        if (deliveryProp.getDeliveryMode() != deliveryMode)
        {
            deliveryProp.setDeliveryMode((byte) deliveryMode);
            message.setJMSDeliveryMode(deliveryMode);
        }
        if (deliveryProp.getPriority() != priority)
        {
            deliveryProp.setPriority((byte) priority);
            message.setJMSPriority(priority);
        }
        String excahngeName = destination.getExchangeName().toString();
        if ( deliveryProp.getExchange() == null || ! deliveryProp.getExchange().equals(excahngeName))
        {
            deliveryProp.setExchange(excahngeName);
        }
        String routingKey = destination.getRoutingKey().toString();
        if (deliveryProp.getRoutingKey() == null || ! deliveryProp.getRoutingKey().equals(routingKey))
        {
            deliveryProp.setRoutingKey(routingKey);
        }

        if (message != origMessage)
        {
             _logger.debug("Updating original message");
            origMessage.setJMSPriority(message.getJMSPriority());
            origMessage.setJMSTimestamp(message.getJMSTimestamp());
            _logger.debug("Setting JMSExpiration:" + message.getJMSExpiration());
            origMessage.setJMSExpiration(message.getJMSExpiration());
            origMessage.setJMSMessageID(message.getJMSMessageID());
            origMessage.setJMSDeliveryMode(deliveryMode);
        }

        BasicContentHeaderProperties contentHeaderProperties = message.getContentHeaderProperties();
        if (contentHeaderProperties.reset())
        {
            // set the application properties
            message.get010Message().getMessageProperties()
                    .setContentType(contentHeaderProperties.getContentType().toString());

            /* Temp hack to get the JMS client to interoperate with the python client
               as it relies on content length to determine the boundaries for
               message framesets. The python client should be fixed.
            */
            message.get010Message().getMessageProperties().setContentLength(message.getContentLength());


            AMQShortString type = contentHeaderProperties.getType();
            if (type != null)
            {
                message.get010Message().getMessageProperties().setType(type.toString());
            }
            message.get010Message().getMessageProperties().setMessageId(message.getJMSMessageID());
            AMQShortString correlationID = contentHeaderProperties.getCorrelationId();
            if (correlationID != null)
            {
                message.get010Message().getMessageProperties().setCorrelationId(correlationID.toString());
            }
            String replyToURL = contentHeaderProperties.getReplyToAsString();
            if (replyToURL != null)
            {
                AMQBindingURL dest;
                try
                {
                    dest = new AMQBindingURL(replyToURL);
                }
                catch (URLSyntaxException e)
                {
                    throw ExceptionHelper.convertQpidExceptionToJMSException(e);
                }
                message.get010Message().getMessageProperties()
                        .setReplyTo(new ReplyTo(dest.getExchangeName().toString(), dest.getRoutingKey().toString()));
            }
            if (contentHeaderProperties.getHeaders() != null)
            {
                //JMS_QPID_DESTTYPE   is always set but useles so this is a temporary fix
                contentHeaderProperties.getHeaders().remove(CustomJMSXProperty.JMS_QPID_DESTTYPE.getShortStringName());
                message.get010Message().getMessageProperties()
                        .setApplicationHeaders(FiledTableSupport.convertToMap(contentHeaderProperties.getHeaders()));
            }
        }
        // send the message
        try
        {
            ((AMQSession_0_10) getSession()).getQpidSession().messageTransfer(destination.getExchangeName().toString(),
                                                                              message.get010Message(),
                                                                              org.apache.qpidity.nclient.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                                                              org.apache.qpidity.nclient.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        catch (RuntimeException rte)
        {
            JMSException ex = new JMSException("Exception when sending message");
            rte.printStackTrace();
            ex.setLinkedException(rte);
            throw ex;
        }
    }


    public boolean isBound(AMQDestination destination) throws JMSException
    {
        return _session.isQueueBound(destination.getExchangeName(), destination.getAMQQueueName(),
                                     destination.getRoutingKey());
    }
}

