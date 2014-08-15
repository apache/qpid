/*
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
 */
package org.apache.qpid.amqp_1_0.jms.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import org.apache.qpid.amqp_1_0.client.AcknowledgeMode;
import org.apache.qpid.amqp_1_0.client.ConnectionErrorException;
import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.client.Transaction;
import org.apache.qpid.amqp_1_0.jms.MessageConsumer;
import org.apache.qpid.amqp_1_0.jms.Queue;
import org.apache.qpid.amqp_1_0.jms.QueueReceiver;
import org.apache.qpid.amqp_1_0.jms.Session;
import org.apache.qpid.amqp_1_0.jms.TemporaryDestination;
import org.apache.qpid.amqp_1_0.jms.Topic;
import org.apache.qpid.amqp_1_0.jms.TopicSubscriber;
import org.apache.qpid.amqp_1_0.jms.MessageConsumerException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Filter;
import org.apache.qpid.amqp_1_0.type.messaging.JMSSelectorFilter;
import org.apache.qpid.amqp_1_0.type.messaging.Modified;
import org.apache.qpid.amqp_1_0.type.messaging.NoLocalFilter;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Error;

public class MessageConsumerImpl implements MessageConsumer, QueueReceiver, TopicSubscriber
{
    private static final Symbol NO_LOCAL_FILTER_NAME = Symbol.valueOf("no-local");
    private static final Symbol JMS_SELECTOR_FILTER_NAME = Symbol.valueOf("jms-selector");
    private String _selector;
    private boolean _noLocal;
    private DestinationImpl _destination;
    private SessionImpl _session;
    private Receiver _receiver;
    private Binary _lastUnackedMessage;
    MessageListener _messageListener;

    private boolean _isQueueConsumer;
    private boolean _isTopicSubscriber;

    private boolean _closed = false;
    private String _linkName;
    private boolean _durable;
    private Collection<Binary> _txnMsgs = Collections.synchronizedCollection(new ArrayList<Binary>());
    private Binary _lastTxnUpdate;
    private final List<Message> _recoverReplayMessages = new ArrayList<Message>();
    private final List<Message> _replaymessages = new ArrayList<Message>();
    private int _maxPrefetch = 100;

    MessageConsumerImpl(final Destination destination,
                        final SessionImpl session,
                        final String selector,
                        final boolean noLocal) throws JMSException
    {
        this(destination,session,selector,noLocal,null,false);
    }

