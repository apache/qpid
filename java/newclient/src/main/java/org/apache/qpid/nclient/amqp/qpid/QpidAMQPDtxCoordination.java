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

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.DtxCoordinationCommitBody;
import org.apache.qpid.framing.DtxCoordinationCommitOkBody;
import org.apache.qpid.framing.DtxCoordinationForgetBody;
import org.apache.qpid.framing.DtxCoordinationForgetOkBody;
import org.apache.qpid.framing.DtxCoordinationGetTimeoutBody;
import org.apache.qpid.framing.DtxCoordinationGetTimeoutOkBody;
import org.apache.qpid.framing.DtxCoordinationPrepareBody;
import org.apache.qpid.framing.DtxCoordinationPrepareOkBody;
import org.apache.qpid.framing.DtxCoordinationRecoverBody;
import org.apache.qpid.framing.DtxCoordinationRecoverOkBody;
import org.apache.qpid.framing.DtxCoordinationRollbackBody;
import org.apache.qpid.framing.DtxCoordinationSetTimeoutBody;
import org.apache.qpid.nclient.amqp.AMQPCallBack;
import org.apache.qpid.nclient.amqp.AMQPCallBackSupport;
import org.apache.qpid.nclient.amqp.AMQPDtxCoordination;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;

public class QpidAMQPDtxCoordination extends AMQPCallBackSupport implements AMQPMethodListener, AMQPDtxCoordination
{
	private Phase _phase;

	protected QpidAMQPDtxCoordination(int channelId,Phase phase)
	{
		super(channelId);
		_phase = phase;
	}

	public void commit(DtxCoordinationCommitBody dtxCoordinationCommitBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationCommitBody,cb);
		_phase.messageSent(msg);
	}

	public void forget(DtxCoordinationForgetBody dtxCoordinationForgetBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationForgetBody,cb);
		_phase.messageSent(msg);
	}

	public void getTimeOut(DtxCoordinationGetTimeoutBody dtxCoordinationGetTimeoutBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationGetTimeoutBody,cb);
		_phase.messageSent(msg);
	}

	public void rollback(DtxCoordinationRollbackBody dtxCoordinationRollbackBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationRollbackBody,cb);
		_phase.messageSent(msg);
	}

	public void prepare(DtxCoordinationPrepareBody dtxCoordinationPrepareBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationPrepareBody,cb);
		_phase.messageSent(msg);
	}

	public void recover(DtxCoordinationRecoverBody dtxCoordinationRecoverBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationRecoverBody,cb);
		_phase.messageSent(msg);
	}
	
	public void setTimeOut(DtxCoordinationSetTimeoutBody dtxCoordinationSetTimeoutBody,AMQPCallBack cb) throws AMQPException
	{
		AMQPMethodEvent msg = handleAsynchronousCall(dtxCoordinationSetTimeoutBody,cb);
		_phase.messageSent(msg);
	}
	
	/**-------------------------------------------
     * AMQPMethodListener methods
     *--------------------------------------------
     */
	public <B extends AMQMethodBody> boolean methodReceived(AMQPMethodEvent<B> evt) throws AMQPException
    {
    	long localCorrelationId = evt.getLocalCorrelationId();
    	AMQMethodBody methodBody = evt.getMethod(); 
    	if ( methodBody instanceof DtxCoordinationCommitOkBody ||
    		 methodBody instanceof DtxCoordinationForgetOkBody	  ||
    		 methodBody instanceof DtxCoordinationGetTimeoutOkBody  ||
    		 methodBody instanceof DtxCoordinationPrepareOkBody	  ||
    		 methodBody instanceof DtxCoordinationRecoverOkBody	  
    	    )
    	{
    		invokeCallBack(localCorrelationId,methodBody);
    		return true;
    	}    	
    	else
    	{
    		return false;
    	}
    }	

}
