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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination.AddressOption;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.AMQMessageDelegate_0_10;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage_0_10;
import org.apache.qpid.common.ServerPropertyNames;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.transport.Acquired;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.TransportException;

/**
 * This is a 0.10 message consumer.
 */
public class BasicMessageConsumer_0_10 extends BasicMessageConsumer<UnprocessedMessage_0_10>
{

    /**
     * This class logger
     */
    private final Logger _logger = LoggerFactory.getLogger(getClass());

    /**
     * The underlying QpidSession
     */
    private AMQSession_0_10 _0_10session;

    /**
     * Indicates whether this consumer receives pre-acquired messages
     */
    private final boolean _preAcquire;

    /**
     * Specify whether this consumer is performing a sync receive
     */
    private final AtomicBoolean _syncReceive = new AtomicBoolean(false);
    private String _consumerTagString;
    
    private final long _capacity;

    /** Flag indicating if the server supports message selectors */
    private final boolean _serverJmsSelectorSupport;

    protected BasicMessageConsumer_0_10(int channelId, AMQConnection connection, AMQDestination destination,
                                        String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory,
                                        AMQSession<?,?> session, FieldTable rawSelector,
                                        int prefetchHigh, int prefetchLow, boolean exclusive,
                                        int acknowledgeMode, boolean browseOnly, boolean autoClose)
            throws JMSException
    {
        super(channelId, connection, destination, messageSelector, noLocal, messageFactory, session, rawSelector,
                prefetchHigh, prefetchLow, exclusive, acknowledgeMode, browseOnly, autoClose);
        _0_10session = (AMQSession_0_10) session;

        _serverJmsSelectorSupport = connection.isSupportedServerFeature(ServerPropertyNames.FEATURE_QPID_JMS_SELECTOR);
        _preAcquire = evaluatePreAcquire(browseOnly, destination, _serverJmsSelectorSupport);

        _capacity = evaluateCapacity(destination);

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
    }

    @Override public void setConsumerTag(int consumerTag)
    {
        super.setConsumerTag(consumerTag);
        _consumerTagString = String.valueOf(consumerTag);
    }

    public String getConsumerTagString()
    {
        return _consumerTagString;
    }

    /**
     *
     * This is invoked by the session thread when emptying the session message queue.
     * We first check if the message is valid (match the selector) and then deliver it to the
     * message listener or to the sync consumer queue.
     *
     * @param jmsMessage this message has already been processed so can't redo preDeliver
     */
    @Override public void notifyMessage(AbstractJMSMessage jmsMessage)
    {
        try
        {
            if (checkPreConditions(jmsMessage))
            {
                if (isMessageListenerSet() && _capacity == 0)
                {
                    messageFlow();
                }
                _logger.debug("messageOk, trying to notify");
                super.notifyMessage(jmsMessage);
            }
            else
            {
                // if we are synchronously waiting for a message
                // and messages are not pre-fetched we then need to request another one
                if(_capacity == 0)
                {
                   messageFlow();
                }
            }
        }
        catch (AMQException e)
        {
            _logger.error("Receivecd an Exception when receiving message",e);
            getSession().getAMQConnection().exceptionReceived(e);
        }
    }

    /**
     * This method is invoked when this consumer is stopped.
     * It tells the broker to stop delivering messages to this consumer.
     */
    @Override void sendCancel() throws AMQException
    {
        _0_10session.getQpidSession().messageCancel(getConsumerTagString());
        postSubscription();
        try
        {
            _0_10session.getQpidSession().sync();
            getSession().confirmConsumerCancelled(getConsumerTag()); // confirm cancel
        }
        catch (SessionException se)
        {
            _0_10session.setCurrentException(se);
        }

        AMQException amqe = _0_10session.getCurrentException();
        if (amqe != null)
        {
            throw amqe;
        }
    }

    @Override void notifyMessage(UnprocessedMessage_0_10 messageFrame)
    {
        super.notifyMessage(messageFrame);
    }

