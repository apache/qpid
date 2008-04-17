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
import org.apache.qpid.client.message.*;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.transport.*;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.filter.MessageFilter;
import org.apache.qpidity.filter.JMSSelectorFilter;

import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is a 0.10 message consumer.
 */
public class BasicMessageConsumer_0_10 extends BasicMessageConsumer<Struct[], ByteBuffer>
        implements org.apache.qpidity.nclient.util.MessageListener
{

    /**
     * This class logger
     */
    protected final Logger _logger = LoggerFactory.getLogger(getClass());

    /**
     * The message selector filter associated with this consumer message selector
     */
    private MessageFilter _filter = null;

    /**
     * The underlying QpidSession
     */
    private AMQSession_0_10 _0_10session;

    /**
     * Indicates whether this consumer receives pre-acquired messages
     */
    private boolean _preAcquire = true;

    /**
     * Indicate whether this consumer is started.
     */
    private boolean _isStarted = false;

    /**
     * Specify whether this consumer is performing a sync receive
     */
    private final AtomicBoolean _syncReceive = new AtomicBoolean(false);

    //--- constructor
    protected BasicMessageConsumer_0_10(int channelId, AMQConnection connection, AMQDestination destination,
                                        String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory,
                                        AMQSession session, AMQProtocolHandler protocolHandler,
                                        FieldTable rawSelectorFieldTable, int prefetchHigh, int prefetchLow,
                                        boolean exclusive, int acknowledgeMode, boolean noConsume, boolean autoClose)
            throws JMSException
    {
        super(channelId, connection, destination, messageSelector, noLocal, messageFactory, session, protocolHandler,
              rawSelectorFieldTable, prefetchHigh, prefetchLow, exclusive, acknowledgeMode, noConsume, autoClose);
        _0_10session = (AMQSession_0_10) session;
        if (messageSelector != null && !messageSelector.equals(""))
        {
            try
            {
                _filter = new JMSSelectorFilter(messageSelector);
            }
            catch (QpidException e)
            {
                throw new InvalidSelectorException("cannot create consumer because of selector issue");
            }
            if (destination instanceof AMQQueue)
            {
                _preAcquire = false;
            }
        }
        _isStarted = connection.started();
    }

    // ----- Interface org.apache.qpidity.client.util.MessageListener

    /**
     *
     * This is invoked by the session thread when emptying the session message queue.
     * We first check if the message is valid (match the selector) and then deliver it to the
     * message listener or to the sync consumer queue.
     *
     * @param jmsMessage this message has already been processed so can't redo preDeliver
     * @param channelId
     */
    public void notifyMessage(AbstractJMSMessage jmsMessage, int channelId)
    {
        boolean messageOk = false;
        try
        {
            messageOk = checkPreConditions(jmsMessage);
        }
        catch (AMQException e)
        {
            _logger.error("Receivecd an Exception when receiving message",e);
            try
            {

                getSession().getAMQConnection().getExceptionListener()
                        .onException(new JMSAMQException("Error when receiving message", e));
            }
            catch (Exception e1)
            {
                // we should silently log thie exception as it only hanppens when the connection is closed
                _logger.error("Exception when receiving message", e1);
            }
        }
        if (messageOk)
        {
            if (isMessageListenerSet() && ! getSession().prefetch())
            {
                _0_10session.getQpidSession().messageFlow(getConsumerTag().toString(),
                                                          MessageCreditUnit.MESSAGE, 1);
            }
            _logger.debug("messageOk, trying to notify");
            super.notifyMessage(jmsMessage, channelId);
        }
    }



    /**
     * This method is invoked by the transport layer when a message is delivered for this
     * consumer. The message is transformed and pass to the session.
     * @param message an 0.10 message
     */
    public void onMessage(Message message)
    {
        int channelId = getSession().getChannelId();
        long deliveryId = message.getMessageTransferId();
        String consumerTag = getConsumerTag().toString();
        AMQShortString exchange;
        AMQShortString routingKey;
        boolean redelivered = false;
        Struct[] headers = {message.getMessageProperties(), message.getDeliveryProperties()};
        if( message.getDeliveryProperties() != null )
        {
            exchange = new AMQShortString(message.getDeliveryProperties().getExchange());
            routingKey = new AMQShortString(message.getDeliveryProperties().getRoutingKey());
            redelivered = message.getDeliveryProperties().getRedelivered();
        }
        else
        {
            exchange = new AMQShortString("");
            routingKey = new AMQShortString("");
            headers[1] = new DeliveryProperties();
        }
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
        // if there is a replyto destination then we need to request the exchange info
        ReplyTo replyTo = message.getMessageProperties().getReplyTo();
        if (replyTo != null && replyTo.getExchange() != null && !replyTo.getExchange().equals(""))
        {
            // <exch_class>://<exch_name>/[<destination>]/[<queue>]?<option>='<value>'[,<option>='<value>']*
            // the exchnage class will be set later from within the sesion thread
            String replyToUrl =  message.getMessageProperties().getReplyTo()
                    .getExchange() + "/" + message.getMessageProperties().getReplyTo()
                    .getRoutingKey() + "/" + message.getMessageProperties().getReplyTo().getRoutingKey();
            newMessage.setReplyToURL(replyToUrl);
        }
        newMessage.setContentHeader(headers);
        getSession().messageReceived(newMessage);
        // else ignore this message
    }

    //----- overwritten methods

    /**
     * This method is invoked when this consumer is stopped.
     * It tells the broker to stop delivering messages to this consumer.
     */
    public void sendCancel() throws JMSAMQException
    {
        ((AMQSession_0_10) getSession()).getQpidSession().messageCancel(getConsumerTag().toString());
        ((AMQSession_0_10) getSession()).getQpidSession().sync();
        // confirm cancel
        getSession().confirmConsumerCancelled(getConsumerTag());
        try
        {
            ((AMQSession_0_10) getSession()).getCurrentException();
        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Problem when stopping consumer", e);
        }
    }

    void notifyMessage(UnprocessedMessage messageFrame, int channelId)
    {
        // if there is a replyto destination then we need to request the exchange info
        String replyToURL = messageFrame.getReplyToURL();
        if (replyToURL != null && !replyToURL.equals(""))
        {
            AMQShortString  shortExchangeName = new AMQShortString( replyToURL.substring(0, replyToURL.indexOf('/')));
            String replyToUrl = "://" + replyToURL;
            if (shortExchangeName.equals(ExchangeDefaults.TOPIC_EXCHANGE_NAME))
            {
                replyToUrl = ExchangeDefaults.TOPIC_EXCHANGE_CLASS + replyToUrl;
            }
            else if (shortExchangeName.equals(ExchangeDefaults.DIRECT_EXCHANGE_NAME))
            {
                replyToUrl = ExchangeDefaults.DIRECT_EXCHANGE_CLASS + replyToUrl;
            }
            else if (shortExchangeName.equals(ExchangeDefaults.HEADERS_EXCHANGE_NAME))
            {
                replyToUrl = ExchangeDefaults.HEADERS_EXCHANGE_CLASS + replyToUrl;
            }
            else if (shortExchangeName.equals(ExchangeDefaults.FANOUT_EXCHANGE_NAME))
            {
                replyToUrl = ExchangeDefaults.FANOUT_EXCHANGE_CLASS + replyToUrl;
            }
            else
            {
                Future<ExchangeQueryResult> future =
                        ((AMQSession_0_10) getSession()).getQpidSession().exchangeQuery(shortExchangeName.toString());
                ExchangeQueryResult res = future.get();
                // <exch_class>://<exch_name>/[<destination>]/[<queue>]?<option>='<value>'[,<option>='<value>']*
                replyToUrl = res.getType() + replyToUrl;
            }
            ((UnprocessedMessage_0_10) messageFrame).setReplyToURL(replyToUrl);
        }
        super.notifyMessage(messageFrame, channelId);
    }

    protected void preApplicationProcessing(AbstractJMSMessage jmsMsg) throws JMSException
    {
        _session.addUnacknowledgedMessage(jmsMsg.getDeliveryTag());
        _session.setInRecovery(false);
    }

    public AbstractJMSMessage createJMSMessageFromUnprocessedMessage(
            UnprocessedMessage<Struct[], ByteBuffer> messageFrame) throws Exception
    {
        return _messageFactory.createMessage(messageFrame.getDeliveryTag(), messageFrame.isRedelivered(),
                                             messageFrame.getExchange(), messageFrame.getRoutingKey(),
                                             messageFrame.getContentHeader(), messageFrame.getBodies(),
                                             messageFrame.getReplyToURL());
    }

    // private methods
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
        // TODO Use a tag for fiding out if message filtering is done here or by the broker.
        try
        {
            if (_messageSelector != null && !_messageSelector.equals(""))
            {
                messageOk = _filter.matches(message);
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
                    _logger.debug("Message not OK, releasing");
                }
                releaseMessage(message);
            }
            // if we are syncrhonously waiting for a message
            // and messages are not prefetched we then need to request another one
            if(! getSession().prefetch())
            {
               _0_10session.getQpidSession().messageFlow(getConsumerTag().toString(),
                                                         MessageCreditUnit.MESSAGE, 1);
            }
        }
        // now we need to acquire this message if needed
        // this is the case of queue with a message selector set
        if (!_preAcquire && messageOk && !isNoConsume())
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("filterMessage - trying to acquire message");
            }
            messageOk = acquireMessage(message);
            _logger.debug("filterMessage - *************************************");
            _logger.debug("filterMessage - message acquire status : " + messageOk);
            _logger.debug("filterMessage - *************************************");
        }
        return messageOk;
    }


    /**
     * Acknowledge a message
     *
     * @param message The message to be acknowledged
     * @throws AMQException If the message cannot be acquired due to some internal error.
     */
    private void acknowledgeMessage(AbstractJMSMessage message) throws AMQException
    {
        if (!_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getDeliveryTag());
            _0_10session.getQpidSession().messageAcknowledge(ranges);
            _0_10session.getCurrentException();
        }
    }

    /**
     * Release a message
     *
     * @param message The message to be released
     * @throws AMQException If the message cannot be released due to some internal error.
     */
    private void releaseMessage(AbstractJMSMessage message) throws AMQException
    {
        if (_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getDeliveryTag());
            _0_10session.getQpidSession().messageRelease(ranges);
            _0_10session.getCurrentException();
        }
    }

    protected void rollbackReceivedMessages()
    {
        // do nothing as the rollback operation will do the job.
    }

    /**
     * Acquire a message
     *
     * @param message The message to be acquired
     * @return true if the message has been acquired, false otherwise.
     * @throws AMQException If the message cannot be acquired due to some internal error.
     */
    private boolean acquireMessage(AbstractJMSMessage message) throws AMQException
    {
        boolean result = false;
        if (!_preAcquire)
        {
            RangeSet ranges = new RangeSet();
            ranges.add(message.getDeliveryTag());

            Acquired acq = _0_10session.getQpidSession().messageAcquire(ranges).get();

            RangeSet acquired = acq.getTransfers();
            if (acquired != null && acquired.size() > 0)
            {
                result = true;
            }
        }
        return result;
    }


    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        super.setMessageListener(messageListener);
        if (messageListener != null && ! getSession().prefetch())
        {
            _0_10session.getQpidSession().messageFlow(getConsumerTag().toString(),
                                                      MessageCreditUnit.MESSAGE, 1);
        }
        if (messageListener != null && !_synchronousQueue.isEmpty())
        {
            Iterator messages=_synchronousQueue.iterator();
            while (messages.hasNext())
            {
                AbstractJMSMessage message=(AbstractJMSMessage) messages.next();
                messages.remove();
                _session.rejectMessage(message, true);
            }
        }
    }

    public boolean isStrated()
    {
        return _isStarted;
    }

    public void start()
    {
        _isStarted = true;
        if (_syncReceive.get())
        {
            _0_10session.getQpidSession().messageFlow(getConsumerTag().toString(),
                                                      MessageCreditUnit.MESSAGE, 1);
        }
    }

    public void stop()
    {
        _isStarted = false;
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
        if (isStrated() && ! getSession().prefetch() && _synchronousQueue.isEmpty())
        {
            _0_10session.getQpidSession().messageFlow(getConsumerTag().toString(),
                                                      MessageCreditUnit.MESSAGE, 1);
        }
        if (! getSession().prefetch())
        {
            _syncReceive.set(true);
        }
        Object o = super.getMessageFromQueue(l);
        if (! getSession().prefetch())
        {
            _syncReceive.set(false);
        }
        return o;
    }

}