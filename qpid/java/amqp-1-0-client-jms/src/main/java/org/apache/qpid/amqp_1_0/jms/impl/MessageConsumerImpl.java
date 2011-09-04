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

import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.client.Receiver;
import org.apache.qpid.amqp_1_0.jms.MessageConsumer;
import org.apache.qpid.amqp_1_0.jms.QueueReceiver;
import org.apache.qpid.amqp_1_0.jms.Queue;
import org.apache.qpid.amqp_1_0.jms.Topic;
import org.apache.qpid.amqp_1_0.jms.TopicSubscriber;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;

import javax.jms.*;
import javax.jms.IllegalStateException;

public class MessageConsumerImpl implements MessageConsumer, QueueReceiver, TopicSubscriber
{
    private String _selector;
    private boolean _noLocal;
    private DestinationImpl _destination;
    private SessionImpl _session;
    private Receiver _receiver;
    private Binary _lastUnackedMessage;
    MessageListener _messageListener;

    private boolean _closed = false;

    MessageConsumerImpl(final Destination destination,
                        final SessionImpl session,
                        final String selector,
                        final boolean noLocal) throws JMSException
    {
        _selector = selector;
        _noLocal = noLocal;
        if(destination instanceof DestinationImpl)
        {
            _destination = (DestinationImpl) destination;
        }
        else if(destination != null)
        {
            // TODO - throw appropriate exception
        }
        _session = session;

        _receiver = createClientReceiver();

    }

    protected Receiver createClientReceiver()
    {
        return _session.getClientSession().createReceiver(_destination.getAddress());
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
        _session.messageListenerSet( this );
        _receiver.setMessageArrivalListener(new Receiver.MessageArrivalListener()
        {

            public void messageArrived(final Receiver receiver)
            {
                _session.messageArrived(MessageConsumerImpl.this);
            }
        });
    }

    public MessageImpl receive() throws JMSException
    {
        checkClosed();
        return receiveImpl(-1L);
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

    private MessageImpl receiveImpl(long timeout) throws IllegalStateException
    {
        org.apache.qpid.amqp_1_0.client.Message msg = receive0(timeout);
        if(msg != null)
        {
            preReceiveAction(msg);
        }
        return createJMSMessage(msg);
    }

    Message receive0(final long timeout)
    {
        return _receiver.receive(timeout);
    }


    void acknowledge(final org.apache.qpid.amqp_1_0.client.Message msg)
    {
        _receiver.acknowledge(msg.getDeliveryTag());
    }

    MessageImpl createJMSMessage(final org.apache.qpid.amqp_1_0.client.Message msg)
    {
        if(msg != null)
        {
            MessageFactory factory = _session.getMessageFactory();
            return factory.createMessage(_destination, msg);
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

            _receiver.close();

        }
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

    void preReceiveAction(final org.apache.qpid.amqp_1_0.client.Message msg) throws IllegalStateException
    {
        final int acknowledgeMode = _session.getAcknowledgeMode();

        if(acknowledgeMode == Session.AUTO_ACKNOWLEDGE || acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE)
        {
            acknowledge(msg);
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
            _receiver.acknowledgeAll(_lastUnackedMessage);
            _lastUnackedMessage = null;
        }
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
        _receiver.setCredit(UnsignedInteger.valueOf(100), true);
    }

    public Queue getQueue() throws JMSException
    {
        return (Queue) getDestination();
    }

    public Topic getTopic() throws JMSException
    {
        return (Topic) getDestination();
    }
}
