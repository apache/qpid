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
package org.apache.qpid.messaging.util;

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.ReceiverException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.internal.ReceiverInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Decorator that adds basic housekeeping tasks to a Receiver.
 * This class adds,
 * 1. State management.
 * 2. Exception handling.
 *
 */
public class ReceiverManagementDecorator implements ReceiverInternal
{
    private static Logger _logger = LoggerFactory.getLogger(ReceiverManagementDecorator.class);

    public enum ReceiverState {OPENED, CLOSED, ERROR};

    private Receiver _delegate;
    private ReceiverState _state = ReceiverState.OPENED;
    private SessionInternal _ssn;
    private final Object _connectionLock;  // global per connection lock

    public ReceiverManagementDecorator(SessionInternal ssn, Receiver delegate)
    {
        _ssn = ssn;
        _delegate = delegate;
        _connectionLock = ssn.getConnectionInternal().getConnectionLock();
    }

    @Override
    public Message get(long timeout) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.get(timeout);
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public Message fetch(long timeout) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.fetch(timeout);
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void setCapacity(int capacity) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.setCapacity(capacity);
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getCapacity() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.getCapacity();
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getAvailable() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.getAvailable();
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public int getUnsettled() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.getUnsettled();
        }
        catch (ConnectionException e)
        {
            throw handleConnectionException(e);
        }
        catch (SessionException e)
        {
            throw handleSessionException(e);
        }
    }

    @Override
    public void close() throws MessagingException
    {
        checkClosedAndThrowException("Receiver is already closed");
        synchronized (_connectionLock)
        {
            _state = ReceiverState.CLOSED;
            _delegate.close();
        }
    }

    @Override
    public boolean isClosed()
    {
        return _state == ReceiverState.CLOSED;
    }

    @Override
    public String getName() throws MessagingException
    {
        checkClosedAndThrowException();
        return _delegate.getName();
    }

    @Override
    public Session getSession() throws MessagingException
    {
        checkClosedAndThrowException();
        _ssn.checkError();
        return _ssn;
    }

    @Override
    public void recreate() throws MessagingException
    {
        // TODO Auto-generated method stub

    }

    private void checkClosedAndThrowException() throws ReceiverException
    {
        checkClosedAndThrowException("Receiver is closed. You cannot invoke methods on a closed receiver");
    }

    private void checkClosedAndThrowException(String closedMessage) throws ReceiverException
    {
        switch (_state)
        {
        case ERROR:
            throw new ReceiverException("Receiver is in a temporary error state. The session may or may not recover from this");
        case CLOSED:
            throw new ReceiverException(closedMessage);
        }
    }

    /**
     * A ConnectionException will cause the Session/Receiver to go into a temporary error state,
     * which prevents it from being used further.
     * From there the Session and Receiver can be moved into OPENED (if failover works) or
     * CLOSED if there is no failover or if failover has failed.
     * @param e
     * @throws MessagingException
     */
    private ReceiverException handleConnectionException(ConnectionException e)
    {
        synchronized (_connectionLock)
        {
            _state = ReceiverState.ERROR;
            _ssn.exception(e); // This might trigger failover in a layer above.
            if (_state == ReceiverState.CLOSED)
            {
                // The connection has instructed the session and it's child objects to be closed.
                // Either there was no failover, or failover has failed.
                return new ReceiverException("Receiver is closed due to connection error",e);
            }
            else
            {
                // Asking the application or the Parent handler to retry the operation.
                // The Session and Receiver should be in OPENED state at this time.
                return new ReceiverException("Receiver was in a temporary error state due to connection error." +
                        "Plase retry your operation",e);
            }
        }
    }

    /**
     * Session Exceptions will generally invalidate the Session.
     * TODO this needs to be revisited again.
     * A new session will need to be created in that case.
     * @param e
     * @throws MessagingException
     */
    private ReceiverException handleSessionException(SessionException e)
    {
        synchronized (_connectionLock)
        {
            // This should close all receivers (including this) and senders.
            _ssn.exception(e);
        }
        return new ReceiverException("Session has been closed",e);
    }
}
