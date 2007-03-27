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

import org.apache.qpid.framing.MessageAppendBody;
import org.apache.qpid.framing.MessageCancelBody;
import org.apache.qpid.framing.MessageCheckpointBody;
import org.apache.qpid.framing.MessageCloseBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.core.AMQPException;

/**
 * This class also represents the AMQP Message class.
 * You need an instance per channel.
 * This is passed in as an argument in the constructor of an AMQPMessage instance.
 * A client who implements this interface is notified When the broker issues 
 * Message class methods on the client.
 * 
 * A Client should use the AMQPMessage class when it wants to issue Message class
 * methods on the broker.
 *  
 * A JMS MessageConsumer implementation can implement this interface and map
 * AMQP Method notifications to the appropriate JMS methods.
 * 
 * Simillarly a JMS MessageProducer implementation can wrap an AMQPMessage instance.
 *
 */

public interface AMQPMessageCallBack
{
	/**
	 * -----------------------------------------------------------------------
	 * This provides Notifications for broker initiated Message class methods.
	 * All methods have a correlationId that u need to pass into 
	 * the corresponding Message methods when responding to the broker.
	 * 
	 * For example the correlationID passed in from Message.trasnfer
	 * should be passed back when u call Message.ok in AMQPMessage
	 * -----------------------------------------------------------------------
	 */
	
	
	public void transfer(MessageTransferBody messageTransferBody,long correlationId) throws AMQPException;

	public void recover(MessageRecoverBody messageRecoverBody,long correlationId) throws AMQPException;
	
	public void open(MessageOpenBody messageOpenBody,long correlationId) throws AMQPException ;
	
	public void close(MessageCloseBody messageCloseBody,long correlationId) throws AMQPException;

	public void append(MessageAppendBody messageAppendBody,long correlationId) throws AMQPException;

	public void checkpoint(MessageCheckpointBody messageCheckpointBody,long correlationId) throws AMQPException;
	
	public void resume(MessageResumeBody messageResumeBody,long correlationId) throws AMQPException;	
}
