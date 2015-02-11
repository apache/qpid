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

package org.apache.qpid.amqp_1_0.transport;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.apache.qpid.amqp_1_0.framing.OversizeFrameException;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.DistributionMode;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.messaging.Source;
import org.apache.qpid.amqp_1_0.type.messaging.Target;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.amqp_1_0.type.transaction.Coordinator;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.Attach;
import org.apache.qpid.amqp_1_0.type.transport.Begin;
import org.apache.qpid.amqp_1_0.type.transport.Detach;
import org.apache.qpid.amqp_1_0.type.transport.Disposition;
import org.apache.qpid.amqp_1_0.type.transport.End;
import org.apache.qpid.amqp_1_0.type.transport.Error;
import org.apache.qpid.amqp_1_0.type.transport.Flow;
import org.apache.qpid.amqp_1_0.type.transport.LinkError;
import org.apache.qpid.amqp_1_0.type.transport.Role;
import org.apache.qpid.amqp_1_0.type.transport.Transfer;

public class SessionEndpoint
{
    private SessionState _state = SessionState.INACTIVE;

    private final Map<String, LinkEndpoint> _linkMap = new HashMap<String, LinkEndpoint>();
    private final Map<LinkEndpoint, UnsignedInteger> _localLinkEndpoints = new HashMap<LinkEndpoint, UnsignedInteger>();
    private final Map<UnsignedInteger, LinkEndpoint> _remoteLinkEndpoints = new HashMap<UnsignedInteger, LinkEndpoint>();

    private long _timeout;


    private ConnectionEndpoint _connection;
    private long _lastAttachedTime;

    private short _receivingChannel;
    private short _sendingChannel;

    private LinkedHashMap<UnsignedInteger,Delivery> _outgoingUnsettled;
    private LinkedHashMap<UnsignedInteger,Delivery> _incomingUnsettled;

    // has to be a power of two
    private static final int DEFAULT_SESSION_BUFFER_SIZE = 1 << 11;
    private static final int BUFFER_SIZE_MASK = DEFAULT_SESSION_BUFFER_SIZE - 1;

    private SequenceNumber _nextIncomingTransferId;
    private SequenceNumber _nextOutgoingTransferId;

    private int _nextOutgoingDeliveryId;

    //private SequenceNumber _incomingLWM;
    //private SequenceNumber _outgoingLWM;

    private UnsignedInteger _outgoingSessionCredit;



    private UnsignedInteger _initialOutgoingId;

    private SessionEventListener _sessionEventListener = SessionEventListener.DEFAULT;

    private int _availableIncomingCredit;
    private int _availableOutgoingCredit;
    private UnsignedInteger _lastSentIncomingLimit;

    private final Error _sessionEndedLinkError =
            new Error(LinkError.DETACH_FORCED,
                     "Force detach the link because the session is remotely ended.");



    public SessionEndpoint(final ConnectionEndpoint connectionEndpoint)
    {
        this(connectionEndpoint, UnsignedInteger.valueOf(0));
    }

    public SessionEndpoint(final ConnectionEndpoint connectionEndpoint, Begin begin)
    {
        this(connectionEndpoint, UnsignedInteger.valueOf(0));
        _state = SessionState.BEGIN_RECVD;
        _nextIncomingTransferId = new SequenceNumber(begin.getNextOutgoingId().intValue());
    }


    public SessionEndpoint(final ConnectionEndpoint connectionEndpoint, UnsignedInteger nextOutgoingId)
    {
        _connection = connectionEndpoint;

        _initialOutgoingId = nextOutgoingId;
        _nextOutgoingTransferId = new SequenceNumber(nextOutgoingId.intValue());

        _outgoingUnsettled = new LinkedHashMap<UnsignedInteger,Delivery>(DEFAULT_SESSION_BUFFER_SIZE);
        _incomingUnsettled = new LinkedHashMap<UnsignedInteger, Delivery>(DEFAULT_SESSION_BUFFER_SIZE);
        _availableIncomingCredit = DEFAULT_SESSION_BUFFER_SIZE;
        _availableOutgoingCredit = DEFAULT_SESSION_BUFFER_SIZE;
    }


