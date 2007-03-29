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
package org.apache.qpid.nclient.amqp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.ConnectionCloseOkBody;
import org.apache.qpid.framing.ConnectionOpenBody;
import org.apache.qpid.framing.ConnectionOpenOkBody;
import org.apache.qpid.framing.ConnectionSecureBody;
import org.apache.qpid.framing.ConnectionSecureOkBody;
import org.apache.qpid.framing.ConnectionStartBody;
import org.apache.qpid.framing.ConnectionStartOkBody;
import org.apache.qpid.framing.ConnectionTuneBody;
import org.apache.qpid.framing.ConnectionTuneOkBody;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.amqp.state.AMQPState;
import org.apache.qpid.nclient.amqp.state.AMQPStateMachine;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.nclient.transport.TransportConnection;
import org.apache.qpid.nclient.util.AMQPValidator;

/**
 * This maps directly to the Connection class defined in the AMQP protocol This class is a finite state machine and is
 * thread safe by design A particular method (state changing) can only be invoked once and only in sequence or else an
 * IllegalStateTransitionException will be thrown Also only one thread can enter those methods at a given time.
 */
public class AMQPConnection extends AMQPStateMachine implements AMQPMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQPConnection.class);

    private Phase _phase;

    private TransportConnection _connection;

    private long _correlationId;

    private AMQPState _currentState;

    private final AMQPState[] _validCloseStates = new AMQPState[] { AMQPState.CONNECTION_NOT_STARTED, AMQPState.CONNECTION_NOT_SECURE,
	    AMQPState.CONNECTION_NOT_TUNED, AMQPState.CONNECTION_NOT_OPENED, AMQPState.CONNECTION_OPEN, };

    // The wait period until a server sends a respond
    private long _serverTimeOut = 1000;

    private final Lock _lock = new ReentrantLock();

    private final Condition _connectionNotStarted = _lock.newCondition();

    private final Condition _connectionNotSecure = _lock.newCondition();

    private final Condition _connectionNotTuned = _lock.newCondition();

    private final Condition _connectionNotOpened = _lock.newCondition();

    private final Condition _connectionNotClosed = _lock.newCondition();

    private ConnectionStartBody _connectionStartBody;

    private ConnectionSecureBody _connectionSecureBody;

    private ConnectionTuneBody _connectionTuneBody;

    private ConnectionOpenOkBody _connectionOpenOkBody;

    private ConnectionCloseOkBody _connectionCloseOkBody;
    
    private ConnectionCloseBody _connectionCloseBody;

    protected AMQPConnection(TransportConnection connection)
    {
	_connection = connection;
	_currentState = AMQPState.CONNECTION_UNDEFINED;
	_serverTimeOut = ClientConfiguration.get().getLong(QpidConstants.SERVER_TIMEOUT_IN_MILLISECONDS);
    }

    /**
     * ------------------------------------------- 
     * API Methods 
     * --------------------------------------------
     */

    /**
     * Opens the TCP connection and let the formalities begin.
     */
    public ConnectionStartBody openTCPConnection() throws AMQPException
    {
	_lock.lock();
	// open the TCP connection
	try
	{
	    _connectionStartBody = null;
	    checkIfValidStateTransition(AMQPState.CONNECTION_UNDEFINED, _currentState, AMQPState.CONNECTION_NOT_STARTED);
	    _phase = _connection.connect();

	    // waiting for ConnectionStartBody or error in connection
	    //_connectionNotStarted.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _connectionNotStarted.await();
	    
	    checkIfConnectionClosed();
	    AMQPValidator.throwExceptionOnNull(_connectionStartBody, "The broker didn't send the ConnectionStartBody in time");
	    _currentState = AMQPState.CONNECTION_NOT_STARTED;
	    return _connectionStartBody;
	}
	catch (InterruptedException e)
	{
	    throw new AMQPException("Error opening connection to broker", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * The current java broker implementation can send a connection tune body
     * as a response to the startOk. Not sure if that is the correct behaviour.
     */
    public AMQMethodBody startOk(ConnectionStartOkBody connectionStartOkBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _connectionSecureBody = null;
	    checkIfValidStateTransition(AMQPState.CONNECTION_NOT_STARTED, _currentState, AMQPState.CONNECTION_NOT_SECURE);
	    AMQPMethodEvent msg = new AMQPMethodEvent(QpidConstants.CHANNEL_ZERO, connectionStartOkBody, _correlationId);
	    _phase.messageSent(msg);
	    //_connectionNotSecure.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _connectionNotSecure.await();
	    //AMQPValidator.throwExceptionOnNull(_connectionSecureBody, "The broker didn't send the ConnectionSecureBody in time");
	    //_currentState = AMQPState.CONNECTION_NOT_SECURE;
	    
	    checkIfConnectionClosed();
	    if (_connectionTuneBody != null)
	    {
		_currentState = AMQPState.CONNECTION_NOT_TUNED;
		return _connectionTuneBody;
	    }
	    else if (_connectionSecureBody != null)
	    { // oops the server sent another challenge
		_currentState = AMQPState.CONNECTION_NOT_SECURE;
		return _connectionSecureBody;
	    }
	    else
	    {
		throw new AMQPException("The broker didn't send the ConnectionTuneBody or ConnectionSecureBody in time");
	    }	    
	}
	catch (InterruptedException e)
	{
	    throw new AMQPException("Error in connection.startOk", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * The server will verify the response contained in the secureOK body and send a ConnectionTuneBody or it could
     * issue a new challenge
     */
    public AMQMethodBody secureOk(ConnectionSecureOkBody connectionSecureOkBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _connectionTuneBody = null;
	    _connectionSecureBody = null;
	    checkIfValidStateTransition(AMQPState.CONNECTION_NOT_SECURE, _currentState, AMQPState.CONNECTION_NOT_TUNED);

	    _connectionSecureBody = null; // The server could send a fresh challenge
	    AMQPMethodEvent msg = new AMQPMethodEvent(QpidConstants.CHANNEL_ZERO, connectionSecureOkBody, _correlationId);
	    _phase.messageSent(msg);
	    
	    //_connectionNotTuned.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _connectionNotTuned.await();
	    checkIfConnectionClosed();

	    if (_connectionTuneBody != null)
	    {
		_currentState = AMQPState.CONNECTION_NOT_TUNED;
		return _connectionTuneBody;
	    }
	    else if (_connectionSecureBody != null)
	    { // oops the server sent another challenge
		_currentState = AMQPState.CONNECTION_NOT_SECURE;
		return _connectionSecureBody;
	    }
	    else
	    {
		throw new AMQPException("The broker didn't send the ConnectionTuneBody or ConnectionSecureBody in time");
	    }
	}
	catch (InterruptedException e)
	{
	    throw new AMQPException("Error in connection.secureOk", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    public void tuneOk(ConnectionTuneOkBody connectionTuneOkBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    checkIfValidStateTransition(AMQPState.CONNECTION_NOT_TUNED, _currentState, AMQPState.CONNECTION_NOT_OPENED);
	    _connectionSecureBody = null;
	    AMQPMethodEvent msg = new AMQPMethodEvent(QpidConstants.CHANNEL_ZERO, connectionTuneOkBody, _correlationId);
	    _phase.messageSent(msg);
	    _currentState = AMQPState.CONNECTION_NOT_OPENED;
	}
	finally
	{
	    _lock.unlock();
	}
    }

    public ConnectionOpenOkBody open(ConnectionOpenBody connectionOpenBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    // If the broker sends a connection close due to an error with the
	    // Connection tune ok, then this call will verify that
	    checkIfConnectionClosed();
	    
	    _connectionOpenOkBody = null;
	    checkIfValidStateTransition(AMQPState.CONNECTION_NOT_OPENED, _currentState, AMQPState.CONNECTION_OPEN);
	    AMQPMethodEvent msg = new AMQPMethodEvent(QpidConstants.CHANNEL_ZERO, connectionOpenBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    
	    //_connectionNotOpened.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _connectionNotOpened.await();
	    
	    checkIfConnectionClosed();
	    AMQPValidator.throwExceptionOnNull(_connectionOpenOkBody, "The broker didn't send the ConnectionOpenOkBody in time");
	    _currentState = AMQPState.CONNECTION_OPEN;
	    return _connectionOpenOkBody;
	}
	catch (InterruptedException e)
	{
	    throw new AMQPException("Error in connection.open", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    public ConnectionCloseOkBody close(ConnectionCloseBody connectioncloseBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _connectionCloseOkBody = null;
	    checkIfValidStateTransition(_validCloseStates, _currentState, AMQPState.CONNECTION_CLOSED);
	    AMQPMethodEvent msg = new AMQPMethodEvent(QpidConstants.CHANNEL_ZERO, connectioncloseBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    _connectionNotClosed.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    AMQPValidator.throwExceptionOnNull(_connectionCloseOkBody, "The broker didn't send the ConnectionCloseOkBody in time");
	    _currentState = AMQPState.CONNECTION_CLOSED;
	    return _connectionCloseOkBody;
	}
	catch (InterruptedException e)
	{
	    throw new AMQPException("Error in connection.close", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * ------------------------------------------- AMQMethodListener methods
     * --------------------------------------------
     */
    public <B extends AMQMethodBody> boolean methodReceived(AMQPMethodEvent<B> evt) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _correlationId = evt.getCorrelationId();

	    if (evt.getMethod() instanceof ConnectionStartBody)
	    {
		_connectionStartBody = (ConnectionStartBody) evt.getMethod();
		_connectionNotStarted.signalAll();
		return true;
	    }
	    else if (evt.getMethod() instanceof ConnectionSecureBody)
	    {
		_connectionSecureBody = (ConnectionSecureBody) evt.getMethod();
		_connectionNotSecure.signal();
		_connectionNotTuned.signal(); // in case the server has sent another chanllenge
		return true;
	    }
	    else if (evt.getMethod() instanceof ConnectionTuneBody)
	    {
		_connectionTuneBody = (ConnectionTuneBody) evt.getMethod();
		_connectionNotSecure.signal(); //if the server does the auth with ConntectionStartOk 
		_connectionNotTuned.signal();
		return true;
	    }
	    else if (evt.getMethod() instanceof ConnectionOpenOkBody)
	    {
		_connectionOpenOkBody = (ConnectionOpenOkBody) evt.getMethod();
		_connectionNotOpened.signal();
		return true;
	    }
	    else if (evt.getMethod() instanceof ConnectionCloseOkBody)
	    {
		_connectionCloseOkBody = (ConnectionCloseOkBody) evt.getMethod();
		_connectionNotClosed.signal();
		return true;
	    }
	    else if (evt.getMethod() instanceof ConnectionCloseBody)
	    {
		_connectionCloseBody = (ConnectionCloseBody)evt.getMethod();
		// release the correct lock as u may have some conditions waiting.
		// while an error occured and the broker has sent a close.
		releaseLocks();
		handleClose();
		return true;
	    }
	    else
	    {
		return false;
	    }
	}
	finally
	{
	    _lock.unlock();
	}
    }

    private void handleClose() throws AMQPException
    {
	try
	{	    
	    _currentState = AMQPState.CONNECTION_CLOSING;
	    // do the required cleanup and send a ConnectionCloseOkBody
	}
	catch (Exception e)
	{
	    throw new AMQPException("Error handling connection.close from broker", e);
	}
    }
    
    private void checkIfConnectionClosed()throws AMQPException
    {
	if (_connectionCloseBody != null)
	{
	    String error = "Broker has closed connection due to : " + _connectionCloseBody.getReplyText() +
	                    " with reply code (" + _connectionCloseBody.getReplyCode() + ") " +
	                    "caused by class " + _connectionCloseBody.getClassId() +
	                    " and method " + _connectionCloseBody.getMethod();
	    
	    throw new AMQPException(error);	    
	}
    }    
    
    private void releaseLocks()
    {
	if(_currentState == AMQPState.CONNECTION_NOT_OPENED)
	{
	    _connectionNotOpened.signal();
	}
	else if(_currentState == AMQPState.CONNECTION_UNDEFINED)
	{
	    _connectionNotStarted.signal();
	}
	else if(_currentState == AMQPState.CONNECTION_NOT_STARTED)
	{
	    _connectionNotSecure.signal();
	}
	else if(_currentState == AMQPState.CONNECTION_NOT_SECURE)
	{
	    _connectionNotTuned.signal();
	}
    }

    public Phase getPhasePipe()
    {
	return _phase;
    }

}