    MessageConsumerImpl(final Destination destination,
                        final SessionImpl session,
                        final String selector,
                        final boolean noLocal,
                        final String linkName,
                        final boolean durable) throws JMSException
    {
        _selector = selector;
        _noLocal = noLocal;
        _linkName = linkName;
        _durable = durable;
        if(destination instanceof DestinationImpl)
        {
            _destination = (DestinationImpl) destination;
            if(destination instanceof javax.jms.Queue)
            {
                _isQueueConsumer = true;
            }
            else if(destination instanceof javax.jms.Topic)
            {
                _isTopicSubscriber = true;
            }
            if(destination instanceof TemporaryDestination)
            {
                ((TemporaryDestination)destination).addConsumer(this);
            }
        }
        else
        {
            throw new InvalidDestinationException("Invalid destination class " + destination.getClass().getName());
        }
        _session = session;
        if(session.getMaxPrefetch() != 0)
        {
            _maxPrefetch = session.getMaxPrefetch();
        }

        _receiver = createClientReceiver();
        _receiver.setRemoteErrorListener(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    final ExceptionListener exceptionListener = _session.getConnection().getExceptionListener();

                    if(exceptionListener != null)
                    {
                        final Error receiverError = _receiver.getError();

                        MessageConsumerException mce = new MessageConsumerException(
                                receiverError.getDescription(),
                                receiverError.getCondition().getValue().toString(),
                                _destination.getAddress());

                        exceptionListener.onException(mce);
                    }
                }
                catch (JMSException e)
                {

                }
            }
        });


    }

    protected Receiver createClientReceiver() throws JMSException
    {
        try
        {
            String targetAddr = _destination.getLocalTerminus() != null ? _destination.getLocalTerminus() : UUID.randomUUID().toString();
            return _session.getClientSession().createReceiver(_session.toAddress(_destination), targetAddr, AcknowledgeMode.ALO,
                    _linkName, _durable, getFilters(), null);
        }
        catch (ConnectionErrorException e)
        {
            Error error = e.getRemoteError();
            if(AmqpError.INVALID_FIELD.equals(error.getCondition()))
            {
                throw new InvalidSelectorException(e.getMessage());
            }
            else
            {
                JMSException jmsException =
                        new JMSException(e.getMessage(), error.getCondition().getValue().toString());
                jmsException.initCause(e);
                throw jmsException;

            }
        }
    }

    Map<Symbol, Filter> getFilters()
    {
        if(_selector == null || _selector.trim().equals(""))
        {
            if(_noLocal)
            {
                return Collections.singletonMap(NO_LOCAL_FILTER_NAME, (Filter) NoLocalFilter.INSTANCE);
            }
            else
            {
                return null;

            }
        }
        else if(_noLocal)
        {
            Map<Symbol, Filter> filters = new HashMap<Symbol, Filter>();
            filters.put(NO_LOCAL_FILTER_NAME, NoLocalFilter.INSTANCE);
            filters.put(JMS_SELECTOR_FILTER_NAME, new JMSSelectorFilter(_selector));
            return filters;
        }
        else
        {
            return Collections.singletonMap(JMS_SELECTOR_FILTER_NAME, (Filter)new JMSSelectorFilter(_selector));
        }


    }

    public String getMessageSelector() throws JMSException
    {
        checkClosed();
        return _selector;
    }

    public MessageListener getMessageListener() throws IllegalStateException
    {
        checkClosed();
        return _messageListener;
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        checkClosed();
        _messageListener = messageListener;
        _receiver.setMessageArrivalListener(new Receiver.MessageArrivalListener()
                {

                    public void messageArrived(final Receiver receiver)
                    {
                        _session.messageArrived(MessageConsumerImpl.this);
                    }
                });
        _session.messageListenerSet( this );

    }

    public MessageImpl receive() throws JMSException
    {
        checkClosed();
        MessageImpl message = receiveImpl(-1L);
        if(message == null)
        {
            throw new JMSException("Message could not be retrieved");
        }
        return message;
    }

    public MessageImpl receive(final long timeout) throws JMSException
    {
        checkClosed();
        // TODO - validate timeout > 0

        return receiveImpl(timeout);
    }

    public MessageImpl receiveNoWait() throws JMSException
    {
        checkClosed();
        return receiveImpl(0L);
    }

    private MessageImpl receiveImpl(long timeout) throws JMSException
    {

        org.apache.qpid.amqp_1_0.client.Message msg;
        boolean redelivery;
        if(_replaymessages.isEmpty())
        {
            checkReceiverError();
            msg = receive0(timeout);
            redelivery = false;
        }
        else
        {
            msg = _replaymessages.remove(0);
            redelivery = true;
        }

        if(msg != null)
        {
            preReceiveAction(msg);
        }
        return createJMSMessage(msg, redelivery);
    }

    void checkReceiverError() throws JMSException
    {
        final Error receiverError = _receiver.getError();
        if(receiverError != null)
        {
            JMSException jmsException =
                    new JMSException(receiverError.getDescription(), receiverError.getCondition().toString());

            throw jmsException;
        }
    }

    Message receive0(final long timeout)
    {

        Message message = _receiver.receive(timeout);
        if(_session.getAckModeEnum() == Session.AcknowledgeMode.CLIENT_ACKNOWLEDGE)
        {
            _recoverReplayMessages.add(message);
        }
        return message;
    }


    void acknowledge(final org.apache.qpid.amqp_1_0.client.Message msg)
    {
        _receiver.acknowledge(msg.getDeliveryTag(), _session.getTxn());
    }

    MessageImpl createJMSMessage(final Message msg, boolean redelivery)
    {
        if(msg != null)
        {
            MessageFactory factory = _session.getMessageFactory();
            final MessageImpl message = factory.createMessage(_destination, msg);
            message.setFromQueue(_isQueueConsumer);
            message.setFromTopic(_isTopicSubscriber);
            if(redelivery)
            {
                if(!message.getJMSRedelivered())
                {
                    message.setJMSRedelivered(true);
                }
            }

            return message;
        }
        else
        {
            return null;
        }
    }

    public void close() throws JMSException
    {
        if(!_closed)
        {
            _closed = true;

            closeUnderlyingReceiver(_receiver);

            if(_destination instanceof TemporaryDestination)
            {
                ((TemporaryDestination)_destination).removeConsumer(this);
            }
        }
    }

    protected void closeUnderlyingReceiver(Receiver receiver)
    {
        receiver.close();
    }

    private void checkClosed() throws IllegalStateException
    {
        if(_closed)
        {
            throw new javax.jms.IllegalStateException("Closed");
        }
    }

    void setLastUnackedMessage(final Binary deliveryTag)
    {
        _lastUnackedMessage = deliveryTag;
    }

    void preReceiveAction(final org.apache.qpid.amqp_1_0.client.Message msg)
    {
        int acknowledgeMode = _session.getAckModeEnum().ordinal();

        if(acknowledgeMode == Session.AUTO_ACKNOWLEDGE
           || acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE
           || acknowledgeMode == Session.SESSION_TRANSACTED)
        {
            acknowledge(msg);
            if(acknowledgeMode == Session.SESSION_TRANSACTED)
            {
                _txnMsgs.add(msg.getDeliveryTag());
            }
        }
        else if(acknowledgeMode == Session.CLIENT_ACKNOWLEDGE)
        {
            setLastUnackedMessage(msg.getDeliveryTag());
        }
    }

    void acknowledgeAll()
    {
        if(_lastUnackedMessage != null)
        {
            Transaction txn = _session.getTxn();
            _receiver.acknowledgeAll(_lastUnackedMessage, txn, null);
            if(txn != null)
            {
                _lastTxnUpdate = _lastUnackedMessage;
            }
            _lastUnackedMessage = null;

        }
        _recoverReplayMessages.clear();
        if(!_replaymessages.isEmpty())
        {
            _recoverReplayMessages.addAll(_replaymessages);
        }
    }

    void postRollback()
    {
        if(_lastTxnUpdate != null)
        {
            final Modified outcome = new Modified();
            outcome.setDeliveryFailed(true);
            _receiver.updateAll(outcome, _lastTxnUpdate);
            _lastTxnUpdate = null;
        }
        for(Binary tag : _txnMsgs)
        {
            _receiver.modified(tag);
        }
        _txnMsgs.clear();
    }

    void postCommit()
    {
        _lastTxnUpdate = null;
        _txnMsgs.clear();
    }

    public DestinationImpl getDestination() throws IllegalStateException
    {
        checkClosed();
        return _destination;
    }


    public SessionImpl getSession() throws IllegalStateException
    {
        checkClosed();
        return _session;
    }

    public boolean getNoLocal() throws IllegalStateException
    {
        checkClosed();
        return _noLocal;
    }

    public void start()
    {
        _receiver.setCredit(UnsignedInteger.valueOf(getMaxPrefetch()), true);
    }

    public Queue getQueue() throws JMSException
    {
        return (Queue) getDestination();
    }

    public Topic getTopic() throws JMSException
    {
        return (Topic) getDestination();
    }

    void setQueueConsumer(final boolean queueConsumer)
    {
        _isQueueConsumer = queueConsumer;
    }

    void setTopicSubscriber(final boolean topicSubscriber)
    {
        _isTopicSubscriber = topicSubscriber;
    }

    String getLinkName()
    {
        return _linkName;
    }

    boolean isDurable()
    {
        return _durable;
    }

    void doRecover()
    {
        _replaymessages.clear();
        if(!_recoverReplayMessages.isEmpty())
        {
            _replaymessages.addAll(_recoverReplayMessages);
            for(Message msg : _replaymessages)
            {
                _session.messageArrived(this);
            }
        }
    }

    public int getMaxPrefetch()
    {
        return _maxPrefetch;
    }

    public void setMaxPrefetch(final int maxPrefetch)
    {
        _maxPrefetch = maxPrefetch;
    }
}
