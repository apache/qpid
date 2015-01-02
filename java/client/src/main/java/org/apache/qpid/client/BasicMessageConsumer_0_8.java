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
package org.apache.qpid.client;

import javax.jms.JMSException;
import javax.jms.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage_0_8;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicCancelBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.ConnectionURL;

public class BasicMessageConsumer_0_8 extends BasicMessageConsumer<UnprocessedMessage_0_8>
{
    private final Logger _logger = LoggerFactory.getLogger(getClass());
    private AMQSession_0_8.DestinationCache<AMQTopic> _topicDestinationCache;
    private AMQSession_0_8.DestinationCache<AMQQueue> _queueDestinationCache;

    private final RejectBehaviour _rejectBehaviour;

    protected BasicMessageConsumer_0_8(int channelId, AMQConnection connection, AMQDestination destination,
                                       String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory, AMQSession_0_8 session,
                                       FieldTable rawSelector, int prefetchHigh, int prefetchLow, boolean exclusive,
                                       int acknowledgeMode, boolean browseOnly, boolean autoClose) throws JMSException
    {
        super(channelId, connection, destination,messageSelector,noLocal,messageFactory,session,
              rawSelector, prefetchHigh, prefetchLow, exclusive, acknowledgeMode,
              browseOnly, autoClose);
        final FieldTable consumerArguments = getArguments();
        if (isAutoClose())
        {
            consumerArguments.put(AMQPFilterTypes.AUTO_CLOSE.getValue(), Boolean.TRUE);
        }

        if (isBrowseOnly())
        {
            consumerArguments.put(AMQPFilterTypes.NO_CONSUME.getValue(), Boolean.TRUE);
        }

        _topicDestinationCache = session.getTopicDestinationCache();
        _queueDestinationCache = session.getQueueDestinationCache();


        // This is due to the Destination carrying the temporary subscription name which is incorrect.
        if (destination.isAddressResolved() && AMQDestination.TOPIC_TYPE == destination.getAddressType())
        {
            boolean namedQueue = destination.getLink() != null && destination.getLink().getName() != null ;

            if (!namedQueue)
            {
                setDestination(destination.copyDestination());
                getDestination().setQueueName(null);
            }
        }

        if (destination.getRejectBehaviour() != null)
        {
            _rejectBehaviour = destination.getRejectBehaviour();
        }
        else
        {
            ConnectionURL connectionURL = connection.getConnectionURL();
            String rejectBehaviour = connectionURL.getOption(ConnectionURL.OPTIONS_REJECT_BEHAVIOUR);
            if (rejectBehaviour != null)
            {
                _rejectBehaviour = RejectBehaviour.valueOf(rejectBehaviour.toUpperCase());
            }
            else
            {
                // use the default value for all connections, if not set
                rejectBehaviour = System.getProperty(ClientProperties.REJECT_BEHAVIOUR_PROP_NAME, RejectBehaviour.NORMAL.toString());
                _rejectBehaviour = RejectBehaviour.valueOf( rejectBehaviour.toUpperCase());
            }
        }
    }

    @Override
    public AMQSession_0_8 getSession()
    {
        return (AMQSession_0_8) super.getSession();
    }

    void sendCancel() throws AMQException, FailoverException
    {
        BasicCancelBody body = getSession().getMethodRegistry().createBasicCancelBody(new AMQShortString(String.valueOf(getConsumerTag())), false);

        final AMQFrame cancelFrame = body.generateFrame(getChannelId());

        getConnection().getProtocolHandler().syncWrite(cancelFrame, BasicCancelOkBody.class);
        postSubscription();
        getSession().sync();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("CancelOk'd for consumer:" + debugIdentity());
        }
    }

    void postSubscription() throws AMQException
    {
        AMQDestination dest = this.getDestination();
        if (dest != null && dest.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
            if (dest.getDelete() == AMQDestination.AddressOption.ALWAYS ||
                dest.getDelete() == AMQDestination.AddressOption.RECEIVER )
            {
                getSession().handleNodeDelete(dest);
            }
            // Subscription queue is handled as part of linkDelete method.
            getSession().handleLinkDelete(dest);
            if (!isDurableSubscriber())
            {
                getSession().deleteSubscriptionQueue(dest);
            }
        }
    }

    public AbstractJMSMessage createJMSMessageFromUnprocessedMessage(AMQMessageDelegateFactory delegateFactory, UnprocessedMessage_0_8 messageFrame)throws Exception
    {

        return getMessageFactory().createMessage(messageFrame.getDeliveryTag(),
                messageFrame.isRedelivered(), messageFrame.getExchange() == null ? AMQShortString.EMPTY_STRING : messageFrame.getExchange(),
                messageFrame.getRoutingKey(), messageFrame.getContentHeader(), messageFrame.getBodies(),
                _queueDestinationCache, _topicDestinationCache, getAddressType());

    }

    Message receiveBrowse() throws JMSException
    {
        return receive();
    }

    public RejectBehaviour getRejectBehaviour()
    {
        return _rejectBehaviour;
    }


    @Override
    public Message receive(final long l) throws JMSException
    {
        boolean manageCredit = getSession().isManagingCredit();
        boolean creditModified = false;
        try
        {
            if (manageCredit)
            {
                creditModified = getSession().ensureCreditForReceive();
            }
            Message message = super.receive(l);
            if (creditModified && message == null)
            {
                getSession().reduceCreditAfterAcknowledge();
            }
            if (manageCredit && message != null)
            {
                getSession().updateCurrentPrefetch(1);
            }
            return message;
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
    }

    @Override
    public Message receiveNoWait() throws JMSException
    {
        boolean manageCredit = getSession().isManagingCredit();
        boolean creditModified = false;
        try
        {
            if (manageCredit)
            {
                creditModified = getSession().ensureCreditForReceive();
                if (creditModified)
                {
                    getSession().sync();
                }
            }
            Message message = super.receiveNoWait();
            if (creditModified && message == null)
            {
                getSession().reduceCreditAfterAcknowledge();
            }
            if (manageCredit && message != null)
            {
                getSession().updateCurrentPrefetch(1);
            }
            return message;
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
    }


    void postDeliver(AbstractJMSMessage msg)
    {
        getSession().reduceCreditInPostDeliver();
        super.postDeliver(msg);
    }
}