    public void setReceivingChannel(final short receivingChannel)
    {
        _receivingChannel = receivingChannel;
        switch(_state)
        {
            case INACTIVE:
                _state = SessionState.BEGIN_RECVD;
                break;
            case BEGIN_SENT:
                _state = SessionState.ACTIVE;
                break;
            case END_PIPE:
                _state = SessionState.END_SENT;
                break;
            default:
                // TODO error

        }
    }


    public void setSendingChannel(final short sendingChannel)
    {
        _sendingChannel = sendingChannel;
        switch(_state)
        {
            case INACTIVE:
                _state = SessionState.BEGIN_SENT;
                break;
            case BEGIN_RECVD:
                _state = SessionState.ACTIVE;
                break;
            default:
                // TODO error

        }
    }

    public SessionState getState()
    {
        return _state;
    }

    public void end()
    {
        end(new End());
    }

    public void end(End end)
    {
        synchronized(getLock())
        {
            switch(_state)
            {
                case BEGIN_SENT:
                    _connection.sendEnd(getSendingChannel(), end, false);
                    _state = SessionState.END_PIPE;
                    break;
                case ACTIVE:
                    detachLinks();
                    short sendChannel = getSendingChannel();
                    _connection.sendEnd(sendChannel, end, true);
                    _state = SessionState.END_SENT;
                    break;
                default:
                    sendChannel = getSendingChannel();
                    End reply = new End();
                    Error error = new Error();
                    error.setCondition(AmqpError.ILLEGAL_STATE);
                    error.setDescription("END called on Session which has not been opened");
                    reply.setError(error);
                    _connection.sendEnd(sendChannel, reply, true);
                    break;


            }
            getLock().notifyAll();
        }
    }

    public void receiveEnd(final End end)
    {
        synchronized(getLock())
        {
            switch(_state)
            {
                case END_SENT:
                    _state = SessionState.ENDED;
                    break;
                case ACTIVE:
                    detachLinks();
                    _sessionEventListener.remoteEnd(end);
                    short sendChannel = getSendingChannel();
                    _connection.sendEnd(sendChannel, new End(), true);
                    _state = SessionState.ENDED;
                    break;
                default:
                    sendChannel = getSendingChannel();
                    End reply = new End();
                    Error error = new Error();
                    error.setCondition(AmqpError.ILLEGAL_STATE);
                    error.setDescription("END called on Session which has not been opened");
                    reply.setError(error);
                    _connection.sendEnd(sendChannel, reply, true);
                    break;


            }
            getLock().notifyAll();
        }
    }

    private void detachLinks()
    {
        Collection<UnsignedInteger> handles = new ArrayList<UnsignedInteger>(_remoteLinkEndpoints.keySet());
        for(UnsignedInteger handle : handles)
        {
            Detach detach = new Detach();
            detach.setClosed(false);
            detach.setHandle(handle);
            detach.setError(_sessionEndedLinkError);
            detach(handle, detach);
        }
    }

    public boolean isSyntheticError(Error error)
    {
        return error == _sessionEndedLinkError;
    }

    public short getSendingChannel()
    {
        return _sendingChannel;
    }


    public void receiveAttach(final Attach attach)
    {
        if(_state == SessionState.ACTIVE)
        {
            UnsignedInteger handle = attach.getHandle();
            if(_remoteLinkEndpoints.containsKey(handle))
            {
                // TODO - Error - handle busy?
            }
            else
            {
                LinkEndpoint endpoint = getLinkMap().get(attach.getName());
                if(endpoint == null)
                {
                    endpoint = attach.getRole() == Role.RECEIVER
                               ? new SendingLinkEndpoint(this, attach)
                               : new ReceivingLinkEndpoint(this, attach);

                    // TODO : fix below - distinguish between local and remote owned
                    endpoint.setSource(attach.getSource());
                    endpoint.setTarget(attach.getTarget());


                }

                if(attach.getRole() == Role.SENDER)
                {
                    endpoint.setDeliveryCount(attach.getInitialDeliveryCount());
                }

                _remoteLinkEndpoints.put(handle, endpoint);

                if(!_localLinkEndpoints.containsKey(endpoint))
                {
                    UnsignedInteger localHandle = findNextAvailableHandle();
                    endpoint.setLocalHandle(localHandle);
                    _localLinkEndpoints.put(endpoint, localHandle);

                    _sessionEventListener.remoteLinkCreation(endpoint);


                }
                else
                {
                    endpoint.receiveAttach(attach);
                }
            }
        }
    }

