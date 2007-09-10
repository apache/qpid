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
import org.apache.qpidity.jms.message.MessageImpl;
import org.apache.qpidity.jms.message.MessageHelper;
import org.apache.qpidity.jms.ExceptionHelper;
import org.apache.qpidity.QpidException;

import javax.jms.Message;
import javax.jms.JMSException;
import java.util.UUID;
import java.io.IOException;

/**
 *
 *  This is a 0_10 message producer. 
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
     * We will always convert the received message
     */
    public void sendMessage(AMQDestination destination, Message origMessage, AbstractJMSMessage message,
                            int deliveryMode, int priority, long timeToLive, boolean mandatory, boolean immediate,
                            boolean wait) throws JMSException
    {
        // Only get current time if required
        long currentTime = Long.MIN_VALUE;
        if (!((timeToLive == 0) && _disableTimestamps))
        {
            currentTime = System.currentTimeMillis();
        }
        // the messae UID
        String uid = (getDisableMessageID()) ? "MSG_ID_DISABLED" : UUID.randomUUID().toString();
        MessageImpl qpidMessage;
        // check that the message is not a foreign one
        try
        {
            qpidMessage = (MessageImpl) origMessage;
        }
        catch (ClassCastException cce)
        {
            // this is a foreign message
            qpidMessage = MessageHelper.transformMessage(origMessage);
            // set message's properties in case they are queried after send.
            origMessage.setJMSDestination(destination);
            origMessage.setJMSDeliveryMode(deliveryMode);
            origMessage.setJMSPriority(priority);
            origMessage.setJMSMessageID(uid);
            if (timeToLive != 0)
            {
                origMessage.setJMSExpiration(timeToLive + currentTime);
                _logger.debug("Setting JMSExpiration:" + message.getJMSExpiration());
            }
            else
            {
                origMessage.setJMSExpiration(timeToLive);
            }
            origMessage.setJMSTimestamp(currentTime);
        }
        // set the message properties
        qpidMessage.setJMSDestination(destination);
        qpidMessage.setJMSMessageID(uid);
        qpidMessage.setJMSDeliveryMode(deliveryMode);
        qpidMessage.setJMSPriority(priority);
        if (timeToLive != 0)
        {
            qpidMessage.setJMSExpiration(timeToLive + currentTime);
        }
        else
        {
            qpidMessage.setJMSExpiration(timeToLive);
        }
        qpidMessage.setJMSTimestamp(currentTime);
        qpidMessage.setRoutingKey(destination.getDestinationName().toString());
        qpidMessage.setExchangeName(destination.getExchangeName().toString());
        // call beforeMessageDispatch
        try
        {
            qpidMessage.beforeMessageDispatch();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        try
        {
            ((AMQSession_0_10) getSession()).getQpidSession().messageTransfer(qpidMessage.getExchangeName(),
                                                                              qpidMessage.getQpidityMessage(),
                                                                              org.apache.qpidity.client.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                                                              org.apache.qpidity.client.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }       
    }
}

