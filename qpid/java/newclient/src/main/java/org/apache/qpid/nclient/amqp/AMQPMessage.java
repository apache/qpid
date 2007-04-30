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
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageGetBody;
import org.apache.qpid.framing.MessageOffsetBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageOpenBody;
import org.apache.qpid.framing.MessageQosBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.MessageRejectBody;
import org.apache.qpid.framing.MessageResumeBody;
import org.apache.qpid.framing.MessageTransferBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPMessage
{

	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */

	public abstract void transfer(MessageTransferBody messageTransferBody, AMQPCallBack cb) throws AMQPException;

	public abstract void consume(MessageConsumeBody messageConsumeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void cancel(MessageCancelBody messageCancelBody, AMQPCallBack cb) throws AMQPException;

	public abstract void get(MessageGetBody messageGetBody, AMQPCallBack cb) throws AMQPException;

	public abstract void recover(MessageRecoverBody messageRecoverBody, AMQPCallBack cb) throws AMQPException;

	public abstract void open(MessageOpenBody messageOpenBody, AMQPCallBack cb) throws AMQPException;

	public abstract void close(MessageCloseBody messageCloseBody, AMQPCallBack cb) throws AMQPException;

	public abstract void append(MessageAppendBody messageAppendBody, AMQPCallBack cb) throws AMQPException;

	public abstract void checkpoint(MessageCheckpointBody messageCheckpointBody, AMQPCallBack cb) throws AMQPException;

	public abstract void resume(MessageResumeBody messageResumeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void qos(MessageQosBody messageQosBody, AMQPCallBack cb) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void ok(MessageOkBody messageOkBody, long correlationId) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.transfer is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void reject(MessageRejectBody messageRejectBody, long correlationId) throws AMQPException;

	/**
	 * The correlationId from the request.
	 * For example if a message.resume is sent with correlationId "ABCD"
	 * then u need to pass that in. This correlation id is used by the execution layer
	 * to handle the correlation of method requests and responses
	 */
	public abstract void offset(MessageOffsetBody messageOffsetBody, long correlationId) throws AMQPException;

}