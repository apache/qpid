package org.apache.qpid.nclient.amqp.qpid;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.DtxDemarcationEndBody;
import org.apache.qpid.framing.DtxDemarcationEndOkBody;
import org.apache.qpid.framing.DtxDemarcationSelectBody;
import org.apache.qpid.framing.DtxDemarcationSelectOkBody;
import org.apache.qpid.framing.DtxDemarcationStartBody;
import org.apache.qpid.framing.DtxDemarcationStartOkBody;
import org.apache.qpid.nclient.amqp.AMQPDtxDemarcation;
import org.apache.qpid.nclient.amqp.state.AMQPState;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;

public class QpidAMQPDtxDemarcation implements AMQPDtxDemarcation
{
	private static final Logger _logger = Logger.getLogger(QpidAMQPDtxDemarcation.class);

	// the channelId assigned for this channel
	private int _channelId;

	private Phase _phase;

	private AMQPState _currentState;

	private AMQPStateManager _stateManager;

	private final AMQPState[] _validCloseStates = new AMQPState[]
	{ AMQPState.CHANNEL_OPENED, AMQPState.CHANNEL_SUSPEND };

	private final AMQPState[] _validResumeStates = new AMQPState[]
	{ AMQPState.CHANNEL_CLOSED, AMQPState.CHANNEL_NOT_OPENED };

	// The wait period until a server sends a respond
	private long _serverTimeOut = 1000;

	private final Lock _lock = new ReentrantLock();
	
	
	public DtxDemarcationEndOkBody end(DtxDemarcationEndBody dtxDemarcationEndBody) throws AMQPException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public DtxDemarcationSelectOkBody select(DtxDemarcationSelectBody dtxDemarcationSelectBody) throws AMQPException
	{
		// TODO Auto-generated method stub
		return null;
	}

	public DtxDemarcationStartOkBody start(DtxDemarcationStartBody dtxDemarcationStartBody) throws AMQPException
	{
		// TODO Auto-generated method stub
		return null;
	}

}
