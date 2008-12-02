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
package org.apache.qpid.server.subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.common.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.filter.FilterManagerFactory;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;

/**
 * Encapsulation of a supscription to a queue. <p/> Ties together the protocol session of a subscriber, the consumer tag
 * that was given out by the broker and the channel id. <p/>
 */
public abstract class SubscriptionImpl implements Subscription, FlowCreditManager.FlowCreditManagerListener
{

    private StateListener _stateListener = new StateListener()
                                            {

                                                public void stateChange(Subscription sub, State oldState, State newState)
                                                {

                                                }
                                            };


    private final AtomicReference<State> _state = new AtomicReference<State>(State.ACTIVE);
    private final AtomicReference<QueueEntry> _queueContext = new AtomicReference<QueueEntry>(null);
    private final ClientDeliveryMethod _deliveryMethod;
    private final RecordDeliveryMethod _recordMethod;
    
    private QueueEntry.SubscriptionAcquiredState _owningState = new QueueEntry.SubscriptionAcquiredState(this);
    private final Lock _stateChangeLock;

    static final class BrowserSubscription extends SubscriptionImpl
    {
        public BrowserSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                                   AMQShortString consumerTag, FieldTable filters,
                                   boolean noLocal, FlowCreditManager creditManager,
                                   ClientDeliveryMethod deliveryMethod,
                                   RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return true;
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         * @param msg   The message to send
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry msg) throws AMQException
        {
            // We don't decrement the reference here as we don't want to consume the message
            // but we do want to send it to the client.

            synchronized (getChannel())
            {
                long deliveryTag = getChannel().getNextDeliveryTag();
                sendToClient(msg, deliveryTag);
            }

        }

