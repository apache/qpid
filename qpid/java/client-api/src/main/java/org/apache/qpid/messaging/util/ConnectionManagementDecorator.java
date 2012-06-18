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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.SessionException;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ConnectionStateListener;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  A Decorator that adds basic housekeeping tasks to a connection.
 *  This allows the various implementations to reuse basic functions.
 *  This class adds,
 *  1. Basic session mgt (tracking, default name generation ..etc)
 *  2. Connection state management.
 *  3. Error handling.
 *
 *  <i> <b>Close() can be called by,</b>
 *      <ol>
 *       <li>The application (normal close)</li>
 *       <li>By the parent if it's not null (error)</li>
 *       <li>By this object if parent is null (error)</li>
 *      </ol>
 *  </i>
 *
 *  <u>Failover</u>
 *  This Decorator does not handle any failover.
 *
 *  If failover is handled at a layer above then it will take appropriate action.
 *  @see ConnectionFailoverDecorator for an example.
 *  If failover is handled at a layer below (or no failover at all) then an exception means the connection is no longer usable.
 *  Therefore this class will attempt to close the connection if the parent is null.
 */
public class ConnectionManagementDecorator implements ConnectionInternal
{
    private static Logger _logger = LoggerFactory.getLogger(ConnectionManagementDecorator.class);

    public enum ConnectionState { UNDEFINED, OPENED, CLOSED, ERROR}

    private ConnectionInternal _parent;
    private Connection _delegate;
    private ConnectionState _state = ConnectionState.UNDEFINED;
    private UUIDGen _ssnNameGenerator = UUIDs.newGenerator();
    private Map<String, SessionInternal> _sessions = new ConcurrentHashMap<String,SessionInternal>();
    private ConnectionException _lastException;
    private List<ConnectionStateListener> _stateListeners = new ArrayList<ConnectionStateListener>();

    private final Object _connectionLock;

    public ConnectionManagementDecorator(Connection delegate)
    {
        this(null,delegate);
    }

    public ConnectionManagementDecorator(ConnectionInternal parent, Connection delegate)
    {
        _delegate = delegate;
        _parent = parent;
        _connectionLock = (_parent == null) ? new Object() : _parent.getConnectionLock();
    }

    @Override
    public void open() throws MessagingException
    {
        // return without exception denotes success
        _delegate.open();
        synchronized (_connectionLock)
        {
            _state = ConnectionState.OPENED;
            for (ConnectionStateListener l: _stateListeners)
            {
                l.opened();
            }
        }
    }

    @Override
    public boolean isOpen() throws MessagingException
    {
        return _delegate.isOpen();
    }

    @Override
    public void close() throws MessagingException
    {
        checkClosedAndThrowException("Connection is already closed");
        synchronized(_connectionLock)
        {
            _state = ConnectionState.CLOSED;
            for (Session ssn : _sessions.values())
            {
                try
                {
                    ssn.close();
                }
                catch (Exception e)
                {
                    _logger.warn("Error closing session",e);
                }
            }
            _sessions.clear();

            for (ConnectionStateListener l: _stateListeners)
            {
                l.closed();
            }
        }
        _delegate.close();
    }

    @Override
    public Session createSession(String name) throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            if (name == null || name.isEmpty()) { name = generateSessionName(); }
            SessionInternal ssn =  new SessionManagementDecorator(this,name,_delegate.createSession(name));
            _sessions.put(name, ssn);
            return ssn;
        }
        catch(ConnectionException e)
        {
            exception(e);
            // If there is a failover handler above this it will handle it.
            // Otherwise the application gets this.
            throw new ConnectionException("Connection closed",e);
        }
    }

    @Override
    public Session createTransactionalSession(String name)
    throws MessagingException
    {
        checkClosedAndThrowException();
        try
        {
            if (name == null || name.isEmpty()) { name = generateSessionName(); }
            SessionInternal ssn = new SessionManagementDecorator(this,name,_delegate.createTransactionalSession(name));
            _sessions.put(name, ssn);
            return ssn;
        }
        catch(ConnectionException e)
        {
            exception(e);
            // If there is a failover handler above this it will handle it.
            // Otherwise the application gets this.
            throw new ConnectionException("Connection closed",e);
        }
    }

    @Override
    public String getAuthenticatedUsername() throws MessagingException
    {
        checkClosedAndThrowException();
        return _delegate.getAuthenticatedUsername();
    }

    @Override
    public MessageFactory getMessageFactory() throws MessagingException
    {
        checkClosedAndThrowException();
        return _delegate.getMessageFactory();
    }

    @Override
    public void addConnectionStateListener(ConnectionStateListener l) throws ConnectionException
    {
        checkClosedAndThrowException();
        synchronized (_connectionLock)
        {
            _stateListeners.add(l);
        }
    }

    @Override
    public void removeConnectionStateListener(ConnectionStateListener l) throws ConnectionException
    {
        checkClosedAndThrowException();
        synchronized (_connectionLock)
        {
            _stateListeners.remove(l);
        }
    }

    @Override
    public List<SessionInternal> getSessions() throws ConnectionException
    {
        checkClosedAndThrowException();
        return new ArrayList<SessionInternal>(_sessions.values());
    }

    @Override
    public void unregisterSession(SessionInternal ssn)
    {
        _sessions.remove(ssn.getName());
    }

    @Override // Called by the delegate or a a session created by this connection.
    public void exception(ConnectionException e)
    {
        synchronized(_connectionLock)
        {
            _state = ConnectionState.ERROR;
            if (_lastException != null)
            {
                _logger.warn("Last exception was not notified to the application", _lastException);
            }
            _lastException = e;

            for (ConnectionStateListener l: _stateListeners)
            {
                l.exception(_lastException);
            }

            if (_parent != null)
            {
                _parent.exception(e);
            }
            else
            {
                try
                {
                    close();
                }
                catch(MessagingException ex)
                {
                    //ignore
                }
            }
        }
        // should we clean lastException if we notify it via a listener?
    }

    @Override
    public Object getConnectionLock()
    {
        return _connectionLock;
    }

    @Override
    public void recreate() throws MessagingException
    {
        // TODO Auto-generated method stub
    }

    private void checkClosedAndThrowException() throws ConnectionException
    {
        checkClosedAndThrowException("Connection is closed. You cannot invoke methods on a closed connection");
    }

    private void checkClosedAndThrowException(String msg) throws ConnectionException
    {
        switch (_state)
        {
        case UNDEFINED:
        case ERROR:
            throw new ConnectionException("Connection is in an error state. The connection may or may not recover from this");
        case CLOSED:
            synchronized(_connectionLock)
            {
                if(_lastException != null)
                {
                    Throwable cause = _lastException;
                    _lastException = null;
                    throw new ConnectionException(msg, cause);
                }
                else
                {
                    throw new ConnectionException(msg);
                }
            }
        default:
            break;
        }
    }

    private String generateSessionName()
    {
        // TODO add local IP and pid to the beginning;
        return _ssnNameGenerator.generate().toString();
    }
}
