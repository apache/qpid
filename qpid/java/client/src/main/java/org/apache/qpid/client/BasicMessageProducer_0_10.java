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

import static org.apache.qpid.transport.Option.NONE;
import static org.apache.qpid.transport.Option.SYNC;
import static org.apache.qpid.transport.Option.UNRELIABLE;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination.AddressOption;
import org.apache.qpid.client.AMQDestination.DestSyntax;
import org.apache.qpid.client.message.AMQMessageDelegate_0_10;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.messaging.address.Link.Reliability;
import org.apache.qpid.client.messaging.address.Node.QueueNode;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Option;
import org.apache.qpid.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a 0_10 message producer.
 */
public class BasicMessageProducer_0_10 extends BasicMessageProducer
{
    private static final Logger _logger = LoggerFactory.getLogger(BasicMessageProducer_0_10.class);
    private byte[] userIDBytes;

    BasicMessageProducer_0_10(AMQConnection connection, AMQDestination destination, boolean transacted, int channelId,
                              AMQSession session, AMQProtocolHandler protocolHandler, long producerId,
                              boolean immediate, boolean mandatory, boolean waitUntilSent) throws AMQException
    {
        super(connection, destination, transacted, channelId, session, protocolHandler, producerId, immediate,
              mandatory, waitUntilSent);
        
        userIDBytes = Strings.toUTF8(_userID);
    }

    void declareDestination(AMQDestination destination) throws AMQException
    {
        if (destination.getDestSyntax() == DestSyntax.BURL)
        {
        	if (getSession().isDeclareExchanges())
        	{
	            String name = destination.getExchangeName().toString();
	            ((AMQSession_0_10) getSession()).getQpidSession().exchangeDeclare
	                (name,
	                 destination.getExchangeClass().toString(),
	                 null, null,
	                 name.startsWith("amq.") ? Option.PASSIVE : Option.NONE);
        	}
        }
        else
        {       
            try
            {
                getSession().handleAddressBasedDestination(destination,false,false);
            }
            catch(Exception e)
            {
                AMQException ex = new AMQException("Exception occured while verifying destination",e);                
                throw ex;                
            }
        }
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

        DeliveryProperties deliveryProp = delegate.getDeliveryProperties();
        MessageProperties messageProps = delegate.getMessageProperties();

        // On the receiving side, this will be read in to the JMSXUserID as well.
        messageProps.setUserId(userIDBytes);
                
        if (messageId != null)
        {
            messageProps.setMessageId(messageId);
        }
        else if (messageProps.hasMessageId())
        {
            messageProps.clearMessageId();
        }

        long currentTime = 0;
        if (timeToLive > 0 || !_disableTimestamps)
        {
            currentTime = System.currentTimeMillis();
        }        
        
        if (timeToLive > 0)
        {
            deliveryProp.setTtl(timeToLive);
            message.setJMSExpiration(currentTime + timeToLive);
        }
        
        if (!_disableTimestamps)
        {
            
            deliveryProp.setTimestamp(currentTime);            
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
        String exchangeName = destination.getExchangeName() == null ? "" : destination.getExchangeName().toString();
        if ( deliveryProp.getExchange() == null || ! deliveryProp.getExchange().equals(exchangeName))
        {
            deliveryProp.setExchange(exchangeName);
        }
        String routingKey = destination.getRoutingKey().toString();
        if (deliveryProp.getRoutingKey() == null || ! deliveryProp.getRoutingKey().equals(routingKey))
        {
            deliveryProp.setRoutingKey(routingKey);
        }
        
        if (destination.getDestSyntax() == AMQDestination.DestSyntax.ADDR && 
           (destination.getSubject() != null || 
              (messageProps.getApplicationHeaders() != null && messageProps.getApplicationHeaders().get("qpid.subject") != null))
           )
        {
            Map<String,Object> appProps = messageProps.getApplicationHeaders();
            if (appProps == null)
            {
                appProps = new HashMap<String,Object>();
                messageProps.setApplicationHeaders(appProps);          
            }
            
            if (appProps.get("qpid.subject") == null)
            {
                // use default subject in address string
                appProps.put("qpid.subject",destination.getSubject());
            }
                    
            if (destination.getTargetNode().getType() == AMQDestination.TOPIC_TYPE)
            {
                deliveryProp.setRoutingKey((String)
                        messageProps.getApplicationHeaders().get("qpid.subject"));                
            }
        }
        
        messageProps.setContentLength(message.getContentLength());

        // send the message
        try
        {
            org.apache.qpid.transport.Session ssn = (org.apache.qpid.transport.Session)
                ((AMQSession_0_10) getSession()).getQpidSession();

            // if true, we need to sync the delivery of this message
            boolean sync = false;

            sync = ( (publishMode == PublishMode.SYNC_PUBLISH_ALL) ||
                     (publishMode == PublishMode.SYNC_PUBLISH_PERSISTENT && 
                         deliveryMode == DeliveryMode.PERSISTENT)
                   );  
            
            boolean unreliable = (destination.getDestSyntax() == DestSyntax.ADDR) &&
                                 (destination.getLink().getReliability() == Reliability.UNRELIABLE);
            
            org.apache.mina.common.ByteBuffer data = message.getData();
            ByteBuffer buffer = data == null ? ByteBuffer.allocate(0) : data.buf().slice();
            
            ssn.messageTransfer(destination.getExchangeName() == null ? "" : destination.getExchangeName().toString(), 
                                MessageAcceptMode.NONE,
                                MessageAcquireMode.PRE_ACQUIRED,
                                new Header(deliveryProp, messageProps),
                    buffer, sync ? SYNC : NONE, unreliable ? UNRELIABLE : NONE);
            if (sync)
            {
                ssn.sync();
                ((AMQSession_0_10) getSession()).getCurrentException();
            }
            
        }
        catch (Exception e)
        {
            JMSException jmse = new JMSException("Exception when sending message");
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }


    public boolean isBound(AMQDestination destination) throws JMSException
    {
        return _session.isQueueBound(destination);
    }
    
    @Override
    public void close()
    {
        super.close();
        AMQDestination dest = _destination;
        if (dest != null && dest.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
            if (dest.getDelete() == AddressOption.ALWAYS ||
                dest.getDelete() == AddressOption.SENDER )
            {
                ((AMQSession_0_10) getSession()).getQpidSession().queueDelete(
                        _destination.getQueueName());
            }
        }
    }
}

