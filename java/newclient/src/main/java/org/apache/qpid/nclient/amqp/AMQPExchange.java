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

import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.framing.ExchangeDeleteBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;
import org.apache.qpid.nclient.model.AMQPMethodEvent;
import org.apache.qpid.nclient.model.AMQPMethodListener;

/**
 * 
 * This class represents the Exchange class defined in AMQP.
 * Each method takes an @see AMQPCallBack object if it wants to know
 * the response from the broker to particular method. 
 * Clients can handle the reponse asynchronously or block for a response
 * using AMQPCallBack.isComplete() periodically using a loop.
 */
public class AMQPExchange extends AMQPCallBackSupport implements AMQPMethodListener
{
	private Phase _phase;
	
	public AMQPExchange(int channelId,Phase phase)
	{
		super(channelId);
		_phase = phase;
	}
	
	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */
	public void declare(ExchangeDeclareBody exchangeDeclareBody,AMQPCallBack cb) throws AMQPException
	{		
		AMQPMethodEvent msg = handleNoWait(exchangeDeclareBody.nowait,exchangeDeclareBody,cb);
		_phase.messageSent(msg);
	}
	
	public void delete(ExchangeDeleteBody exchangeDeleteBody,AMQPCallBack cb) throws AMQPException
	{	
		AMQPMethodEvent msg = handleNoWait(exchangeDeleteBody.nowait,exchangeDeleteBody,cb);
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
    	if ( methodBody instanceof ExchangeDeclareOkBody || methodBody instanceof ExchangeDeleteOkBody)
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
