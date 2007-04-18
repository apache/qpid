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
import org.apache.qpid.nclient.core.QpidConstants;
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

	private final Condition _channelNotClosed = _lock.newCondition();
	
	private DtxDemarcationSelectOkBody _dtxDemarcationSelectOkBody;
	
	protected QpidAMQPDtxDemarcation(int channelId, Phase phase, AMQPStateManager stateManager)
	{
		_channelId = channelId;
		_phase = phase;
		_stateManager = stateManager;
		_currentState = AMQPState.DTX_CHANNEL_NOT_SELECTED;
		_serverTimeOut = ClientConfiguration.get().getLong(QpidConstants.SERVER_TIMEOUT_IN_MILLISECONDS);
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
			AMQPMethodEvent msg = new AMQPMethodEvent(_channelId, _dtxDemarcationSelectOkBody, QpidConstants.EMPTY_CORRELATION_ID);
			_phase.messageSent(msg);

			//_channelNotOpend.await(_serverTimeOut, TimeUnit.MILLISECONDS);
			_dtxNotSelected.await();
			AMQPValidator.throwExceptionOnNull(_dtxDemarcationSelectOkBody, "The broker didn't send the DtxDemarcationSelectOkBody in time");
			notifyState(AMQPState.CHANNEL_OPENED);
			_currentState = AMQPState.CHANNEL_OPENED;
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
		// TODO Auto-generated method stub
		return null;
	}
	
	public DtxDemarcationEndOkBody end(DtxDemarcationEndBody dtxDemarcationEndBody) throws AMQPException
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	/**
	 * ------------------------------------------- 
	 * AMQPMethodListener methods
	 * --------------------------------------------
	 */
	public <B extends AMQMethodBody> boolean methodReceived(AMQPMethodEvent<B> evt) throws AMQPException
	{
		return true;
	}

	private void notifyState(AMQPState newState) throws AMQPException
	{
		_stateManager.notifyStateChanged(new AMQPStateChangedEvent(_currentState, newState,AMQPStateType.CHANNEL_STATE));
	}
}
