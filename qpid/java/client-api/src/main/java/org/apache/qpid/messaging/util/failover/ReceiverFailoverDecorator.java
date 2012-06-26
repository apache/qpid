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

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.ReceiverException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionEvent;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.apache.qpid.messaging.util.AbstractReceiverDecorator;
import org.apache.qpid.messaging.util.failover.SessionFailoverDecorator.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A Decorator that adds failover and basic housekeeping tasks to a Sender.
 * This class adds,
 * <ol>
 * <li>Failover support.</li>
 * <li>State management.</li>
 * <li>Exception handling.</li>
 * <li>Failover</li>
 * </ol></p>
 *
 * <p><b>Exception Handling</b><br>
 * This class will wrap each method call to it's delegate to handle error situations.
 * First it will check if the Receiver is already CLOSED or FAILOVER_IN_PROGRESS state.
 * If latter it will wait until the Receiver is moved to OPENED, CLOSED or the timer expires.
 * For the last two cases a ReceiverException will be thrown and the Receiver closed.</p>
 *
 * <p><b>TransportFailureException</b><br>
 * This class intercepts TransportFailureExceptions and are passed onto the session,
 * via the exception() method, which in turn passes into the connection.
 * The Receiver will be marked as FAILOVER_IN_PROGRESS and the "operation" will be
 * blocked until the exception() on the Session object returns. At this point
 * the Receiver is either moved to OPENED or CLOSED.</p>
 *
 * <p><b>SessionException</b><br>
 * For the time being, anytime a session exception is received, the Receiver will be marked CLOSED.
 * We need to revisit this.</p>
 *
 * <p><i> <b>Close() can be called by,</b>
 *      <ol>
 *       <li>The application (normal close).</li>
 *       <li>By the session object, if close is called on it.(normal close)</li>
 *       <li>By the connection object, if close is called on it.(normal close)</li>
 *       <li>By the connection object, if failover was unsuccessful(error)</li>
 *       <li>By itself (via the session) if it receives and exception (error).</li>
 *      </ol>
 * </i></p>
 */
public class ReceiverFailoverDecorator extends AbstractReceiverDecorator implements ConnectionEventListener
{
    private static Logger _logger = LoggerFactory.getLogger(ReceiverFailoverDecorator.class);

    public enum ReceiverState {OPENED, CLOSED, FAILOVER_IN_PROGRESS};

    private ReceiverState _state = ReceiverState.OPENED;
    private long _failoverTimeout = Long.getLong("qpid.failover-timeout", 1000);
    private ReceiverException _lastException;
    private long _connSerialNumber = 0;

    public ReceiverFailoverDecorator(SessionInternal ssn, ReceiverInternal delegate)
    {
        super(ssn,delegate);
        synchronized(_connectionLock)
        {
            _connSerialNumber = ssn.getConnectionInternal().getSerialNumber();
        }
    }

    @Override
    public Message get(long timeout) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.get(timeout);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return get(timeout);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Message fetch(long timeout) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.fetch(timeout);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return fetch(timeout);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void setCapacity(int capacity) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            _delegate.setCapacity(capacity);
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            setCapacity(capacity);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getCapacity() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getCapacity();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getCapacity();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getAvailable() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getAvailable();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getAvailable();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _connSerialNumber; // take a snapshot
        try
        {
            return _delegate.getUnsettled();
        }
        catch (TransportFailureException e)
        {
            failover(e,serialNumber);
            return getUnsettled();
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            if (_state == ReceiverState.CLOSED)
            {
                throw new MessagingException("Receiver is already closed");
            }
            _state = ReceiverState.CLOSED;
            super.close();
        }
    }

    @Override
    public boolean isClosed()
    {
        return _state == ReceiverState.CLOSED;
    }

    @Override
    public Session getSession() throws MessagingException
    {
        checkPreConditions();
        _ssn.checkError();
        return _ssn;
    }

    @Override
    public void recreate() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            _connSerialNumber = _ssn.getConnectionInternal().getSerialNumber();
            _delegate.recreate();
            _state = ReceiverState.OPENED;
        }
    }

    @Override
    public void eventOccured(ConnectionEvent event)
    {
        synchronized (_connectionLock)
        {
            switch(event.getType())
            {
            case PRE_FAILOVER:
            case CONNECTION_LOST:
                _state = ReceiverState.FAILOVER_IN_PROGRESS;
                break;
            case RECONNCTED:
                _state = ReceiverState.OPENED;
                break;
            case POST_FAILOVER:
                try
                {
                    if (_state != ReceiverState.OPENED)
                    {
                        close();
                    }
                }
                catch (MessagingException e)
                {
                    _logger.warn("Exception when trying to close the receiver", e);
                }
                _connectionLock.notifyAll();
                break;
            default:
                break; //ignore the rest
            }
        }
    }

    @Override  // From ConnectionEventListener
    public void exception(ConnectionException e)
    {// NOOP
    }

    protected void checkPreConditions() throws ReceiverException
    {
        switch (_state)
        {
        case CLOSED:
            throw new ReceiverException("Receiver is closed. You cannot invoke methods on a closed Receiver",_lastException);
        case FAILOVER_IN_PROGRESS:
            waitForFailoverToComplete();
        }
    }

    protected void waitForFailoverToComplete() throws ReceiverException
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
            if (_state == ReceiverState.CLOSED)
            {
                throw new ReceiverException("Receiver is closed. Failover was unsuccesfull",_lastException);
            }
            else if  (_state == ReceiverState.FAILOVER_IN_PROGRESS)
            {
                closeInternal();
                throw new ReceiverException("Receiver is closed. Failover did not complete on time");
            }
        }
    }

    protected ReceiverException handleSessionException(SessionException e)
    {
        synchronized (_connectionLock)
        {
            // This should close all receivers (including this) and senders.
            _ssn.exception(e);
        }
        return new ReceiverException("Session has been closed",e);
    }

    protected void failover(TransportFailureException e, long serialNumber) throws ReceiverException
    {
        synchronized (_connectionLock)
        {
            if (_connSerialNumber > serialNumber)
            {
                return; // ignore, we already have failed over.
            }
            _state = ReceiverState.FAILOVER_IN_PROGRESS;
            _ssn.exception(e, serialNumber); // This triggers failover.
            waitForFailoverToComplete();
        }
    }

    /** Suppress Exceptions as */
    private void closeInternal()
    {
        try
        {
            close();
        }
        catch (Exception e)
        {
            //ignore
        }
    }
}
