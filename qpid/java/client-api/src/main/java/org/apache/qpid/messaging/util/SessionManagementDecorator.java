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

import java.util.ArrayList;
import java.util.List;

import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Sender;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.ext.ConnectionExt;
import org.apache.qpid.messaging.ext.ReceiverExt;
import org.apache.qpid.messaging.ext.SenderExt;
import org.apache.qpid.messaging.ext.SessionExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Decorator that adds basic housekeeping tasks to a session.
 * This class adds,
 * 1. Management of receivers and senders created by this session.
 * 2. State management.
 * 3. Exception handling.
 *
 * <b>Exception Handling</b>
 * This class will wrap each method call to it's delegate to handle error situations.
 * First it will check if the session is already CLOSED or in an ERROR situation.
 * Then it will look for connection and session errors and handle as follows.
 *
 * <b>Connection Exceptions</b>
 * This class intercepts ConnectionException's and are passed onto the connection.
 * The Session will be marked as ERROR and a session exception will be thrown with an appropriate message.
 * Any further use of the session is prevented until it moves to OPENED.
 *
 * If failover is handled at a layer above, there will be a Session Decorator that
 * would handle the session exception and retry when the connection is available.
 * This handler may block the call until the state moves into either OPENED or CLOSED.
 * Ex @see SessionFailoverDecorator.
 *
 * If failover is handled at a layer below, then a connection exception means it has failed already.
 * Therefore when passed to the connection,the exception will be thrown directly to the application.
 * The connection object will be responsible for calling close on this session for the above case.
 *
 * <i> <b>Close() can be called by,</b>
 *      <ol>
 *       <li>The application (normal close)</li>
 *       <li>By the parent via failover (error)</li>
 *       <li>By the connection object, if no failover(error)</li>
 *       <li>By itself if it receives and exception (error)</li>
 *      </ol>
 * </i>
 *
 * <b>Session Exceptions</b>
 * For the time being, anytime a session exception is received, the session will be marked CLOSED.
 * We need to revisit this.
 */
public class SessionManagementDecorator implements SessionExt
{
    private static Logger _logger = LoggerFactory.getLogger(SessionManagementDecorator.class);

    public enum SessionState {OPENED, CLOSED, ERROR}

    private ConnectionExt _conn;
    private Session _delegate;
    SessionState _state = SessionState.OPENED;
    private List<ReceiverExt> _receivers = new ArrayList<ReceiverExt>();
    private List<SenderExt> _senders = new ArrayList<SenderExt>();
    private final Object _connectionLock;  // global per connection lock

    public SessionManagementDecorator(ConnectionExt conn, Session delegate)
    {
        _conn = conn;
        _delegate = delegate;
        _connectionLock = conn.getConnectionLock();
    }

    @Override
    public boolean isClosed()
    {
        return _state == SessionState.CLOSED;
    }

    @Override
    public void close() throws MessagingException
    {
        checkClosedAndThrowException("Session is already closed");
        synchronized(_connectionLock)
        {
            _state = SessionState.CLOSED;
            for (Sender sender: _senders)
            {
                sender.close();
            }
            _senders.clear();

            for (Receiver receiver: _receivers)
            {
                receiver.close();
            }
            _receivers.clear();
            _delegate.close();
        }
    }

    @Override
    public void commit() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.commit();
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
    public void rollback() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.rollback();
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
    public void acknowledge(boolean sync) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.acknowledge(sync);
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
    public void acknowledge(Message message, boolean sync)
    throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.acknowledge(message, sync);
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
    public void reject(Message message) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.reject(message);
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
    public void release(Message message) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.release(message);
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
    public void sync(boolean block) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            _delegate.sync(block);
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
    public int getReceivable() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.getReceivable();
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
    public int getUnsettledAcks() throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.getUnsettledAcks();
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
    public Receiver nextReceiver(long timeout) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            return _delegate.nextReceiver(timeout);
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
    public Sender createSender(Address address) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            SenderExt sender = new SenderManagementDecorator(this,_delegate.createSender(address));
            _senders.add(sender);
            return sender;
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
    public Sender createSender(String address) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            SenderExt sender = new SenderManagementDecorator(this,_delegate.createSender(address));
            _senders.add(sender);
            return sender;
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
    public Receiver createReceiver(Address address) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            ReceiverExt receiver = new ReceiverManagementDecorator(this,_delegate.createReceiver(address));
            _receivers.add(receiver);
            return receiver;
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
    public Receiver createReceiver(String address) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            ReceiverExt receiver = new ReceiverManagementDecorator(this,_delegate.createReceiver(address));
            _receivers.add(receiver);
            return receiver;
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
    public Connection getConnection() throws MessagingException
    {
        checkError();
        return _conn;  // always return your peer (not your delegate's peer)
    }

    @Override
    public boolean hasError()
    {
        return _delegate.hasError();
    }

    @Override
    public void checkError() throws MessagingException
    {
        checkClosedAndThrowException(); // check if we already have the info.
        try
        {
            // Asking the delegate.
            _delegate.checkError();
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
    public void exception(MessagingException e)
    {
        if (e instanceof ConnectionException)
        {
            handleConnectionException((ConnectionException)e);
        }
        else if (e instanceof SessionException)
        {
            handleSessionException((SessionException)e);
        }
    }

    @Override
    public void recreate() throws MessagingException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public ConnectionExt getConnectionExt()
    {
        return _conn;
    }

    private void checkClosedAndThrowException() throws SessionException
    {
        checkClosedAndThrowException("Session is closed. You cannot invoke methods on a closed sesion");
    }

    private void checkClosedAndThrowException(String closedMessage) throws SessionException
    {
        switch (_state)
        {
        case ERROR:
            throw new SessionException("Session is in a temporary error state. The session may or may not recover from this");
        case CLOSED:
            throw new SessionException(closedMessage);
        }
    }

    /**
     * A ConnectionException will cause the Session to go into a temporary error state,
     * which prevents it from being used further.
     * From there the Session can be moved into OPENED (if failover works) or
     * CLOSED if there is no failover or if failover has failed.
     * @param e
     * @throws MessagingException
     */
    private SessionException handleConnectionException(ConnectionException e)
    {
        synchronized (_connectionLock)
        {
            _state = SessionState.ERROR;
            _conn.exception(e); // This might trigger failover in a layer above.
            if (_state == SessionState.CLOSED)
            {
                // The connection has instructed the session to be closed.
                // Either there was no failover, or failover has failed.
                return new SessionException("Session is closed due to connection error",e);
            }
            else
            {
                // Asking the application or the Parent handler to retry the operation.
                // The Session should be in OPENED state at this time.
                return new SessionException("Session was in a temporary error state due to connection error." +
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
    private SessionException handleSessionException(SessionException e)
    {
        synchronized (_connectionLock)
        {
            try
            {
                close();
            }
            catch(MessagingException ex)
            {
                // Should not throw an exception here.
                // Even if it did, does't matter as are closing.
            }
        }
        return new SessionException("Session has been closed",e);
    }
}
