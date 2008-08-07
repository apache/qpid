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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.DeliveryMode;

import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.FiledTableSupport;
import org.apache.qpid.client.message.AMQMessageDelegate_0_10;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.nclient.util.ByteBufferMessage;
import org.apache.qpid.njms.ExceptionHelper;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.ReplyTo;

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

    void declareDestination(AMQDestination destination)
    {
        ((AMQSession_0_10) getSession()).getQpidSession().exchangeDeclare(destination.getExchangeName().toString(),
                                                                          destination.getExchangeClass().toString(),
                                                                          null, null);
    }

    //--- Overwritten methods

    /**
     * Sends a message to a given destination
     */
    void sendMessage(AMQDestination destination, Message origMessage, AbstractJMSMessage message,
                     UUID messageId, int deliveryMode, int priority, long timeToLive, boolean mandatory,
                     boolean immediate, boolean wait) throws JMSException
    {
        message.prepareForSending();

        AMQMessageDelegate_0_10 delegate = (AMQMessageDelegate_0_10) message.getDelegate();

        org.apache.qpid.api.Message underlyingMessage = message.get010Message();
        if (underlyingMessage == null)
        {
            underlyingMessage = new ByteBufferMessage(delegate.getMessageProperties(), delegate.getDeliveryProperties()); 
            message.set010Message(underlyingMessage);

        }
        // force a rebuild of the 0-10 message if data has changed
        if (message.getData() == null)
        {
            message.dataChanged();
        }

        DeliveryProperties deliveryProp = underlyingMessage.getDeliveryProperties();
        MessageProperties messageProps = underlyingMessage.getMessageProperties();

        if (messageId != null)
        {
            messageProps.setMessageId(messageId);
        }
        else if (messageProps.hasMessageId())
        {
            messageProps.clearMessageId();
        }

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

        if (!deliveryProp.hasDeliveryMode() || deliveryProp.getDeliveryMode().getValue() != deliveryMode)
        {
            MessageDeliveryMode mode;
            switch (deliveryMode)
            {
            case DeliveryMode.PERSISTENT:
                mode = MessageDeliveryMode.PERSISTENT;
                break;
            case DeliveryMode.NON_PERSISTENT:
                mode = MessageDeliveryMode.NON_PERSISTENT;
                break;
            default:
                throw new IllegalArgumentException("illegal delivery mode: " + deliveryMode);
            }
            deliveryProp.setDeliveryMode(mode);
            message.setJMSDeliveryMode(deliveryMode);
        }
        if (!deliveryProp.hasPriority() || deliveryProp.getPriority().getValue() != priority)
        {
            deliveryProp.setPriority(MessageDeliveryPriority.get((short) priority));
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

        messageProps.setContentLength(message.getContentLength());


        /*    String replyToURL = contentHeaderProperties.getReplyToAsString();
            if (replyToURL != null)
            {
                if(_logger.isDebugEnabled())
                {
                    StringBuffer b = new StringBuffer();
                    b.append("\n==========================");
                    b.append("\nReplyTo : " + replyToURL);
                    b.append("\n==========================");
                    _logger.debug(b.toString());
                }
                AMQBindingURL dest;
                try
                {
                    dest = new AMQBindingURL(replyToURL);
                }
                catch (URISyntaxException e)
                {
                    throw ExceptionHelper.convertQpidExceptionToJMSException(e);
                }
                messageProps.setReplyTo(new ReplyTo(dest.getExchangeName().toString(), dest.getRoutingKey().toString()));
            }
*/


        // send the message
        try
        {
            org.apache.qpid.nclient.Session ssn = ((AMQSession_0_10) getSession()).getQpidSession();

            // if true, we need to sync the delivery of this message
            boolean sync = (deliveryMode == DeliveryMode.PERSISTENT &&
                            getSession().getAMQConnection().getSyncPersistence());

            if (sync)
            {
                ssn.setAutoSync(true);
            }
            try
            {
                ssn.messageTransfer(destination.getExchangeName().toString(),
                                    underlyingMessage,
                                    ssn.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                    ssn.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
            }
            finally
            {
                if (sync)
                {
                    ssn.setAutoSync(false);
                }
            }
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
        return _session.isQueueBound(destination);
    }
}