        @Override
        public boolean wouldSuspend(QueueEntry msg)
        {
            return false;
        }

    }

    public static class NoAckSubscription extends SubscriptionImpl
    {
        public NoAckSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                                 AMQShortString consumerTag, FieldTable filters,
                                 boolean noLocal, FlowCreditManager creditManager,
                                   ClientDeliveryMethod deliveryMethod,
                                   RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return false;
        }

        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         * @param entry   The message to send
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry entry) throws AMQException
        {

            StoreContext storeContext = getChannel().getStoreContext();
            try
            { // if we do not need to wait for client acknowledgements
                // we can decrement the reference count immediately.

                // By doing this _before_ the send we ensure that it
                // doesn't get sent if it can't be dequeued, preventing
                // duplicate delivery on recovery.

                // The send may of course still fail, in which case, as
                // the message is unacked, it will be lost.
                entry.dequeue(storeContext);


                synchronized (getChannel())
                {
                    long deliveryTag = getChannel().getNextDeliveryTag();

                    sendToClient(entry, deliveryTag);

                }
                entry.dispose(storeContext);
            }
            finally
            {
                //Only set delivered if it actually was writen successfully..
                // using a try->finally would set it even if an error occured.
                // Is this what we want?

                entry.setDeliveredToSubscription();
            }
        }

        @Override
        public boolean wouldSuspend(QueueEntry msg)
        {
            return false;
        }

    }

    static final class AckSubscription extends SubscriptionImpl
    {
        public AckSubscription(AMQChannel channel, AMQProtocolSession protocolSession,
                               AMQShortString consumerTag, FieldTable filters,
                               boolean noLocal, FlowCreditManager creditManager,
                                   ClientDeliveryMethod deliveryMethod,
                                   RecordDeliveryMethod recordMethod)
            throws AMQException
        {
            super(channel, protocolSession, consumerTag, filters, noLocal, creditManager, deliveryMethod, recordMethod);
        }


        public boolean isBrowser()
        {
            return false;
        }


        /**
         * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
         * thread safe.
         *
         * @param entry   The message to send
         * @throws AMQException
         */
        @Override
        public void send(QueueEntry entry) throws AMQException
        {

            try
            { // if we do not need to wait for client acknowledgements
                // we can decrement the reference count immediately.

                // By doing this _before_ the send we ensure that it
                // doesn't get sent if it can't be dequeued, preventing
                // duplicate delivery on recovery.

                // The send may of course still fail, in which case, as
                // the message is unacked, it will be lost.

                synchronized (getChannel())
                {
                    long deliveryTag = getChannel().getNextDeliveryTag();


                    recordMessageDelivery(entry, deliveryTag);
                    sendToClient(entry, deliveryTag);


                }
            }
            finally
            {
                //Only set delivered if it actually was writen successfully..
                // using a try->finally would set it even if an error occured.
                // Is this what we want?

                entry.setDeliveredToSubscription();
            }
        }


    }


    private static final Logger _logger = Logger.getLogger(SubscriptionImpl.class);

    private final AMQChannel _channel;

    private final AMQShortString _consumerTag;


    private final boolean _noLocal;

    private final FlowCreditManager _creditManager;

    private FilterManager _filters;

    private final Boolean _autoClose;


    private static final String CLIENT_PROPERTIES_INSTANCE = ClientProperties.instance.toString();

    private AMQQueue _queue;
    private final AtomicBoolean _deleted = new AtomicBoolean(false);



    
    public SubscriptionImpl(AMQChannel channel , AMQProtocolSession protocolSession,
                            AMQShortString consumerTag, FieldTable arguments,
                            boolean noLocal, FlowCreditManager creditManager,
                            ClientDeliveryMethod deliveryMethod,
                            RecordDeliveryMethod recordMethod)
            throws AMQException
    {

        _channel = channel;
        _consumerTag = consumerTag;

        _creditManager = creditManager;
        creditManager.addStateListener(this);

        _noLocal = noLocal;


        _filters = FilterManagerFactory.createManager(arguments);

        _deliveryMethod = deliveryMethod;
        _recordMethod = recordMethod;


        _stateChangeLock = new ReentrantLock();


        if (arguments != null)
        {
            Object autoClose = arguments.get(AMQPFilterTypes.AUTO_CLOSE.getValue());
            if (autoClose != null)
            {
                _autoClose = (Boolean) autoClose;
            }
            else
            {
                _autoClose = false;
            }
        }
        else
        {
            _autoClose = false;
        }


    }



    public synchronized void setQueue(AMQQueue queue)
    {
        if(getQueue() != null)
        {
            throw new IllegalStateException("Attempt to set queue for subscription " + this + " to " + queue + "when already set to " + getQueue());
        }
        _queue = queue;
    }

    public String toString()
    {
        String subscriber = "[channel=" + _channel +
                            ", consumerTag=" + _consumerTag +
                            ", session=" + getProtocolSession().getKey()  ;

        return subscriber + "]";
    }

    /**
     * This method can be called by each of the publisher threads. As a result all changes to the channel object must be
     * thread safe.
     *
     * @param msg   The message to send
     * @throws AMQException
     */
    abstract public void send(QueueEntry msg) throws AMQException;


    public boolean isSuspended()
    {
        return !isActive() || _channel.isSuspended() || _deleted.get();
    }

    /**
     * Callback indicating that a queue has been deleted.
     *
     * @param queue The queue to delete
     */
    public void queueDeleted(AMQQueue queue)
    {
        _deleted.set(true);
//        _channel.queueDeleted(queue);
    }

    public boolean filtersMessages()
    {
        return _filters != null || _noLocal;
    }

    public boolean hasInterest(QueueEntry entry)
    {
        //check that the message hasn't been rejected
        if (entry.isRejectedBy(this))
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Subscription:" + debugIdentity() + " rejected message:" + entry.debugIdentity());
            }
