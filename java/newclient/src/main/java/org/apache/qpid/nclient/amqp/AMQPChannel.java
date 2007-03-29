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
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ChannelFlowOkBody;
import org.apache.qpid.framing.ChannelOkBody;
import org.apache.qpid.framing.ChannelOpenBody;
import org.apache.qpid.framing.ChannelOpenOkBody;
import org.apache.qpid.framing.ChannelResumeBody;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.amqp.state.AMQPState;
import org.apache.qpid.nclient.amqp.state.AMQPStateMachine;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.QpidConstants;
import org.apache.qpid.nclient.util.AMQPValidator;

/**
 * This represents the Channel class defined in the AMQP protocol. This class is a finite state machine and is thread
 * safe by design. Only valid state changes are allowed or else an IllegalStateTransitionException will be thrown. Only
 * one thread can enter the methods that change state, at a given time. The AMQP protocol recommends one thread per
 * channel by design.
 * 
 * A JMS Session can wrap an instance of this class.
 */

public class AMQPChannel extends AMQPStateMachine implements AMQPMethodListener
{
    private static final Logger _logger = Logger.getLogger(AMQPChannel.class);

    // the channelId assigned for this channel
    private int _channelId;

    private Phase _phase;

    private AMQPState _currentState;

    private final AMQPState[] _validCloseStates = new AMQPState[] { AMQPState.CHANNEL_OPENED, AMQPState.CHANNEL_SUSPEND };

    private final AMQPState[] _validResumeStates = new AMQPState[] { AMQPState.CHANNEL_CLOSED, AMQPState.CHANNEL_NOT_OPENED };

    // The wait period until a server sends a respond
    private long _serverTimeOut = 1000;

    private final Lock _lock = new ReentrantLock();

    private final Condition _channelNotOpend = _lock.newCondition();

    private final Condition _channelNotClosed = _lock.newCondition();

    private final Condition _channelFlowNotResponded = _lock.newCondition();

    private final Condition _channelNotResumed = _lock.newCondition();

    private ChannelOpenOkBody _channelOpenOkBody;

    private ChannelCloseOkBody _channelCloseOkBody;

    private ChannelFlowOkBody _channelFlowOkBody;

    private ChannelOkBody _channelOkBody;
    
    private ChannelCloseBody _channelCloseBody;

    protected AMQPChannel(int channelId, Phase phase)
    {
	_channelId = channelId;
	_phase = phase;
	_currentState = AMQPState.CHANNEL_NOT_OPENED;
	_serverTimeOut = ClientConfiguration.get().getLong(QpidConstants.SERVER_TIMEOUT_IN_MILLISECONDS);
    }

    /**
     * ------------------------------------------- 
     * API Methods
     *  --------------------------------------------
     */

