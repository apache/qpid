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

import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueueUnbindBody;
import org.apache.qpid.nclient.core.AMQPException;

public interface AMQPQueue
{

	/**
	 * -----------------------------------------------
	 * API Methods
	 * -----------------------------------------------
	 */
	public abstract void declare(QueueDeclareBody queueDeclareBody, AMQPCallBack cb) throws AMQPException;

	public abstract void bind(QueueBindBody queueBindBody, AMQPCallBack cb) throws AMQPException;

	// Queue.unbind doesn't have nowait
	public abstract void unbind(QueueUnbindBody queueUnbindBody, AMQPCallBack cb) throws AMQPException;

	public abstract void purge(QueuePurgeBody queuePurgeBody, AMQPCallBack cb) throws AMQPException;

	public abstract void delete(QueueDeleteBody queueDeleteBody, AMQPCallBack cb) throws AMQPException;

}