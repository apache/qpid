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
import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageEmptyBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOffsetBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageQosBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageRejectBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.Phase;

/**
 * This class represents the AMQP Message class.
 * You need an instance of this class per channel.
 * A @see AMQPMessageCallBack class is taken as an argument in the constructor.
 * A client can use this class to issue Message class methods on the broker. 
 * When the broker issues Message class methods on the client, the client is notified
 * via the AMQPMessageCallBack interface. 
 *  
 * A JMS Message producer implementation can wrap an instance if this and map
 * JMS method calls to the appropriate AMQP methods.
 *  
 * AMQPMessageCallBack can be implemented by the JMS MessageConsumer implementation.
 *
 */
public class AMQPMessage extends AMQPCallBackSupport implements AMQPMethodListener
{
	private Phase _phase;
	private AMQPMessageCallBack _messageCb;
	
	protected AMQPMessage(int channelId,Phase phase,AMQPMessageCallBack messageCb)
	{
		super(channelId);
		_phase = phase;
		_messageCb = messageCb; 
	}	
	
	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */
	
	public void transfer(MessageTransferBody messageTransferBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageTransferBody,cb);
		_phase.messageSent(msg);
	}

	public void consume(MessageConsumeBody messageConsumeBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageConsumeBody,cb);
		_phase.messageSent(msg);
	}
	
	public void cancel(MessageCancelBody messageCancelBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageCancelBody,cb);
		_phase.messageSent(msg);
	}
	
	public void get(MessageGetBody messageGetBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageGetBody,cb);
		_phase.messageSent(msg);
	}
	
	public void recover(MessageRecoverBody messageRecoverBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageRecoverBody,cb);
		_phase.messageSent(msg);
	}
	
	public void open(MessageOpenBody messageOpenBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageOpenBody,cb);
		_phase.messageSent(msg);
	}
	
	public void close(MessageCloseBody messageCloseBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageCloseBody,cb);
		_phase.messageSent(msg);
	}

	public void append(MessageAppendBody messageAppendBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageAppendBody,cb);
		_phase.messageSent(msg);
	}

	public void checkpoint(MessageCheckpointBody messageCheckpointBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageCheckpointBody,cb);
		_phase.messageSent(msg);
	}
	
	public void resume(MessageResumeBody messageResumeBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageResumeBody,cb);
		_phase.messageSent(msg);
	}
	
	public void qos(MessageQosBody messageQosBody,AMQPCallBack cb) throws AMQPException 
	{
		AMQPMethodEvent msg = handleAsynchronousCall(messageQosBody,cb);
		_phase.messageSent(msg);
	}
	
	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public void ok(MessageOkBody messageOkBody,long correlationId) throws AMQPException 
	{
		AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,messageOkBody,correlationId);
		_phase.messageSent(msg);
	}
	
	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public void reject(MessageRejectBody messageRejectBody,long correlationId) throws AMQPException 
	{
		AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,messageRejectBody,correlationId);
		_phase.messageSent(msg);
	}
	
	/**
	 * The correlationId from the request.
	 * For example if a message.resume is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public void offset(MessageOffsetBody messageOffsetBody,long correlationId) throws AMQPException 
	{
		AMQPMethodEvent msg = new AMQPMethodEvent(_channelId,messageOffsetBody,correlationId);
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
    	if ( methodBody instanceof MessageOkBody     || 
    		 methodBody instanceof MessageRejectBody ||
    		 methodBody instanceof MessageEmptyBody)
    	{
    		invokeCallBack(localCorrelationId,methodBody);
    		return true;
    	}
    	else if (methodBody instanceof MessageTransferBody)
    	{
    		_messageCb.transfer((MessageTransferBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageAppendBody)
    	{
    		_messageCb.append((MessageAppendBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageOpenBody)
    	{
    		_messageCb.open((MessageOpenBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageCloseBody)
    	{
    		_messageCb.close((MessageCloseBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageCheckpointBody)
    	{
    		_messageCb.checkpoint((MessageCheckpointBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageRecoverBody)
    	{
    		_messageCb.recover((MessageRecoverBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else if (methodBody instanceof MessageResumeBody)
    	{
    		_messageCb.resume((MessageResumeBody)methodBody, evt.getCorrelationId());
    		return true;
    	}
    	else
    	{
    		return false;
    	}
    }
}
