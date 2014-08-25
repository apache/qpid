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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination.AddressOption;
import org.apache.qpid.client.AMQDestination.DestSyntax;
import org.apache.qpid.client.message.AMQMessageDelegate_0_10;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.QpidMessageProperties;
import org.apache.qpid.client.messaging.address.Link.Reliability;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageDeliveryMode;
import org.apache.qpid.transport.MessageDeliveryPriority;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.util.GZIPUtils;
import org.apache.qpid.util.Strings;

/**
 * This is a 0_10 message producer.
 */
public class BasicMessageProducer_0_10 extends BasicMessageProducer
{

    private static final Logger _logger = LoggerFactory.getLogger(BasicMessageProducer_0_10.class);
    private byte[] userIDBytes;

    BasicMessageProducer_0_10(AMQConnection connection, AMQDestination destination, boolean transacted, int channelId,
                              AMQSession session, long producerId, Boolean immediate, Boolean mandatory) throws AMQException
    {
        super(_logger, connection, destination, transacted, channelId, session, producerId, immediate, mandatory);
        
        userIDBytes = Strings.toUTF8(getUserID());
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
	                 name.startsWith("amq.") ? Option.PASSIVE : Option.NONE,
	                 destination.isExchangeDurable() ? Option.DURABLE : Option.NONE,
	                 destination.isExchangeAutoDelete() ? Option.AUTO_DELETE : Option.NONE);
        	}
        }
        else
        {       
            try
            {
                getSession().resolveAddress(destination,false,false);
                ((AMQSession_0_10)getSession()).handleLinkCreation(destination);
                ((AMQSession_0_10)getSession()).sync();
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
                     boolean immediate) throws JMSException
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
        if (timeToLive > 0 || !isDisableTimestamps())
        {
            currentTime = System.currentTimeMillis();
        }        
        
        if (timeToLive > 0)
        {
            deliveryProp.setTtl(timeToLive);
            message.setJMSExpiration(currentTime + timeToLive);
        }
        
        if (!isDisableTimestamps())
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
              (messageProps.getApplicationHeaders() != null && messageProps.getApplicationHeaders().get(QpidMessageProperties.QPID_SUBJECT) != null))
           )
        {
            Map<String,Object> appProps = messageProps.getApplicationHeaders();
            if (appProps == null)
            {
                appProps = new HashMap<String,Object>();
                messageProps.setApplicationHeaders(appProps);          
            }
            
            if (appProps.get(QpidMessageProperties.QPID_SUBJECT) == null)
            {
                // use default subject in address string
                appProps.put(QpidMessageProperties.QPID_SUBJECT,destination.getSubject());
            }
                    
            if (destination.getAddressType() == AMQDestination.TOPIC_TYPE)
            {
                deliveryProp.setRoutingKey((String)
                        messageProps.getApplicationHeaders().get(QpidMessageProperties.QPID_SUBJECT));                
            }
        }

        ByteBuffer data = message.getData();

        if(data != null
           && data.remaining() > getConnection().getMessageCompressionThresholdSize()
           && getConnection().getDelegate().isMessageCompressionSupported()
           && getConnection().isMessageCompressionDesired()
           && messageProps.getContentEncoding() == null)
        {
            byte[] compressed = GZIPUtils.compressBufferToArray(data);
            if(compressed != null)
            {
                messageProps.setContentEncoding(GZIPUtils.GZIP_CONTENT_ENCODING);
                data = ByteBuffer.wrap(compressed);
            }
        }


        messageProps.setContentLength(data == null ? 0 : data.remaining());

        // send the message
        try
        {
            org.apache.qpid.transport.Session ssn = (org.apache.qpid.transport.Session)
                ((AMQSession_0_10) getSession()).getQpidSession();

            // if true, we need to sync the delivery of this message
            boolean sync = false;

            sync = ( (getPublishMode() == PublishMode.SYNC_PUBLISH_ALL) ||
                     (getPublishMode() == PublishMode.SYNC_PUBLISH_PERSISTENT &&
                         deliveryMode == DeliveryMode.PERSISTENT)
                   );  
            
            boolean unreliable = (destination.getDestSyntax() == DestSyntax.ADDR) &&
                                 (destination.getLink().getReliability() == Reliability.UNRELIABLE);
            

            ByteBuffer buffer = data == null ? ByteBuffer.allocate(0) : data.slice();
            
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
            JMSException jmse = new JMSException("Exception when sending message:" + e.getMessage());
            jmse.setLinkedException(e);
            jmse.initCause(e);
            throw jmse;
        }
    }

    @Override
    public boolean isBound(AMQDestination destination) throws JMSException
    {
        return getSession().isQueueBound(destination);
    }
    
    // We should have a close and closed method to distinguish between normal close
    // and a close due to session or connection error.
    @Override
    public void close() throws JMSException
    {
        super.close();
        AMQDestination dest = getAMQDestination();
        AMQSession_0_10 ssn = (AMQSession_0_10) getSession();
        if (!ssn.isClosed() && dest != null && dest.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
            try
            {
                if (dest.getDelete() == AddressOption.ALWAYS ||
                    dest.getDelete() == AddressOption.SENDER )
                {
                    ssn.handleNodeDelete(dest);
                }
                ssn.handleLinkDelete(dest);
            }
            catch(TransportException e)
            {
                throw getSession().toJMSException("Exception while closing producer:" + e.getMessage(), e);
            }
            catch (AMQException e)
            {
                JMSException ex = new JMSException("Exception while closing producer:" + e.getMessage());
                ex.setLinkedException(e);
                ex.initCause(e);
                throw ex;
            }
        }
    }

}

