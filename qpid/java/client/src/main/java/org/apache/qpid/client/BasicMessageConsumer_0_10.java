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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.UnprocessedMessage_0_10;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.AMQException;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.Struct;

import javax.jms.JMSException;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This is a 0.10 message consumer.
 */
public class BasicMessageConsumer_0_10 extends BasicMessageConsumer<Struct[], ByteBuffer>
        implements org.apache.qpidity.client.util.MessageListener
{
    /**
     * This class logger
     */
    protected final Logger _logger = LoggerFactory.getLogger(getClass());

    protected BasicMessageConsumer_0_10(int channelId, AMQConnection connection, AMQDestination destination,
                                        String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory,
                                        AMQSession session, AMQProtocolHandler protocolHandler,
                                        FieldTable rawSelectorFieldTable, int prefetchHigh, int prefetchLow,
                                        boolean exclusive, int acknowledgeMode, boolean noConsume, boolean autoClose)
    {
        super(channelId, connection, destination, messageSelector, noLocal, messageFactory, session, protocolHandler,
              rawSelectorFieldTable, prefetchHigh, prefetchLow, exclusive, acknowledgeMode, noConsume, autoClose);

    }

    // ----- Interface org.apache.qpidity.client.util.MessageListener
    public void onMessage(Message message)
    {
        int channelId = getSession().getChannelId();
        long deliveryId = message.getMessageTransferId();
        String consumerTag = getConsumerTag().toString();
        AMQShortString exchange = new AMQShortString(message.getDeliveryProperties().getExchange());
        AMQShortString routingKey = new AMQShortString(message.getDeliveryProperties().getRoutingKey());
        boolean redelivered = message.getDeliveryProperties().getRedelivered();
        UnprocessedMessage_0_10 newMessage =
                new UnprocessedMessage_0_10(channelId, deliveryId, consumerTag, exchange, routingKey, redelivered);
        try
        {
            newMessage.receiveBody(message.readData());
        }
        catch (IOException e)
        {
            getSession().getAMQConnection().exceptionReceived(e);
        }
        Struct[] headers = {message.getMessageProperties(), message.getDeliveryProperties()};
        newMessage.setContentHeader(headers);
        getSession().messageReceived(newMessage);
    }

    //----- overwritten methods

    /**
     * This method is invoked when this consumer is stopped.
     * It tells the broker to stop delivering messages to this consumer.
     */
    public void sendCancel() throws JMSAMQException
    {
        ((AMQSession_0_10) getSession()).getQpidSession().messageStop(getConsumerTag().toString());
        ((AMQSession_0_10) getSession()).getQpidSession().sync();
        try
        {
            ((AMQSession_0_10) getSession()).getCurrentException();
        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Problem when stopping consumer", e);
        }
    }

    /**
     * This is invoked just before a message is delivered to the jms consumer
     */
    void postDeliver(AbstractJMSMessage msg) throws JMSException
    {
        // notify the session
        ((AMQSession_0_10) getSession()).addMessageTag(msg.getDeliveryTag());
        super.postDeliver(msg);
    }


    public AbstractJMSMessage createJMSMessageFromUnprocessedMessage(UnprocessedMessage<Struct[], ByteBuffer>  messageFrame) throws Exception
    {
        return _messageFactory.createMessage(messageFrame.getDeliveryTag(),
            messageFrame.isRedelivered(), messageFrame.getExchange(),
            messageFrame.getRoutingKey(), messageFrame.getContentHeader(), messageFrame.getBodies());
    }
}