    private void send(final FrameBody frameBody)
    {
        _connection.send(this.getSendingChannel(), frameBody);
    }


    private int send(final FrameBody frameBody, ByteBuffer payload)
    {
        return _connection.send(this.getSendingChannel(), frameBody, payload);
    }

    private UnsignedInteger findNextAvailableHandle()
    {
        int i = 0;
        do
        {
            if(!_localLinkEndpoints.containsValue(UnsignedInteger.valueOf(i)))
            {
                return UnsignedInteger.valueOf(i);
            }
        } while(++i != 0);

        // TODO
        throw new RuntimeException();
    }

    public void receiveDetach(final Detach detach)
    {
        UnsignedInteger handle = detach.getHandle();
        detach(handle, detach);
    }

    private void detach(UnsignedInteger handle, Detach detach)
    {
        if(_remoteLinkEndpoints.containsKey(handle))
        {
            LinkEndpoint endpoint = _remoteLinkEndpoints.remove(handle);

            endpoint.remoteDetached(detach);

            _localLinkEndpoints.remove(endpoint);


        }
        else
        {
            // TODO
        }
    }

    public void receiveTransfer(final Transfer transfer)
    {
        synchronized(getLock())
        {
            _nextIncomingTransferId.incr();
/*
            _availableIncomingCredit--;
*/

            UnsignedInteger handle = transfer.getHandle();



            LinkEndpoint endpoint = _remoteLinkEndpoints.get(handle);

            if(endpoint == null)
            {
                //TODO - error unknown link
                System.err.println("Unknown endpoint " + transfer);

            }

            UnsignedInteger deliveryId = transfer.getDeliveryId();
            if(deliveryId == null)
            {
                deliveryId = ((ReceivingLinkEndpoint)endpoint).getLastDeliveryId();
            }

            Delivery delivery = _incomingUnsettled.get(deliveryId);
            if(delivery == null)
            {
                delivery = new Delivery(transfer, endpoint);
                _incomingUnsettled.put(deliveryId,delivery);
                if(delivery.isSettled() || Boolean.TRUE.equals(transfer.getAborted()))
                {
/*
                    _availableIncomingCredit++;
*/
                }

                if(Boolean.TRUE.equals(transfer.getMore()))
                {
                    ((ReceivingLinkEndpoint)endpoint).setLastDeliveryId(transfer.getDeliveryId());
                }
            }
            else
            {
                if(delivery.getDeliveryId().equals(deliveryId))
                {
                    delivery.addTransfer(transfer);
                    if(delivery.isSettled())
                    {
/*
                        _availableIncomingCredit++;
*/
                    }
                    else if(Boolean.TRUE.equals(transfer.getAborted()))
                    {
/*
                        _availableIncomingCredit += delivery.getTransfers().size();
*/
                    }

                    if(!Boolean.TRUE.equals(transfer.getMore()))
                    {
                        ((ReceivingLinkEndpoint)endpoint).setLastDeliveryId(null);
                    }
                }
                else
                {
                    // TODO - error
                    System.err.println("Incorrect transfer id " + transfer);
                }
            }

            if(endpoint != null)
            {
                endpoint.receiveTransfer(transfer, delivery);
            }

            if((delivery.isComplete() && delivery.isSettled() || Boolean.TRUE.equals(transfer.getAborted())))
            {
                _incomingUnsettled.remove(deliveryId);
            }
        }
    }