    /**
     * Opens the channel
     */
    public ChannelOpenOkBody open(ChannelOpenBody channelOpenBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _channelOpenOkBody = null;
	    checkIfValidStateTransition(AMQPState.CHANNEL_NOT_OPENED, _currentState, AMQPState.CHANNEL_OPENED);
	    AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, channelOpenBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    
	    //_channelNotOpend.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _channelNotOpend.await();
	    checkIfConnectionClosed();
	    AMQPValidator.throwExceptionOnNull(_channelOpenOkBody, "The broker didn't send the ChannelOpenOkBody in time");
	    _currentState = AMQPState.CHANNEL_OPENED;
	    return _channelOpenOkBody;
	}
	catch (Exception e)
	{
	    throw new AMQPException("Error in channel.open", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * Close the channel
     */
    public ChannelCloseOkBody close(ChannelCloseBody channelCloseBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _channelCloseOkBody = null;
	    checkIfValidStateTransition(_validCloseStates, _currentState, AMQPState.CHANNEL_CLOSED);
	    AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, channelCloseBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    
	    //_channelNotClosed.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _channelNotClosed.await();
	    AMQPValidator.throwExceptionOnNull(_channelCloseOkBody, "The broker didn't send the ChannelCloseOkBody in time");
	    _currentState = AMQPState.CHANNEL_CLOSED;
	    return _channelCloseOkBody;
	}
	catch (Exception e)
	{
	    throw new AMQPException("Error in channel.close", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * Channel Flow
     */
    public ChannelFlowOkBody flow(ChannelFlowBody channelFlowBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _channelFlowOkBody = null;
	    if (channelFlowBody.active)
	    {
		checkIfValidStateTransition(AMQPState.CHANNEL_SUSPEND, _currentState, AMQPState.CHANNEL_OPENED);
	    }
	    else
	    {
		checkIfValidStateTransition(AMQPState.CHANNEL_OPENED, _currentState, AMQPState.CHANNEL_SUSPEND);
	    }
	    AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, channelFlowBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    
	    //_channelFlowNotResponded.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _channelFlowNotResponded.await();
	    checkIfConnectionClosed();
	    AMQPValidator.throwExceptionOnNull(_channelFlowOkBody, "The broker didn't send the ChannelFlowOkBody in time");
	    handleChannelFlowState(_channelFlowOkBody.active);
	    return _channelFlowOkBody;
	}
	catch (Exception e)
	{
	    throw new AMQPException("Error in channel.flow", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * Close the channel
     */
    public ChannelOkBody resume(ChannelResumeBody channelResumeBody) throws AMQPException
    {
	_lock.lock();
	try
	{
	    _channelOkBody = null;
	    checkIfValidStateTransition(_validResumeStates, _currentState, AMQPState.CHANNEL_OPENED);
	    AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, channelResumeBody, QpidConstants.EMPTY_CORRELATION_ID);
	    _phase.messageSent(msg);
	    
	    //_channelNotResumed.await(_serverTimeOut, TimeUnit.MILLISECONDS);
	    _channelNotResumed.await();
	    checkIfConnectionClosed();
	    AMQPValidator.throwExceptionOnNull(_channelOkBody,
		    "The broker didn't send the ChannelOkBody in response to the ChannelResumeBody in time");
	    _currentState = AMQPState.CHANNEL_OPENED;
	    return _channelOkBody;
	}
	catch (Exception e)
	{
	    throw new AMQPException("Error in channel.resume", e);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    /**
     * ------------------------------------------- 
     * AMQPMethodListener methods
     * --------------------------------------------
     */
    public <B extends AMQMethodBody> boolean methodReceived(AMQPMethodEvent<B> evt) throws AMQPException
    {
	_lock.lock();
	try
	{
        	if (evt.getMethod() instanceof ChannelOpenOkBody)
        	{
        	    _channelOpenOkBody = (ChannelOpenOkBody) evt.getMethod();
        	    _channelNotOpend.signal();
        	    return true;
        	}
        	else if (evt.getMethod() instanceof ChannelCloseOkBody)
        	{
        	    _channelCloseOkBody = (ChannelCloseOkBody) evt.getMethod();
        	    _channelNotClosed.signal();
        	    return true;
        	}
        	else if (evt.getMethod() instanceof ChannelCloseBody)
        	{
        	    _channelCloseBody = (ChannelCloseBody)evt.getMethod();
    		    // release the correct lock as u may have some conditions waiting.
    		   // while an error occured and the broker has sent a close.
    		    releaseLocks();
        	    handleChannelClose(_channelCloseBody);
        	    return true;
        	}
        	else if (evt.getMethod() instanceof ChannelFlowOkBody)
        	{
        	    _channelFlowOkBody = (ChannelFlowOkBody) evt.getMethod();
        	    _channelFlowNotResponded.signal();
        	    return true;
        	}
        	else if (evt.getMethod() instanceof ChannelFlowBody)
        	{
        	    handleChannelFlow((ChannelFlowBody) evt.getMethod());
        	    return true;
        	}
        	else if (evt.getMethod() instanceof ChannelOkBody)
        	{
        	    _channelOkBody = (ChannelOkBody) evt.getMethod();
        	    // In this case the only method expecting channel-ok is channel-resume
        	    // haven't implemented ping and pong.
        	    _channelNotResumed.signal();
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

    private void handleChannelClose(ChannelCloseBody channelCloseBody)
    {
	_currentState = AMQPState.CHANNEL_CLOSED;
	// handle channel related cleanup
    }
    
    private void releaseLocks()
    {
	if(_currentState == AMQPState.CHANNEL_NOT_OPENED)
	{
	    _channelNotOpend.signal();
	    _channelNotResumed.signal(); // It could be a channel.resume call
	}
	else if(_currentState == AMQPState.CHANNEL_OPENED || _currentState == AMQPState.CHANNEL_SUSPEND)
	{
	    _channelFlowNotResponded.signal();
	}
	else if(_currentState == AMQPState.CHANNEL_CLOSED)
	{
	    _channelNotResumed.signal();
	}
    }
    
    private void checkIfConnectionClosed()throws AMQPException
    {
	if (_channelCloseBody != null)
	{
	    String error = "Broker has closed channel due to : " + _channelCloseBody.getReplyText() +
	                    " with reply code (" + _channelCloseBody.getReplyCode() + ") " +
	                    "caused by class " + _channelCloseBody.getClassId() +
	                    " and method " + _channelCloseBody.getMethod();
	    
	    throw new AMQPException(error);	    
	}
    }    

    private void handleChannelFlow(ChannelFlowBody channelFlowBody)
    {
	_lock.lock();
	try
	{
	    handleChannelFlowState(channelFlowBody.active);
	}
	finally
	{
	    _lock.unlock();
	}
    }

    private void handleChannelFlowState(boolean flow)
    {
	_currentState = (flow) ? AMQPState.CHANNEL_OPENED : AMQPState.CHANNEL_SUSPEND;
    }
}
