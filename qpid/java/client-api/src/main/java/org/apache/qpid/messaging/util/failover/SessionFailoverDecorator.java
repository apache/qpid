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
package org.apache.qpid.messaging.util.failover;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionEvent;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SenderInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.apache.qpid.messaging.util.AbstractSessionDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionFailoverDecorator extends AbstractSessionDecorator implements ConnectionEventListener
{
    private static Logger _logger = LoggerFactory.getLogger(SessionFailoverDecorator.class);

    public enum SessionState {OPENED, CLOSED, FAILOVER_IN_PROGRESS}

    private SessionState _state = SessionState.OPENED;
    private long _failoverTimeout = Long.getLong("qpid.failover-timeout", 1000);
    private SessionException _lastException;
    private long _connSerialNumber = 0;
    
    public SessionFailoverDecorator(ConnectionInternal conn, SessionInternal delegate)
    {
        super(conn,delegate);
        synchronized(_connectionLock)
        {
            _connSerialNumber = conn.getSerialNumber();
        }
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            if (_state == SessionState.CLOSED)
            {
                throw new MessagingException("Session is already closed");
            }
            _state = SessionState.CLOSED;
            super.close();
        }
    }

    @Override
    public void commit() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.commit();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            commit();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void rollback() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.rollback();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            rollback();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void acknowledge(boolean sync) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.acknowledge(sync);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            acknowledge(sync);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void acknowledge(Message message, boolean sync)
    throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.acknowledge(message,sync);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            acknowledge(message,sync);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void reject(Message message) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.reject(message);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            reject(message);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void release(Message message) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.release(message);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            release(message);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void sync(boolean block) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.sync(block);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            sync(block);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getReceivable() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getReceivable();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getReceivable();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getUnsettledAcks() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getUnsettledAcks();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getUnsettledAcks();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Receiver nextReceiver(long timeout) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.nextReceiver(timeout);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return nextReceiver(timeout);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Sender createSender(Address address) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            SenderInternal sender = new SenderFailoverDecorator(this,
                    (SenderInternal) _delegate.createSender(address));            
            synchronized (_connectionLock)
            {
                _senders.add(sender);
            }
            return sender;
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return createSender(address);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Sender createSender(String address) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            SenderInternal sender = new SenderFailoverDecorator(this,
                    (SenderInternal) _delegate.createSender(address));
            synchronized (_connectionLock)
            {
                _senders.add(sender);
            }
            return sender;
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return createSender(address);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Receiver createReceiver(Address address) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            ReceiverInternal receiver = new ReceiverFailoverDecorator(this,
                    (ReceiverInternal) _delegate.createReceiver(address));
            synchronized (_connectionLock)
            {
                _receivers.add(receiver);
            }
            return receiver;
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return createReceiver(address);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Receiver createReceiver(String address) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            ReceiverInternal receiver = new ReceiverFailoverDecorator(this,
                    (ReceiverInternal) _delegate.createReceiver(address));
            synchronized (_connectionLock)
            {
                _receivers.add(receiver);
            }
            return receiver;
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return createReceiver(address);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void checkError() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot // check if we already have the info.
        try
        {
            // Asking the delegate.
            _delegate.checkError();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber); // will throw an exception
            return;
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }
    
    @Override
    public boolean isClosed()
    {
        if (_state == SessionState.OPENED)
        {
            return super.isClosed(); // ask the delegate to be sure.
        }
        else
        {
            return true;
        }
    }

    @Override // From SessionInternal
    public void exception(TransportFailureException e, long serialNumber)
    {
        try
        {
            failover((TransportFailureException)e, serialNumber);
        }
        catch (SessionException ex)
        {
            _lastException = ex;
        }
    }
    
    public void exception(SessionException e)
    {
        handleSessionException(e);
    }

    @Override
    public void recreate() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            _connSerialNumber = _conn.getSerialNumber();
            _delegate.recreate();
            for (ReceiverInternal rec : _receivers)
            {
                rec.recreate();
            }
            for (SenderInternal sender : _senders)
            {
                sender.recreate();
            }
        }
    }

    @Override
    public ConnectionInternal getConnectionInternal()
    {
        return _conn;
    }

    @Override //From ConnectionEventListener
    public void exception(ConnectionException e)
    {
        // NOOP
    }

    @Override //From ConnectionEventListener
    public void eventOccured(ConnectionEvent event)
    {
        synchronized (_connectionLock)
        {
            switch(event.getType())
            {
            case PRE_FAILOVER:
            case CONNECTION_LOST:
                _state = SessionState.FAILOVER_IN_PROGRESS;
                break;
            case RECONNCTED:
                _state = SessionState.OPENED;
                break;
            case POST_FAILOVER:
                try
                {
                    if (_state != SessionState.OPENED) 
                    {
                        close();
                    }
                }
                catch (MessagingException e)
                {
                    _logger.warn("Exception when trying to close the session", e);
                }
                _connectionLock.notifyAll();
                break;
            default:
                break; //ignore the rest 
            }
        }
    }

    protected void failover(TransportFailureException e, long serialNumber) throws SessionException
    {
        synchronized (_connectionLock)
        {
            if (_connSerialNumber > serialNumber)
            {
                return; // ignore, we already have failed over.
            }
            _state = SessionState.FAILOVER_IN_PROGRESS;
            _conn.exception(e, serialNumber); // This triggers failover.
            waitForFailoverToComplete();
        }
    }
    
    protected void checkPreConditions() throws SessionException
    {
        switch (_state)
        {
        case CLOSED:
            throw new SessionException("Session is closed. You cannot invoke methods on a closed session",_lastException);
        case FAILOVER_IN_PROGRESS:
            waitForFailoverToComplete();
        }
    }

    /**
     * Session Exceptions will generally invalidate the Session.
     * TODO this needs to be revisited again.
     * A new session will need to be created in that case.
     * @param e
     * @throws MessagingException
     */
    protected SessionException handleSessionException(SessionException e)
    {
        synchronized (_connectionLock)
        {
            try
            {
                close();
            }
            catch(MessagingException ex)
            {
                _logger.warn("Error when closing session : " + getName(), ex);
            }
        }
        return new SessionException("Session has been closed",e);
    }
    
    protected void waitForFailoverToComplete() throws SessionException
    {
        synchronized (_connectionLock)
        {
            try
            {
                _connectionLock.wait(_failoverTimeout);
            }
            catch (InterruptedException e)
            {
                //ignore.
            }
            if (_state == SessionState.CLOSED)
            {
                throw new SessionException("Session is closed. Failover was unsuccesfull",_lastException);
            }
        }
    }
}
