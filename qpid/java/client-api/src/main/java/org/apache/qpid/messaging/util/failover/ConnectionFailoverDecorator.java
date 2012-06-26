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

import java.util.Map;

import org.apache.qpid.messaging.ConnectionException;
import org.apache.qpid.messaging.MessageFactory;
import org.apache.qpid.messaging.MessagingException;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.TransportFailureException;
import org.apache.qpid.messaging.internal.ConnectionEvent;
import org.apache.qpid.messaging.internal.ConnectionEvent.EventType;
import org.apache.qpid.messaging.internal.ConnectionEventListener;
import org.apache.qpid.messaging.internal.ConnectionInternal;
import org.apache.qpid.messaging.internal.ConnectionString;
import org.apache.qpid.messaging.internal.FailoverStrategy;
import org.apache.qpid.messaging.internal.FailoverStrategyFactory;
import org.apache.qpid.messaging.internal.SessionInternal;
import org.apache.qpid.messaging.util.AbstractConnectionDecorator;
import org.apache.qpid.util.UUIDGen;
import org.apache.qpid.util.UUIDs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * Closing after unsuccessful failover is not done yet!
 * 
 */
public class ConnectionFailoverDecorator extends AbstractConnectionDecorator
{
    private static Logger _logger = LoggerFactory.getLogger(ConnectionFailoverDecorator.class);
    
    public enum ConnectionState
    {
        UNDEFINED,
        OPENED,
        CONNECTION_LOST,
        FAILOVER_IN_PROGRESS,
        CLOSED
    };

    private ConnectionState _state = ConnectionState.UNDEFINED;
    private long _failoverTimeout = Long.getLong("qpid.failover-timeout", 1000);
    private UUIDGen _ssnNameGenerator = UUIDs.newGenerator();
    private ConnectionException _lastException;
    private FailoverStrategy _failoverStrategy;
    private long _serialNumber = 0;

    public ConnectionFailoverDecorator(ConnectionInternal delegate, Object lock)
    {
        super(delegate,lock);
        _failoverStrategy = FailoverStrategyFactory.get().getFailoverStrategy(delegate);
    }

    @Override
    public void open() throws MessagingException
    {
        synchronized (_connectionLock)
        {
            super.open();
            _serialNumber = getSerialNumber();
            _state = ConnectionState.OPENED;
            notifyEvent(new ConnectionEvent(this,EventType.OPENED,this));
        }
    }

    @Override
    public boolean isOpen() throws MessagingException
    {
        // If the state is opened, query the delegate and see.
        if (_state == ConnectionState.OPENED)
        {
            return super.isOpen();
        }
        else
        {
            return false;
        }
    }

    @Override
    public void reconnect(String url,Map<String,Object> options) throws TransportFailureException
    {
        synchronized (_connectionLock)
        {
            super.reconnect(url,options);
            _serialNumber = getSerialNumber();
            _state = ConnectionState.OPENED;
            notifyEvent(new ConnectionEvent(this,EventType.RECONNCTED,this));
        }
    }