    public void receiveFlow(final Flow flow)
    {

        synchronized(getLock())
        {
            UnsignedInteger handle = flow.getHandle();
            LinkEndpoint endpoint = handle == null ? null : _remoteLinkEndpoints.get(handle);

            final UnsignedInteger nextOutgoingId = flow.getNextIncomingId() == null ? _initialOutgoingId : flow.getNextIncomingId();
            int limit = (nextOutgoingId.intValue() + flow.getIncomingWindow().intValue());
            _outgoingSessionCredit = UnsignedInteger.valueOf(limit - _nextOutgoingTransferId.intValue());

            if(endpoint != null)
            {
                endpoint.receiveFlow( flow );
            }
            else
            {
                for(LinkEndpoint le : _remoteLinkEndpoints.values())
                {
                    le.flowStateChanged();
                }
            }

            getLock().notifyAll();
        }


    }

    public void receiveDisposition(final Disposition disposition)
    {
        Role dispositionRole = disposition.getRole();

        LinkedHashMap<UnsignedInteger, Delivery> unsettledTransfers;

        if(dispositionRole == Role.RECEIVER)
        {
            unsettledTransfers = _outgoingUnsettled;
        }
        else
        {
            unsettledTransfers = _incomingUnsettled;

        }

        UnsignedInteger deliveryId = disposition.getFirst();
        UnsignedInteger last = disposition.getLast();
        if(last == null)
        {
            last = deliveryId;
        }


                while(deliveryId.compareTo(last)<=0)
                {

                    Delivery delivery = unsettledTransfers.get(deliveryId);
                    if(delivery != null)
                    {
                        delivery.getLinkEndpoint().receiveDeliveryState(delivery,
                                                                   disposition.getState(),
                                                                   disposition.getSettled());
                    }
                    deliveryId = deliveryId.add(UnsignedInteger.ONE);
                }
                if(disposition.getSettled())
                {
                    checkSendFlow();
                }

    }

    private void checkSendFlow()
    {
        //TODO
    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final String targetAddr, final String sourceAddr)
    {
        return createSendingLinkEndpoint(name, targetAddr, sourceAddr, null);
    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final String targetAddr, final String sourceAddr, Map<Binary, Outcome> unsettled)
    {
        return createSendingLinkEndpoint(name, targetAddr, sourceAddr, false, unsettled);
    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final String targetAddr,
                                                         final String sourceAddr, boolean durable,
                                                         Map<Binary, Outcome> unsettled)
    {

        Source source = new Source();
        source.setAddress(sourceAddr);
        Target target = new Target();
        target.setAddress(targetAddr);
        if(durable)
        {
            target.setDurable(TerminusDurability.UNSETTLED_STATE);
            target.setExpiryPolicy(TerminusExpiryPolicy.NEVER);
        }

        return createSendingLinkEndpoint(name, source, target, unsettled);

    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final Source source, final org.apache.qpid.amqp_1_0.type.Target target)
    {
        return createSendingLinkEndpoint(name, source, target, null);
    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final Source source, final org.apache.qpid.amqp_1_0.type.Target target, Map<Binary, Outcome> unsettled)
    {
        return createSendingLinkEndpoint(name, source, target, unsettled, null);
    }

    public SendingLinkEndpoint createSendingLinkEndpoint(final String name, final Source source,
                                                         final org.apache.qpid.amqp_1_0.type.Target target,
                                                         Map<Binary, Outcome> unsettled,
                                                         DeliveryStateHandler deliveryStateHandler)
    {
        SendingLinkEndpoint endpoint = new SendingLinkEndpoint(this, name, unsettled, deliveryStateHandler);
        endpoint.setSource(source);
        endpoint.setTarget(target);
        UnsignedInteger handle = findNextAvailableHandle();
        _localLinkEndpoints.put(endpoint, handle);
        endpoint.setLocalHandle(handle);
        getLinkMap().put(name, endpoint);

        return endpoint;
    }

    public void sendAttach(Attach attach)
    {
        send(attach);
    }

