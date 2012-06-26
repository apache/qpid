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

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionEvent;
import org.apache.qpid.messaging.internal.ConnectionEvent.EventType;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConnectionDecorator implements ConnectionInternal
{
    private final Logger _logger = LoggerFactory.getLogger(getClass());
    
    protected ConnectionInternal _delegate;
    protected final Object _connectionLock;
    protected List<ConnectionEventListener> _stateListeners = new ArrayList<ConnectionEventListener>();
    protected Map<String, SessionInternal> _sessions = new ConcurrentHashMap<String,SessionInternal>();
    
    protected AbstractConnectionDecorator(ConnectionInternal delegate, Object lock)
    {
        _delegate = delegate;
        _connectionLock = lock;
    }

    @Override
    public void open() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            _delegate.open();
        }
    }
    
    public void reconnect(String url, Map<String,Object> options) throws TransportFailureException
    {
        synchronized (_connectionLock)
        {
            _delegate.reconnect(url,options);
        }
    }

    @Override
    public boolean isOpen() throws MessagingException
    {
        checkPreConditions();
        return _delegate.isOpen();
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized (_connectionLock)
        {
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
            try
            {
                _delegate.close();
            }
            catch(Exception e)
            {
                _logger.warn("Error closing connection",e);
            }
            notifyEvent(new ConnectionEvent(this,EventType.CLOSED,this));
        }
    }

    @Override
    public String getAuthenticatedUsername() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getAuthenticatedUsername();
    }

    @Override
    public MessageFactory getMessageFactory() throws MessagingException
    {
        checkPreConditions();
        return _delegate.getMessageFactory();
    }

    @Override
    public void addConnectionEventListener(ConnectionEventListener l) throws ConnectionException
    {
        checkPreConditions();
        synchronized (_connectionLock)
        {
            _stateListeners.add(l);
        }
    }

    @Override
    public void removeConnectionEventListener(ConnectionEventListener l) throws ConnectionException
    {
        checkPreConditions();
        synchronized (_connectionLock)
        {
            _stateListeners.remove(l);
        }
    }


    @Override
    public List<SessionInternal> getSessions() throws ConnectionException
    {
        checkPreConditions();
        return new ArrayList<SessionInternal>(_sessions.values());
    }

    @Override
    public void unregisterSession(SessionInternal ssn)
    {
        synchronized (_connectionLock)
        {
            _sessions.remove(ssn.getName());
        }
    }

    @Override
    public void exception(TransportFailureException e, long serialNumber)
    {
        synchronized(_connectionLock)
        {
            for (ConnectionEventListener l: _stateListeners)
            {
                l.exception(new ConnectionException("Connection Failed",e));
            }
        }
    }

    @Override
    public Object getConnectionLock()
    { 
        return _connectionLock;
    }

    @Override
    public String getConnectionURL()
    {
        return _delegate.getConnectionURL();
    }

    @Override
    public Map<String, Object> getConnectionOptions()
    {
        return _delegate.getConnectionOptions();
    }

    @Override
    public long getSerialNumber()
    {
        return _delegate.getSerialNumber();
    }
    
    protected void notifyEvent(ConnectionEvent event)
    {
        for (ConnectionEventListener l: _stateListeners)
        {
            l.eventOccured(event);
        }
    }
    
    protected abstract void checkPreConditions() throws ConnectionException;
}