//            return false;
        }



        //todo - client id should be recoreded and this test removed but handled below
        if (_noLocal)
        {
            final Object publisherId = entry.getMessage().getPublisherClientInstance();

            // We don't want local messages so check to see if message is one we sent
            Object localInstance;

            if (publisherId != null && (getProtocolSession().getClientProperties() != null) &&
                (localInstance = getProtocolSession().getClientProperties().getObject(CLIENT_PROPERTIES_INSTANCE)) != null)
            {
                if(publisherId.equals(localInstance))
                {
                    return false;
                }
            }
            else
            {

                localInstance = getProtocolSession().getClientIdentifier();
                //todo - client id should be recoreded and this test removed but handled here


                if (localInstance != null && localInstance.equals(entry.getMessage().getPublisherIdentifier()))
                {
                    return false;
                }
            }


        }


        if (_logger.isDebugEnabled())
        {
            _logger.debug("(" + debugIdentity() + ") checking filters for message (" + entry.debugIdentity());
        }
        return checkFilters(entry);

    }

    private String id = String.valueOf(System.identityHashCode(this));

    private String debugIdentity()
    {
        return id;
    }

    private boolean checkFilters(QueueEntry msg)
    {
        return (_filters == null) || _filters.allAllow(msg.getMessage());
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public FlowCreditManager getCreditManager()
    {
        return _creditManager;
    }


    public void close()
    {
        boolean closed = false;
        State state = getState();

        _stateChangeLock.lock();
        try
        {
            while(!closed && state != State.CLOSED)
            {
                closed = _state.compareAndSet(state, State.CLOSED);
                if(!closed)
                {
                    state = getState();
                }
                else
                {
                    _stateListener.stateChange(this,state, State.CLOSED);
                }
            }
            _creditManager.removeListener(this);
        }
        finally
        {
            _stateChangeLock.unlock();
        }


        if (closed)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Called close() on a closed subscription");
            }

            return;
        }

        if (_logger.isInfoEnabled())
        {
            _logger.info("Closing subscription (" + debugIdentity() + "):" + this);
        }
    }

    public boolean isClosed()
    {
        return getState() == State.CLOSED;
    }


    public boolean wouldSuspend(QueueEntry msg)
    {
        return !_creditManager.useCreditForMessage(msg.getMessage());//_channel.wouldSuspend(msg.getMessage());
    }

    public void getSendLock()
    {
        _stateChangeLock.lock();
    }

    public void releaseSendLock()
    {
        _stateChangeLock.unlock();
    }

    public void resend(final QueueEntry entry) throws AMQException
    {
        _queue.resend(entry, this);
    }

    public AMQChannel getChannel()
    {
        return _channel;
    }

    public AMQShortString getConsumerTag()
    {
        return _consumerTag;
    }

    public AMQProtocolSession getProtocolSession()
    {
        return _channel.getProtocolSession();
    }

    public AMQQueue getQueue()
    {
        return _queue;        
    }

    public void restoreCredit(final QueueEntry queueEntry)
    {
        _creditManager.addCredit(1, queueEntry.getSize());
    }


    public void creditStateChanged(boolean hasCredit)
    {
        
        if(hasCredit)
        {
            if(_state.compareAndSet(State.SUSPENDED, State.ACTIVE))
            {
                _stateListener.stateChange(this, State.SUSPENDED, State.ACTIVE);
            }
            else
            {
                // this is a hack to get round the issue of increasing bytes credit
                _stateListener.stateChange(this, State.ACTIVE, State.ACTIVE);
            }
        }
        else
        {
            if(_state.compareAndSet(State.ACTIVE, State.SUSPENDED))
            {
                _stateListener.stateChange(this, State.ACTIVE, State.SUSPENDED);
            }
        }
    }

    public State getState()
    {
        return _state.get();
    }


    public void setStateListener(final StateListener listener)
    {
        _stateListener = listener;
    }


    public QueueEntry getLastSeenEntry()
    {
        return _queueContext.get();
    }

    public boolean setLastSeenEntry(QueueEntry expected, QueueEntry newvalue)
    {
        return _queueContext.compareAndSet(expected,newvalue);
    }


    protected void sendToClient(final QueueEntry entry, final long deliveryTag)
            throws AMQException
    {
        _deliveryMethod.deliverToClient(this,entry,deliveryTag);
    }


    protected void recordMessageDelivery(final QueueEntry entry, final long deliveryTag)
    {
        _recordMethod.recordMessageDelivery(this,entry,deliveryTag);
    }


    public boolean isActive()
    {
        return getState() == State.ACTIVE;
    }

    public QueueEntry.SubscriptionAcquiredState getOwningState()
    {
        return _owningState;
    }

}
