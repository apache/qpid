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
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;
import org.apache.qpidity.jms.ExceptionHelper;
import org.apache.qpidity.client.util.ByteBufferMessage;
import org.apache.qpidity.ReplyTo;

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
        // Declare the exchange
        // Note that the durable and internal arguments are ignored since passive is set to false
        AMQFrame declare = ExchangeDeclareBody.createAMQFrame(_channelId, _protocolHandler.getProtocolMajorVersion(),
                                                              _protocolHandler.getProtocolMinorVersion(), null,
                                                              // arguments
                                                              false, // autoDelete
                                                              false, // durable
                                                              destination.getExchangeName(), // exchange
                                                              false, // internal
                                                              true, // nowait
                                                              false, // passive
                                                              _session.getTicket(), // ticket
                                                              destination.getExchangeClass()); // type
        _protocolHandler.writeFrame(declare);
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
        // set the payload
        try
        {
            qpidityMessage.appendData(message.getData().buf());
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
        qpidityMessage.getMessageProperties().setCorrelationId(contentHeaderProperties.getCorrelationId().toString());
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
        if (contentHeaderProperties.getHeaders() != null)
        {
            // todo use the new fieldTable
            qpidityMessage.getMessageProperties().setApplicationHeaders(null);
        }
        // send the message 
        try
        {
            ((AMQSession_0_10) getSession()).getQpidSession().messageTransfer(destination.getExchangeName().toString(),
                                                                              qpidityMessage,
                                                                              org.apache.qpidity.client.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                                                              org.apache.qpidity.client.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }

    }
}

