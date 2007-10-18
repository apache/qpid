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

import javax.jms.Message;
import javax.jms.JMSException;
import java.io.IOException;

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
                                                                          null,
                                                                          null
                                                                          );
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
        org.apache.qpidity.api.Message qpidityMessage = new ByteBufferMessage();
        if(_logger.isDebugEnabled())
        {
            _logger.debug("Message Props: " + message.toString());
        }
        try
        {
            if (message.getData() != null)
            {
                qpidityMessage.appendData(message.getData().buf());
            }
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // set the delivery properties
        if (!_disableTimestamps)
        {
            final long currentTime = System.currentTimeMillis();
            qpidityMessage.getDeliveryProperties().setTimestamp(currentTime);
            if (timeToLive > 0)
            {
                qpidityMessage.getDeliveryProperties().setExpiration(currentTime + timeToLive);
            }
            else
            {
                qpidityMessage.getDeliveryProperties().setExpiration(0);
            }
        }
        qpidityMessage.getDeliveryProperties().setDeliveryMode((byte) deliveryMode);
        qpidityMessage.getDeliveryProperties().setPriority((byte) priority);
        qpidityMessage.getDeliveryProperties().setExchange(destination.getExchangeName().toString());
        qpidityMessage.getDeliveryProperties().setRoutingKey(destination.getRoutingKey().toString());
        BasicContentHeaderProperties contentHeaderProperties = message.getContentHeaderProperties();
        // set the application properties
        qpidityMessage.getMessageProperties().setContentType(contentHeaderProperties.getContentType().toString());
        AMQShortString type = contentHeaderProperties.getType();
        if( type != null )
        {
            qpidityMessage.getMessageProperties().setType( type.toString());
        }
        qpidityMessage.getMessageProperties().setMessageId(message.getJMSMessageID()) ;
        AMQShortString correlationID = contentHeaderProperties.getCorrelationId();
        if( correlationID != null )
        {
            qpidityMessage.getMessageProperties().setCorrelationId(correlationID.toString());
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
            qpidityMessage.getMessageProperties()
                    .setReplyTo(new ReplyTo(dest.getExchangeName().toString(), dest.getRoutingKey().toString()));
        }
         //JMS_QPID_DESTTYPE   is always set but useles so this is a temporary fix
        if (contentHeaderProperties.getHeaders() != null)
        {
            qpidityMessage.getMessageProperties().setApplicationHeaders(FiledTableSupport.convertToMap(contentHeaderProperties.getHeaders()));

            for(String key:qpidityMessage.getMessageProperties().getApplicationHeaders().keySet())
            {
                _logger.debug(key + "=" + qpidityMessage.getMessageProperties().getApplicationHeaders().get(key));
            }
        }
        if(_logger.isDebugEnabled() )
        {
            _logger.debug("Updating original message");
        }
        origMessage.setJMSPriority(qpidityMessage.getDeliveryProperties().getPriority());
        origMessage.setJMSTimestamp(qpidityMessage.getDeliveryProperties().getTimestamp());
        origMessage.setJMSExpiration(qpidityMessage.getDeliveryProperties().getExpiration());
        origMessage.setJMSMessageID(message.getJMSMessageID());
        // send the message
        try
        {
            ((AMQSession_0_10) getSession()).getQpidSession().messageTransfer(destination.getExchangeName().toString(),
                                                                              qpidityMessage,
                                                                              org.apache.qpidity.nclient.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                                                              org.apache.qpidity.nclient.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }

    }


    public boolean isBound(AMQDestination destination) throws JMSException
    {
        return _session.isQueueBound(destination.getExchangeName(), destination.getAMQQueueName(), destination.getRoutingKey());
    }
}

