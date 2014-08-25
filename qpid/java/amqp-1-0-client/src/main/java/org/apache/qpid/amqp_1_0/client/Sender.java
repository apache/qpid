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
package org.apache.qpid.amqp_1_0.client;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.qpid.amqp_1_0.codec.DescribedTypeConstructor;
import org.apache.qpid.amqp_1_0.messaging.SectionEncoder;
import org.apache.qpid.amqp_1_0.transport.DeliveryStateHandler;
import org.apache.qpid.amqp_1_0.transport.LinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.Predicate;
import org.apache.qpid.amqp_1_0.transport.SendingLinkEndpoint;
import org.apache.qpid.amqp_1_0.transport.SendingLinkListener;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Source;
import org.apache.qpid.amqp_1_0.type.Target;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.Accepted;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionalState;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;

public class Sender implements DeliveryStateHandler
{
    private static final long UNSETTLED_MESSAGE_TIMEOUT_MULTIPLIER = 1000l;
    private static final long DEFAULT_CREDIT_TIMEOUT = 30000l;

    private SendingLinkEndpoint _endpoint;
    private int _id;
    private Session _session;
    private int _windowSize;
    private Map<Binary, OutcomeAction> _outcomeActions = Collections.synchronizedMap(new HashMap<Binary, OutcomeAction>());
    private boolean _closed;
    private Error _error;
    private Runnable _remoteErrorTask;
    private Outcome _defaultOutcome;

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, targetAddr, sourceAddr, false);
    }

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr,
                  boolean synchronous)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, targetAddr, sourceAddr, synchronous ? 1 : 0);
    }

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr,
                  int window) throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, targetAddr, sourceAddr, window, AcknowledgeMode.ALO);
    }


    public Sender(final Session session, final String linkName, final org.apache.qpid.amqp_1_0.type.messaging.Target target, final org.apache.qpid.amqp_1_0.type.messaging.Source source,
                  int window) throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, target, source, window, AcknowledgeMode.ALO);
    }

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr,
                  int window, AcknowledgeMode mode)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, targetAddr, sourceAddr, window, mode, null);
    }

    public Sender(final Session session, final String linkName, final org.apache.qpid.amqp_1_0.type.messaging.Target target, final org.apache.qpid.amqp_1_0.type.messaging.Source source,
                  int window, AcknowledgeMode mode)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, target, source, window, mode, null);
    }

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr,
                  int window, AcknowledgeMode mode, Map<Binary, Outcome> unsettled)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, targetAddr, sourceAddr, window, mode, false, unsettled);
    }

    public Sender(final Session session, final String linkName, final String targetAddr, final String sourceAddr,
                  int window, AcknowledgeMode mode, boolean isDurable, Map<Binary, Outcome> unsettled)
            throws SenderCreationException, ConnectionClosedException
    {
        this(session, linkName, createTarget(targetAddr, isDurable), createSource(sourceAddr), window, mode, unsettled);
    }

    protected void configureSource(org.apache.qpid.amqp_1_0.type.messaging.Source source)
    {

    }

    protected void configureTarget(org.apache.qpid.amqp_1_0.type.messaging.Target target)
    {

    }

    private static org.apache.qpid.amqp_1_0.type.messaging.Source createSource(final String sourceAddr)
    {
        org.apache.qpid.amqp_1_0.type.messaging.Source source = new org.apache.qpid.amqp_1_0.type.messaging.Source();
        source.setAddress(sourceAddr);
        return source;
    }

    private static org.apache.qpid.amqp_1_0.type.messaging.Target createTarget(final String targetAddr, final boolean isDurable)
    {
        org.apache.qpid.amqp_1_0.type.messaging.Target target = new org.apache.qpid.amqp_1_0.type.messaging.Target();
        target.setAddress(targetAddr);
        if(isDurable)
        {
            target.setDurable(TerminusDurability.UNSETTLED_STATE);
            target.setExpiryPolicy(TerminusExpiryPolicy.NEVER);
        }
        return target;
    }

    public Sender(final Session session, final String linkName, final org.apache.qpid.amqp_1_0.type.messaging.Target target, final org.apache.qpid.amqp_1_0.type.messaging.Source source,
                  int window, AcknowledgeMode mode, Map<Binary, Outcome> unsettled)
            throws SenderCreationException, ConnectionClosedException
    {

        _session = session;
        _windowSize = window;
        session.getConnection().checkNotClosed();
        configureSource(source);
        configureTarget(target);
        _endpoint = session.createSendingLinkEndpoint(linkName, target, source, mode, unsettled, this);

        synchronized(_endpoint.getLock())
        {
            try
            {
                _endpoint.waitUntil(new Predicate()
                                    {
                                        @Override
                                        public boolean isSatisfied()
                                        {
                                            return _endpoint.isAttached() || _endpoint.isDetached();
                                        }
                                    });
            }
            catch (TimeoutException e)
            {
                throw new SenderCreationException(e);
            }
            catch (InterruptedException e)
            {
                throw new SenderCreationException(e);
            }

            if (session.getEndpoint().isEnded())
            {
                throw new SenderCreationException("Session is closed while creating link, target: " + target.getAddress());
            }
            if(_endpoint.getTarget()== null)
            {
                throw new SenderCreationException("Peer did not create remote endpoint for link, target: " + target.getAddress());
            }
        }

        _endpoint.setLinkEventListener(new SendingLinkListener.DefaultLinkEventListener()
        {

            @Override
            public void remoteDetached(final LinkEndpoint endpoint, final Detach detach)
            {
                _error = detach.getError();
                if(_error != null)
                {
                    remoteError();
                }
                super.remoteDetached(endpoint, detach);
            }
        });

        _defaultOutcome = source.getDefaultOutcome();
        if(_defaultOutcome == null)
        {
            if(source.getOutcomes() == null || source.getOutcomes().length == 0)
            {
                _defaultOutcome = new Accepted();
            }
            else if(source.getOutcomes().length == 1)
            {

                final AMQPDescribedTypeRegistry describedTypeRegistry = _endpoint.getSession()
                        .getConnection()
                        .getDescribedTypeRegistry();

                DescribedTypeConstructor constructor = describedTypeRegistry
                        .getConstructor(source.getOutcomes()[0]);
                if(constructor != null)
                {
                    Object impliedOutcome = constructor.construct(Collections.EMPTY_LIST);
                    if(impliedOutcome instanceof Outcome)
                    {
                        _defaultOutcome = (Outcome) impliedOutcome;
                    }
                }

            }
        }
    }

    public Source getSource()
    {
        return _endpoint.getSource();
    }

    public Target getTarget()
    {
        return _endpoint.getTarget();
    }

    public void send(Message message) throws LinkDetachedException, TimeoutException
    {
        send(message, null, null);
    }

    public void send(Message message, final OutcomeAction action) throws LinkDetachedException, TimeoutException
    {
        send(message, null, action);
    }

    public void send(Message message, final Transaction txn) throws LinkDetachedException, TimeoutException
    {
        send(message, txn, null);
    }

    public void send(Message message, final Transaction txn, OutcomeAction action) throws LinkDetachedException, TimeoutException
    {

        List<Section> sections = message.getPayload();

        Transfer xfr = new Transfer();

        if(sections != null && !sections.isEmpty())
        {
            SectionEncoder encoder = _session.getSectionEncoder();
            encoder.reset();

            int sectionNumber = 0;
            for(Section section : sections)
            {
                encoder.encodeObject(section);
            }


            Binary encoding = encoder.getEncoding();
            ByteBuffer payload = encoding.asByteBuffer();
            xfr.setPayload(payload);
        }
        if(message.getDeliveryTag() == null)
        {
            message.setDeliveryTag(new Binary(String.valueOf(_id++).getBytes()));
        }
        if(message.isResume())
        {
            xfr.setResume(Boolean.TRUE);
        }
        if(message.getDeliveryState() != null)
        {
            xfr.setState(message.getDeliveryState());
        }

        xfr.setDeliveryTag(message.getDeliveryTag());
        //xfr.setSettled(_windowSize ==0);
        if(txn != null)
        {
            xfr.setSettled(false);
            TransactionalState deliveryState = new TransactionalState();
            deliveryState.setTxnId(txn.getTxnId());
            xfr.setState(deliveryState);
        }
        else
        {
            xfr.setSettled(message.getSettled() || _endpoint.getSendingSettlementMode() == SenderSettleMode.SETTLED);
        }
        final Object lock = _endpoint.getLock();

        synchronized(lock)
        {

            try
            {
                _endpoint.waitUntil(new Predicate()
                                    {
                                        @Override
                                        public boolean isSatisfied()
                                        {
                                            return _endpoint.hasCreditToSend() || _endpoint.isDetached();
                                        }
                                    }, getCreditTimeout());
            }
            catch (InterruptedException e)
            {
                throw new TimeoutException("Interrupted while waiting for credit");
            }

            if(_endpoint.isDetached())
            {
                throw new LinkDetachedException(_error);
            }
            if(action != null)
            {
                _outcomeActions.put(message.getDeliveryTag(), action);
            }
            _endpoint.transfer(xfr);
        }

        if(_windowSize != 0)
        {
            try
            {
                _endpoint.waitUntil(new Predicate()
                                    {
                                        @Override
                                        public boolean isSatisfied()
                                        {
                                            return _endpoint.getUnsettledCount() < _windowSize;
                                        }
                                    }, getUnsettledTimeout());
            }
            catch (InterruptedException e)
            {
                throw new TimeoutException("Interrupted while waiting for the window to expand to allow sending");
            }

        }


    }

    private long getCreditTimeout()
    {
        return _endpoint.getSyncTimeout() < DEFAULT_CREDIT_TIMEOUT ? DEFAULT_CREDIT_TIMEOUT : _endpoint.getSyncTimeout();
    }

    public void close() throws SenderClosingException
    {
        boolean unsettledDeliveries = false;

        if(_windowSize != 0)
        {
            long timeout = getUnsettledTimeout();

            try
            {
                _endpoint.waitUntil(new Predicate()
                {
                    @Override
                    public boolean isSatisfied()
                    {
                        return _endpoint.getUnsettledCount() == 0;
                    }
                }, timeout);
            }
            catch (InterruptedException e)
            {
                unsettledDeliveries = true;
            }
            catch (TimeoutException e)
            {
                unsettledDeliveries = true;
            }

        }
        _session.removeSender(this);
        _endpoint.setSource(null);
        _endpoint.close();
        _closed = true;

        try
        {
            _endpoint.waitUntil(new Predicate()
            {
                @Override
                public boolean isSatisfied()
                {
                    return _endpoint.isDetached();
                }
            });
        }
        catch (TimeoutException e)
        {
            throw new SenderClosingException("Timed out attempting to detach link", e);
        }
        catch (InterruptedException e)
        {
            throw new SenderClosingException("Interrupted while attempting to detach link", e);
        }
        if(unsettledDeliveries && _endpoint.getUnsettledCount() > 0)
        {
            throw new SenderClosingException("Some messages may not have been received by the recipient");
        }
    }

    private long getUnsettledTimeout()
    {
        long timeout = _endpoint.getSyncTimeout();

        // give a generous timeout where there are unsettled messages
        if(timeout < _endpoint.getUnsettledCount() * UNSETTLED_MESSAGE_TIMEOUT_MULTIPLIER)
        {
            timeout = _endpoint.getUnsettledCount() * UNSETTLED_MESSAGE_TIMEOUT_MULTIPLIER;
        }
        return timeout;
    }

    public boolean isClosed()
    {
        return _closed;
    }

    public void handle(Binary deliveryTag, DeliveryState state, Boolean settled)
    {
        OutcomeAction action;
        if(state instanceof Outcome)
        {
            if((action = _outcomeActions.remove(deliveryTag)) != null)
            {

                final Outcome outcome = (Outcome) state;
                action.onOutcome(deliveryTag, (outcome == null && settled) ? _defaultOutcome : outcome);
            }
            if(!Boolean.TRUE.equals(settled))
            {
                _endpoint.updateDisposition(deliveryTag, state, true);
            }
        }
        else if(state instanceof TransactionalState)
        {
            if((action = _outcomeActions.remove(deliveryTag)) != null)
            {
                final Outcome outcome = ((TransactionalState) state).getOutcome();
                action.onOutcome(deliveryTag, outcome == null ? _defaultOutcome : outcome);
            }

        }
        else if(state == null && settled && (action = _outcomeActions.remove(deliveryTag)) != null)
        {
            action.onOutcome(deliveryTag, _defaultOutcome);
        }
    }

    public SendingLinkEndpoint getEndpoint()
    {
        return _endpoint;
    }

    public Map<Binary, DeliveryState> getRemoteUnsettled()
    {
        return _endpoint.getInitialUnsettledMap();
    }

    public Session getSession()
    {
        return _session;
    }


    private void remoteError()
    {
        if(_remoteErrorTask != null)
        {
            Thread thread = new Thread(_remoteErrorTask);
            thread.start();
        }
    }


    public void setRemoteErrorListener(Runnable listener)
    {
        _remoteErrorTask = listener;
    }

    public Error getError()
    {
        return _error;
    }

    public class SenderCreationException extends Exception
    {
        public SenderCreationException(Throwable e)
        {
            super(e);
        }

        public SenderCreationException(String e)
        {
            super(e);

        }
    }

    public class SenderClosingException extends Exception
    {
        public SenderClosingException(final String message, final Throwable cause)
        {
            super(message, cause);
        }

        public SenderClosingException(Throwable e)
        {
            super(e);
        }

        public SenderClosingException(final String message)
        {
            super(message);
        }
    }

    public static interface OutcomeAction
    {
        public void onOutcome(Binary deliveryTag, Outcome outcome);
    }
}