    @Override
    public void close() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            if (_state == ConnectionState.CLOSED)
            {
                throw new MessagingException("Connection is already closed");
            }
            super.close();
            _state = ConnectionState.CLOSED;
        }
    }

    @Override
    public Session createSession(String name) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _serialNumber; // take a snapshot
        try
        {
            if (name == null || name.isEmpty()) { name = generateSessionName(); }
            SessionInternal ssn = new SessionFailoverDecorator(this,
                    (SessionInternal) _delegate.createSession(name));
            _sessions.put(name, ssn);
            return ssn;
        }
        catch(TransportFailureException e)
        {
            failover(e,serialNumber);
            return createSession(name);
        }
    }

    @Override
    public Session createTransactionalSession(String name) throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _serialNumber; // take a snapshot
        try
        {
            if (name == null || name.isEmpty()) { name = generateSessionName(); }
            SessionInternal ssn = new SessionFailoverDecorator(this,
                    (SessionInternal)_delegate.createTransactionalSession(name));
            _sessions.put(name, ssn);
            return ssn;
        }
        catch(TransportFailureException e)
        {
            failover(e,serialNumber);
            return createTransactionalSession(name);
        }
    }

    @Override
    public String getAuthenticatedUsername() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _serialNumber; // take a snapshot
        try
        {
            return _delegate.getAuthenticatedUsername();
        }
        catch(TransportFailureException e)
        {
            failover(e,serialNumber);
            return getAuthenticatedUsername();
        }
    }

    @Override
    public MessageFactory getMessageFactory() throws MessagingException
    {
        checkPreConditions();
        long serialNumber = _serialNumber; // take a snapshot
        try
        {
            return _delegate.getMessageFactory();
        }
        catch(TransportFailureException e)
        {
            failover(e,serialNumber);
            return getMessageFactory();
        }
    }

    @Override
    public void exception(TransportFailureException e, long serialNumber)
    {
        try
        {
            failover(e,serialNumber);
        }
        catch(ConnectionException ex)
        {   
            //ignore. 
            //failover() handles notifications
        }
    }

    @Override
    public void recreate() throws MessagingException
    {
        synchronized(_connectionLock)
        {
            for (SessionInternal ssn: _sessions.values())
            {
                ssn.recreate();
            }
        }
    }

    protected void checkPreConditions() throws ConnectionException
    {
        switch (_state)
        {
        case CLOSED:
            throw new ConnectionException("Connection is closed. You cannot invoke methods on a closed connection");
        case UNDEFINED:
            throw new ConnectionException("Connection should be opened before it can be used");
        case CONNECTION_LOST:    
        case FAILOVER_IN_PROGRESS:
            waitForFailoverToComplete();
        }
    }
    
    protected void failover(TransportFailureException e,long serialNumber) throws ConnectionException
    {
        synchronized(_connectionLock)
        {   
            if (_serialNumber > serialNumber)
            {
                return; // Ignore, We have a working connection now.
            }
         
            _logger.warn("Connection lost!");
            _state = ConnectionState.CONNECTION_LOST;
            notifyEvent(new ConnectionEvent(this,EventType.CONNECTION_LOST,this));            
                        
            if (_failoverStrategy.failoverAllowed())
            {
                // Failover is allowed at least once.
                _state = ConnectionState.FAILOVER_IN_PROGRESS;
                notifyEvent(new ConnectionEvent(this,EventType.PRE_FAILOVER,this));
            
            
            
                StringBuffer errorMsg = new StringBuffer();
                while (_failoverStrategy.failoverAllowed())
                {                
                    try
                    {
                        ConnectionString conString = _failoverStrategy.getNextConnectionString();
                        notifyEvent(new ConnectionEvent(this,EventType.RECONNCTION_ATTEMPTED,this));
                        _logger.warn("Attempting connection to " + conString.getUrl());
                        reconnect(conString.getUrl(), conString.getOptions());
                        try
                        {
                            recreate();
                            _state = ConnectionState.OPENED;
                            _lastException = null;
                        }
                        catch (MessagingException ex)
                        {
                            _lastException = new ConnectionException(
                                    "Recreating the state for the connection and it's children failed",
                                    ex);
                        }
                        break;
                    }
                    catch (TransportFailureException te)
                    {
                        errorMsg.append("\nUnable to connect to : " +
                                   _failoverStrategy.getCurrentConnectionString().getUrl() +
                                   " due to : " +
                                   te.getMessage());
                        notifyEvent(new ConnectionEvent(this,EventType.RECONNCTION_FAILED,this));
                    }
                }
                
                if (_state != ConnectionState.OPENED)
                {
                    closeInternal();
                    _lastException = new ConnectionException("Failover was unsuccessful." + errorMsg.toString());
                    _logger.warn("Faiolver was unsuccesful" + errorMsg.toString());
                }
                notifyEvent(new ConnectionEvent(this,EventType.POST_FAILOVER,this));
            }
            else
            {
                closeInternal();
                _state = ConnectionState.CLOSED;
                _lastException = new ConnectionException("Connection Failed!",e);
                _logger.warn("Connection Failed!", e);
            }
            
            _connectionLock.notifyAll();
            
            if (_lastException != null)
            {
                for (ConnectionEventListener l: _stateListeners)
                {
                    l.exception(_lastException);
                }
                throw _lastException;
            }
        }
    }
    
    protected void waitForFailoverToComplete() throws ConnectionException
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
            if (_state == ConnectionState.CLOSED)
            {
                throw new ConnectionException("Connection is closed. Failover was unsuccesfull",_lastException);
            }
        }
    }

    private String generateSessionName()
    {
        // TODO add local IP and pid to the beginning;
        return _ssnNameGenerator.generate().toString();
    }
    
    // Suppresses the exceptions
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