    public void sendTransfer(final Transfer xfr, SendingLinkEndpoint endpoint, boolean newDelivery)
    {
        _nextOutgoingTransferId.incr();
        UnsignedInteger deliveryId;
        if(newDelivery)
        {
            deliveryId = UnsignedInteger.valueOf(_nextOutgoingDeliveryId++);
            endpoint.setLastDeliveryId(deliveryId);
        }
        else
        {
            deliveryId = endpoint.getLastDeliveryId();
        }
        xfr.setDeliveryId(deliveryId);

        if(!Boolean.TRUE.equals(xfr.getSettled()))
        {
            Delivery delivery;
            if((delivery = _outgoingUnsettled.get(deliveryId))== null)
            {
                delivery = new Delivery(xfr, endpoint);
                _outgoingUnsettled.put(deliveryId, delivery);

            }
            else
            {
                delivery.addTransfer(xfr);
            }
            _outgoingSessionCredit = _outgoingSessionCredit.subtract(UnsignedInteger.ONE);
            endpoint.addUnsettled(delivery);

        }

        try
        {
            ByteBuffer payload = xfr.getPayload();
            int payloadSent = send(xfr, payload);

            if(payload != null && payloadSent < payload.remaining() && payloadSent >= 0)
            {
                payload = payload.duplicate();
                payload.position(payload.position()+payloadSent);

                Transfer secondTransfer = new Transfer();

                secondTransfer.setDeliveryTag(xfr.getDeliveryTag());
                secondTransfer.setHandle(xfr.getHandle());
                secondTransfer.setSettled(xfr.getSettled());
                secondTransfer.setState(xfr.getState());
                secondTransfer.setMessageFormat(xfr.getMessageFormat());
                secondTransfer.setPayload(payload);

                sendTransfer(secondTransfer, endpoint, false);

            }
        }
        catch(OversizeFrameException e)
        {
            e.printStackTrace();
        }

    }

    public Object getLock()
    {
        return _connection.getLock();
    }


    public long getSyncTimeout()
    {
        return _connection.getSyncTimeout();
    }

    public void waitUntil(Predicate predicate) throws TimeoutException, InterruptedException
    {
        _connection.waitUntil(predicate);
    }

    public void waitUntil(Predicate predicate, long timeout) throws TimeoutException, InterruptedException
    {
        _connection.waitUntil(predicate, timeout);
    }


    public ReceivingLinkEndpoint createReceivingLinkEndpoint(final String name,
                                                             String targetAddr,
                                                             String sourceAddr,
                                                             UnsignedInteger initialCredit,
                                                             final DistributionMode distributionMode)
    {
        Source source = new Source();
        source.setAddress(sourceAddr);
        source.setDistributionMode(distributionMode);
        Target target = new Target();
        target.setAddress(targetAddr);

        return createReceivingLinkEndpoint(name, target, source, initialCredit);
    }

    public ReceivingLinkEndpoint createReceivingLinkEndpoint(final String name,
                                                             Target target,
                                                             Source source,
                                                             UnsignedInteger initialCredit)
    {
        ReceivingLinkEndpoint endpoint = new ReceivingLinkEndpoint(this, name);
        endpoint.setLinkCredit(initialCredit);
        endpoint.setSource(source);
        endpoint.setTarget(target);
        UnsignedInteger handle = findNextAvailableHandle();
        _localLinkEndpoints.put(endpoint, handle);
        endpoint.setLocalHandle(handle);
        getLinkMap().put(name, endpoint);

        return endpoint;

    }

    public void updateDisposition(final Role role,
                                  final UnsignedInteger first,
                                  final UnsignedInteger last,
                                  final DeliveryState state,
                                  final boolean settled)
    {


        Disposition disposition = new Disposition();
        disposition.setRole(role);
        disposition.setFirst(first);
        disposition.setLast(last);
        disposition.setSettled(settled);

        disposition.setState(state);


        if(settled)
        {
            if(role == Role.RECEIVER)
            {
                SequenceNumber pos = new SequenceNumber(first.intValue());
                SequenceNumber end = new SequenceNumber(last.intValue());
                while(pos.compareTo(end)<=0)
                {
                    Delivery d = _incomingUnsettled.remove(new UnsignedInteger(pos.intValue()));

/*
                    _availableIncomingCredit += d.getTransfers().size();
*/

                    pos.incr();
                }
            }
        }

        send(disposition);
        checkSendFlow();
    }

