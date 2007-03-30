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
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueBindOkBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueuePurgeOkBody;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.framing.QueueUnbindOkBody;
import org.apache.qpid.nclient.amqp.AMQPCallBack;
import org.apache.qpid.nclient.amqp.AMQPCallBackSupport;
import org.apache.qpid.nclient.amqp.AMQPQueue;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;

/**
 * 
 * This class represents the Queue class defined in AMQP.
 * Each method takes an @see AMQPCallBack object if it wants to know
 * the response from the broker to a particular method. 
 * Clients can handle the reponse asynchronously or block for a response
 * using AMQPCallBack.isComplete() periodically using a loop.
 */
public class QpidAMQPQueue extends AMQPCallBackSupport implements AMQPMethodListener, AMQPQueue
{
	private Phase _phase;

	protected QpidAMQPQueue(int channelId,Phase phase)
	{
		super(channelId);
		_phase = phase;
	}
	
	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPQueue#declare(org.apache.qpid.framing.QueueDeclareBody, org.apache.qpid.nclient.amqp.AMQPCallBack)
	 */
	public void declare(QueueDeclareBody queueDeclareBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleNoWait(queueDeclareBody.nowait,queueDeclareBody,cb);
		_phase.messageSent(msg);
	}
	
	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPQueue#bind(org.apache.qpid.framing.QueueBindBody, org.apache.qpid.nclient.amqp.AMQPCallBack)
	 */
	public void bind(QueueBindBody queueBindBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleNoWait(queueBindBody.nowait,queueBindBody,cb);
		_phase.messageSent(msg);
	}
	
	// Queue.unbind doesn't have nowait
	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPQueue#unbind(org.apache.qpid.framing.QueueUnbindBody, org.apache.qpid.nclient.amqp.AMQPCallBack)
	 */
	public void unbind(QueueUnbindBody queueUnbindBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleAsynchronousCall(queueUnbindBody,cb);
		_phase.messageSent(msg);
	}
	
	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPQueue#purge(org.apache.qpid.framing.QueuePurgeBody, org.apache.qpid.nclient.amqp.AMQPCallBack)
	 */
	public void purge(QueuePurgeBody queuePurgeBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleNoWait(queuePurgeBody.nowait,queuePurgeBody,cb);
		_phase.messageSent(msg);
	}

	/* (non-Javadoc)
	 * @see org.apache.qpid.nclient.amqp.AMQPQueue#delete(org.apache.qpid.framing.QueueDeleteBody, org.apache.qpid.nclient.amqp.AMQPCallBack)
	 */
	public void delete(QueueDeleteBody queueDeleteBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleNoWait(queueDeleteBody.nowait,queueDeleteBody,cb);
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
    	if ( methodBody instanceof QueueDeclareOkBody ||
    		 methodBody instanceof QueueBindOkBody	  ||
    		 methodBody instanceof QueueUnbindOkBody  ||
    		 methodBody instanceof QueuePurgeOkBody	  ||
    		 methodBody instanceof QueueDeleteOkBody	  
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
