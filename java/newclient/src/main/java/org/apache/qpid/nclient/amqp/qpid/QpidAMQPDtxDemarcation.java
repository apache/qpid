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
package org.apache.qpid.nclient.amqp.qpid;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.DtxDemarcationEndBody;
import org.apache.qpid.framing.DtxDemarcationEndOkBody;
import org.apache.qpid.framing.DtxDemarcationSelectBody;
import org.apache.qpid.framing.DtxDemarcationSelectOkBody;
import org.apache.qpid.framing.DtxDemarcationStartBody;
import org.apache.qpid.framing.DtxDemarcationStartOkBody;
import org.apache.qpid.nclient.amqp.AMQPDtxDemarcation;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.amqp.state.AMQPState;
import org.apache.qpid.nclient.amqp.state.AMQPStateChangedEvent;
import org.apache.qpid.nclient.amqp.state.AMQPStateMachine;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.amqp.state.AMQPStateType;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.core.AMQPConstants;
import org.apache.qpid.nclient.util.AMQPValidator;

public class QpidAMQPDtxDemarcation extends AMQPStateMachine implements AMQPMethodListener, AMQPDtxDemarcation
{
	private static final Logger _logger = Logger.getLogger(QpidAMQPDtxDemarcation.class);

	// the channelId that will be used for transactions
	private int _channelId;

	private Phase _phase;

	private AMQPState _currentState;

	private AMQPStateManager _stateManager;

	private final AMQPState[] _validEndStates = new AMQPState[]
	{ AMQPState.DTX_STARTED };

	private final AMQPState[] _validStartStates = new AMQPState[]
	{ AMQPState.DTX_NOT_STARTED, AMQPState.DTX_END };

	// The wait period until a server sends a respond
	private long _serverTimeOut = 1000;

	private final Lock _lock = new ReentrantLock();
	
	private final Condition _dtxNotSelected = _lock.newCondition();

	private final Condition _dtxNotStarted = _lock.newCondition();
	
	// maybe it needs a better name
	private final Condition _dtxNotEnd = _lock.newCondition();
	
	private DtxDemarcationSelectOkBody _dtxDemarcationSelectOkBody;
	
	private DtxDemarcationStartOkBody _dtxDemarcationStartOkBody;
	
	private DtxDemarcationEndOkBody _dtxDemarcationEndOkBody;
	
	protected QpidAMQPDtxDemarcation(int channelId, Phase phase, AMQPStateManager stateManager)
	{
		_channelId = channelId;
		_phase = phase;
		_stateManager = stateManager;
		_currentState = AMQPState.DTX_CHANNEL_NOT_SELECTED;
		_serverTimeOut = ClientConfiguration.get().getLong(AMQPConstants.SERVER_TIMEOUT_IN_MILLISECONDS);
	}	

	/**
	 * ------------------------------------------- 
	 * API Methods
	 *  --------------------------------------------
	 */
	public DtxDemarcationSelectOkBody select(DtxDemarcationSelectBody dtxDemarcationSelectBody) throws AMQPException
	{
		_lock.lock();
		try
		{
			_dtxDemarcationSelectOkBody = null;
			checkIfValidStateTransition(AMQPState.DTX_CHANNEL_NOT_SELECTED, _currentState, AMQPState.DTX_NOT_STARTED);
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, dtxDemarcationSelectBody, AMQPConstants.EMPTY_CORRELATION_ID);
			_phase.messageSent(msg);

			//_dtxNotSelected.await(_serverTimeOut, TimeUnit.MILLISECONDS);
			_dtxNotSelected.await();
			AMQPValidator.throwExceptionOnNull(_dtxDemarcationSelectOkBody, "The broker didn't send the DtxDemarcationSelectOkBody in time");
			notifyState(AMQPState.DTX_NOT_STARTED);
			_currentState = AMQPState.DTX_NOT_STARTED;
			return _dtxDemarcationSelectOkBody;
		}
		catch (Exception e)
		{
			throw new AMQPException("Error in dtx.select", e);
		}
		finally
		{
			_lock.unlock();
		}
	}

	public DtxDemarcationStartOkBody start(DtxDemarcationStartBody dtxDemarcationStartBody) throws AMQPException
	{
		_lock.lock();
		try
		{
			_dtxDemarcationStartOkBody = null;
			checkIfValidStateTransition(_validStartStates, _currentState, AMQPState.DTX_STARTED);
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, _dtxDemarcationStartOkBody, AMQPConstants.EMPTY_CORRELATION_ID);
			_phase.messageSent(msg);

			//_dtxNotStarted.await(_serverTimeOut, TimeUnit.MILLISECONDS);
			_dtxNotStarted.await();
			AMQPValidator.throwExceptionOnNull(_dtxDemarcationStartOkBody, "The broker didn't send the DtxDemarcationStartOkBody in time");
			notifyState(AMQPState.DTX_STARTED);
			_currentState = AMQPState.DTX_STARTED;
			return _dtxDemarcationStartOkBody;
		}
		catch (Exception e)
		{
			throw new AMQPException("Error in dtx.start", e);
		}
		finally
		{
			_lock.unlock();
		}
	}
	
	public DtxDemarcationEndOkBody end(DtxDemarcationEndBody dtxDemarcationEndBody) throws AMQPException
	{
		_lock.lock();
		try
		{
			_dtxDemarcationEndOkBody = null;
			checkIfValidStateTransition(_validEndStates, _currentState, AMQPState.DTX_END);
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, _dtxDemarcationEndOkBody, AMQPConstants.EMPTY_CORRELATION_ID);
			_phase.messageSent(msg);

			//_dtxNotEnd.await(_serverTimeOut, TimeUnit.MILLISECONDS);
			_dtxNotEnd.await();
			AMQPValidator.throwExceptionOnNull(_dtxDemarcationEndOkBody, "The broker didn't send the DtxDemarcationEndOkBody in time");
			notifyState(AMQPState.DTX_END);
			_currentState = AMQPState.DTX_END;
			return _dtxDemarcationEndOkBody;
		}
		catch (Exception e)
		{
			throw new AMQPException("Error in dtx.start", e);
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
			if (evt.getMethod() instanceof DtxDemarcationSelectOkBody)
			{
				_dtxDemarcationEndOkBody = (DtxDemarcationEndOkBody) evt.getMethod();
				_dtxNotSelected.signal();
				return true;
			}
			else if (evt.getMethod() instanceof DtxDemarcationStartOkBody)
			{
				_dtxDemarcationStartOkBody = (DtxDemarcationStartOkBody) evt.getMethod();
				_dtxNotStarted.signal();
				return true;
			}
			else if (evt.getMethod() instanceof DtxDemarcationEndOkBody)
			{
				_dtxDemarcationEndOkBody = (DtxDemarcationEndOkBody) evt.getMethod();
				_dtxNotEnd.signal();
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

	private void notifyState(AMQPState newState) throws AMQPException
	{
		_stateManager.notifyStateChanged(new AMQPStateChangedEvent(_currentState, newState,AMQPStateType.CHANNEL_STATE));
	}
}