    @Override
    protected void preDeliver(AbstractJMSMessage jmsMsg)
    {
        super.preDeliver(jmsMsg);

        if (getAcknowledgeMode() == org.apache.qpid.jms.Session.NO_ACKNOWLEDGE)
        {
            //For 0-10 we need to ensure that all messages are indicated processed in some way to
            //ensure their AMQP command-id is marked completed, and so we must send a completion
            //even for no-ack messages even though there isnt actually an 'acknowledgement' occurring.
            //Add message to the unacked message list to ensure we dont lose record of it before
            //sending a completion of some sort.
            getSession().addUnacknowledgedMessage(jmsMsg.getDeliveryTag());
        }
    }

    @Override public AbstractJMSMessage createJMSMessageFromUnprocessedMessage(
            AMQMessageDelegateFactory delegateFactory, UnprocessedMessage_0_10 msg) throws Exception
    {
        AMQMessageDelegate_0_10.updateExchangeTypeMapping(msg.getMessageTransfer().getHeader(), ((AMQSession_0_10)getSession()).getQpidSession());
        return getMessageFactory().createMessage(msg.getMessageTransfer());
    }

    /**
     * Check whether a message can be delivered to this consumer.
     *
     * @param message The message to be checked.
     * @return true if the message matches the selector and can be acquired, false otherwise.
     * @throws AMQException If the message preConditions cannot be checked due to some internal error.
     */
    private boolean checkPreConditions(AbstractJMSMessage message) throws AMQException
    {
        boolean messageOk = true;
        try
        {
            if (!_serverJmsSelectorSupport && getMessageSelectorFilter() != null)
            {
                messageOk = getMessageSelectorFilter().matches(message);
            }
        }
        catch (Exception e)
        {
            throw new AMQException(AMQConstant.INTERNAL_ERROR, "Error when evaluating message selector", e);
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("messageOk " + messageOk);
            _logger.debug("_preAcquire " + _preAcquire);
        }

        if (!messageOk)
        {
            if (_preAcquire)
            {
                // this is the case for topics
                // We need to ack this message
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("filterMessage - trying to ack message");
                }
                acknowledgeMessage(message);
            }
            else
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("filterMessage - not ack'ing message as not acquired");
                }
                flushUnwantedMessage(message);
            }
        }
        else if (!_preAcquire && !isBrowseOnly())
        {
            // now we need to acquire this message if needed
            // this is the case of queue with a message selector set
            if (_logger.isDebugEnabled())
            {
                _logger.debug("filterMessage - trying to acquire message");
            }
            messageOk = acquireMessage(message);
            _logger.debug("filterMessage - message acquire status : " + messageOk);
        }

        return messageOk;
    }


    /**
     * Acknowledge a message
     *
     * @param message The message to be acknowledged
     * @throws AMQException If the message cannot be acquired due to some internal error.
     */
    private void acknowledgeMessage(final AbstractJMSMessage message) throws AMQException
    {
        _0_10session.messageAcknowledge
            (Range.newInstance((int) message.getDeliveryTag()),
             getAcknowledgeMode() != org.apache.qpid.jms.Session.NO_ACKNOWLEDGE);

        final AMQException amqe = _0_10session.getCurrentException();
        if (amqe != null)
        {
            throw amqe;
        }
    }

    /**
     * Flush an unwanted message. For 0-10 we need to ensure that all messages are indicated
     * processed to ensure their AMQP command-id is marked completed.
     *
     * @param message The unwanted message to be flushed
     * @throws AMQException If the unwanted message cannot be flushed due to some internal error.
     */
    private void flushUnwantedMessage(final AbstractJMSMessage message) throws AMQException
    {
        _0_10session.flushProcessed(Range.newInstance((int) message.getDeliveryTag()),false);

        final AMQException amqe = _0_10session.getCurrentException();
        if (amqe != null)
        {
            throw amqe;
        }
    }

    /**
     * Acquire a message
     *
     * @param message The message to be acquired
     * @return true if the message has been acquired, false otherwise.
     * @throws AMQException If the message cannot be acquired due to some internal error.
     */
    private boolean acquireMessage(final AbstractJMSMessage message) throws AMQException
    {
        boolean result = false;

        final Acquired acq = _0_10session.getQpidSession().messageAcquire(Range.newInstance((int)message.getDeliveryTag())).get();

        final RangeSet acquired = acq.getTransfers();
        if (acquired != null && acquired.size() > 0)
        {
            result = true;
        }
        return result;
    }

    private void messageFlow()
    {
        _0_10session.getQpidSession().messageFlow(getConsumerTagString(),
                                                  MessageCreditUnit.MESSAGE, 1,
                                                  Option.UNRELIABLE);
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        super.setMessageListener(messageListener);
        try
        {
            if (messageListener != null && _capacity == 0)
            {
                messageFlow();
            }
            if (messageListener != null && !getSynchronousQueue().isEmpty())
            {
                Iterator messages= getSynchronousQueue().iterator();
                while (messages.hasNext())
                {
                    AbstractJMSMessage message=(AbstractJMSMessage) messages.next();
                    messages.remove();
                    getSession().rejectMessage(message, true);
                }
            }
        }
        catch(TransportException e)
        {
            throw getSession().toJMSException("Exception while setting message listener:" + e.getMessage(), e);
        }
    }

    public void failedOverPost()
    {
        if (_0_10session.isStarted() && _syncReceive.get())
        {
            messageFlow();
        }
    }

    /**
     * When messages are not prefetched we need to request a message from the
     * broker.
     * Note that if the timeout is too short a message may be queued in _synchronousQueue until
     * this consumer closes or request it.
     * @param l
     * @return
     * @throws InterruptedException
     */
    public Object getMessageFromQueue(long l) throws InterruptedException
    {
        if (_capacity == 0)
        {
            _syncReceive.set(true);
        }
        if (_0_10session.isStarted() && isMessageListenerSet() && _capacity == 0 && getSynchronousQueue().isEmpty())
        {
            messageFlow();
        }
        Object o = super.getMessageFromQueue(l);
        if (o == null && _0_10session.isStarted())
        {
           
            _0_10session.getQpidSession().messageFlush
                (getConsumerTagString(), Option.UNRELIABLE, Option.SYNC);
            _0_10session.getQpidSession().sync();
            _0_10session.getQpidSession().messageFlow
                (getConsumerTagString(), MessageCreditUnit.BYTE,
                 0xFFFFFFFF, Option.UNRELIABLE);
            
            if (_capacity > 0)
            {
                _0_10session.getQpidSession().messageFlow
                                               (getConsumerTagString(),
                                                MessageCreditUnit.MESSAGE,
                                                _capacity,
                                                Option.UNRELIABLE);
            }
            _0_10session.syncDispatchQueue(false);
            o = super.getMessageFromQueue(-1);
        }
        if (_capacity == 0)
        {
            _syncReceive.set(false);
        }
        return o;
    }

    void postDeliver(AbstractJMSMessage msg)
    {
        super.postDeliver(msg);

        switch (getAcknowledgeMode())
        {
            case Session.SESSION_TRANSACTED:
                _0_10session.sendTxCompletionsIfNecessary();
                break;
            case Session.NO_ACKNOWLEDGE:
                if (!getSession().isInRecovery())
                {
                  getSession().acknowledgeMessage(msg.getDeliveryTag(), false);
                }
                break;
            case Session.AUTO_ACKNOWLEDGE:
                if (!getSession().isInRecovery() && getSession().getAMQConnection().getSyncAck())
                {
                    ((AMQSession_0_10) getSession()).getQpidSession().sync();
                }
                break;
        }
        
    }

    Message receiveBrowse() throws JMSException
    {
        return receiveNoWait();
    }

    @Override public void rollbackPendingMessages()
    {
        if (getSynchronousQueue().size() > 0)
        {
            RangeSet ranges = RangeSetFactory.createRangeSet();
            Iterator iterator = getSynchronousQueue().iterator();
            while (iterator.hasNext())
            {

                Object o = iterator.next();
                if (o instanceof AbstractJMSMessage)
                {
                    ranges.add((int) ((AbstractJMSMessage) o).getDeliveryTag());
                    iterator.remove();
                }
                else
                {
                    _logger.error("Queue contained a :" + o.getClass()
                                  + " unable to reject as it is not an AbstractJMSMessage. Will be cleared");
                    iterator.remove();
                }
            }

            _0_10session.flushProcessed(ranges, false);
            _0_10session.getQpidSession().messageRelease(ranges);
            clearReceiveQueue();
        }
    }

    
    void postSubscription() throws AMQException
    {
        AMQDestination dest = this.getDestination();
        if (dest != null && dest.getDestSyntax() == AMQDestination.DestSyntax.ADDR)
        {
            if (dest.getDelete() == AddressOption.ALWAYS ||
                dest.getDelete() == AddressOption.RECEIVER )
            {
                getSession().handleNodeDelete(dest);
            }
            // Subscription queue is handled as part of linkDelete method.
            getSession().handleLinkDelete(dest);
            if (!isDurableSubscriber())
            {
                ((AMQSession_0_10) getSession()).deleteSubscriptionQueue(dest);
            }
        }
    }

    long getCapacity()
    {
        return _capacity;
    }

    boolean isPreAcquire()
    {
        return _preAcquire;
    }

    private boolean evaluatePreAcquire(boolean browseOnly, AMQDestination destination, boolean serverJmsSelectorSupport)
    {
        boolean preAcquire;
        if (browseOnly)
        {
            preAcquire = false;
        }
        else
        {
            boolean isQueue = (destination instanceof AMQQueue || getDestination().getAddressType() == AMQDestination.QUEUE_TYPE);
            if (!serverJmsSelectorSupport && isQueue && getMessageSelectorFilter() != null)
            {
                preAcquire = false;
            }
            else
            {
                preAcquire = true;
            }
        }
        return preAcquire;
    }

    private long evaluateCapacity(AMQDestination destination)
    {
        long capacity = 0;
        if (destination.getLink() != null && destination.getLink().getConsumerCapacity() >= 0)
        {
            capacity = destination.getLink().getConsumerCapacity();
        }
        else if (getSession().prefetch())
        {
            capacity = getSession().getPrefetch();
        }
        return capacity;
    }

    @Override
    public Message receive(final long l) throws JMSException
    {
        long capacity = getCapacity();
        try
        {
            AMQSession_0_10 session = (AMQSession_0_10) getSession();

            if (capacity == 0 && getMessageListener() == null)
            {
                session.getQpidSession().messageFlow(getConsumerTagString(),
                                                     MessageCreditUnit.MESSAGE, 1,
                                                     Option.UNRELIABLE);

                session.sync();

            }

            Message message = super.receive(l);

            if (message == null && capacity == 0 && getMessageListener() == null)
            {
                session.getQpidSession().messageFlow(getConsumerTagString(),
                                                     MessageCreditUnit.MESSAGE, 0,
                                                     Option.UNRELIABLE);
                session.sync();

                message = super.receiveNoWait();
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
        long capacity = getCapacity();
        try
        {
            AMQSession_0_10 session = (AMQSession_0_10) getSession();

            if (capacity == 0 && getMessageListener() == null)
            {
                session.getQpidSession().messageFlow(getConsumerTagString(),
                                                     MessageCreditUnit.MESSAGE, 1,
                                                     Option.UNRELIABLE);

                session.sync();
            }
            Message message = super.receiveNoWait();
            if (message == null && capacity == 0 && getMessageListener() == null)
            {
                session.getQpidSession().messageFlow(getConsumerTagString(),
                                                     MessageCreditUnit.MESSAGE, 0,
                                                     Option.UNRELIABLE);
                session.sync();

                message = super.receiveNoWait();
            }
            return message;
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }

    }
}