    public void settle(Role role, final UnsignedInteger deliveryId)
    {
        if(role == Role.RECEIVER)
        {
            Delivery d = _incomingUnsettled.remove(deliveryId);
            if(d != null)
            {
/*
                _availableIncomingCredit += d.getTransfers().size();
*/
            }
        }
        else
        {
            Delivery d = _outgoingUnsettled.remove(deliveryId);
/*            if(d != null)
            {
                _availableOutgoingCredit += d.getTransfers().size();

            }*/
        }

    }

    public void sendFlow()
    {
        sendFlow(new Flow());
    }
    public void sendFlow(final Flow flow)
    {
        if(_nextIncomingTransferId != null)
        {
            final int nextIncomingId = _nextIncomingTransferId.intValue();
            flow.setNextIncomingId(UnsignedInteger.valueOf(nextIncomingId));
            _lastSentIncomingLimit = UnsignedInteger.valueOf(nextIncomingId + _availableIncomingCredit);
        }
        flow.setIncomingWindow(UnsignedInteger.valueOf(_availableIncomingCredit));

        flow.setNextOutgoingId(UnsignedInteger.valueOf(_nextOutgoingTransferId.intValue()));
        flow.setOutgoingWindow(UnsignedInteger.valueOf(_availableOutgoingCredit));
        send(flow);
    }

    public void sendFlowConditional()
    {
        if(_nextIncomingTransferId != null)
        {
            UnsignedInteger clientsCredit =
                    _lastSentIncomingLimit.subtract(UnsignedInteger.valueOf(_nextIncomingTransferId.intValue()));
            int i = UnsignedInteger.valueOf(_availableIncomingCredit).subtract(clientsCredit).compareTo(clientsCredit);
            if (i >= 0)
            {
                sendFlow();
            }
        }

    }

    public void sendDetach(Detach detach)
    {
        send(detach);

    }

    void doEnd(End end)
    {
    }

    public void setNextIncomingId(final UnsignedInteger nextIncomingId)
    {
        _nextIncomingTransferId = new SequenceNumber(nextIncomingId.intValue());

    }

    public void setOutgoingSessionCredit(final UnsignedInteger outgoingSessionCredit)
    {
        _outgoingSessionCredit = outgoingSessionCredit;
    }

    public UnsignedInteger getNextOutgoingId()
    {
        return UnsignedInteger.valueOf(_nextOutgoingTransferId.intValue());
    }

    public UnsignedInteger getOutgoingWindowSize()
    {
        return UnsignedInteger.valueOf(_availableOutgoingCredit);
    }

    public boolean hasCreditToSend()
    {
        boolean b = _outgoingSessionCredit != null && _outgoingSessionCredit.intValue() > 0;
        boolean b1 = getOutgoingWindowSize() != null && getOutgoingWindowSize().compareTo(UnsignedInteger.ZERO) > 0;
        return b && b1;
    }

    public UnsignedInteger getIncomingWindowSize()
    {
        return UnsignedInteger.valueOf(_availableIncomingCredit);
    }

    public SessionEventListener getSessionEventListener()
    {
        return _sessionEventListener;
    }

    public void setSessionEventListener(final SessionEventListener sessionEventListener)
    {
        _sessionEventListener = sessionEventListener;
    }

    public ConnectionEndpoint getConnection()
    {
        return _connection;
    }

    public SendingLinkEndpoint createTransactionController(String name, TxnCapability... capabilities)
    {
        Coordinator coordinator = new Coordinator();
        coordinator.setCapabilities(capabilities);

        Source src = new Source();

        return createSendingLinkEndpoint(name, src, coordinator);
    }

    Map<String, LinkEndpoint> getLinkMap()
    {
        return _linkMap;
    }

    public Collection<LinkEndpoint> getLocalLinkEndpoints()
    {
        return new ArrayList<>(_localLinkEndpoints.keySet());
    }

    public boolean isEnded()
    {
        return _state == SessionState.ENDED || _connection.isClosed();
    }

    public boolean isActive()
    {
        return _state == SessionState.ACTIVE;
    }
}
