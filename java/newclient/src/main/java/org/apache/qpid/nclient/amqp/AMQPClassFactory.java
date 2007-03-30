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

import org.apache.qpid.nclient.amqp.event.AMQPEventManager;
import org.apache.qpid.nclient.amqp.qpid.QpidAMQPChannel;
import org.apache.qpid.nclient.amqp.qpid.QpidAMQPExchange;
import org.apache.qpid.nclient.amqp.qpid.QpidAMQPMessage;
import org.apache.qpid.nclient.amqp.qpid.QpidAMQPQueue;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.transport.ConnectionURL;
import org.apache.qpid.nclient.transport.TransportConnectionFactory.ConnectionType;
import org.apache.qpid.url.URLSyntaxException;

public interface AMQPClassFactory
{

	public abstract AMQPConnection createConnectionClass(String urlStr, ConnectionType type) throws AMQPException, URLSyntaxException;

	public abstract AMQPConnection createConnectionClass(ConnectionURL url, ConnectionType type) throws AMQPException;

	public abstract AMQPChannel createChannelClass(int channel) throws AMQPException;

	public abstract void destroyChannelClass(int channel, QpidAMQPChannel amqpChannel) throws AMQPException;

	public abstract AMQPExchange createExchangeClass(int channel) throws AMQPException;

	public abstract void destoryExchangeClass(int channel, QpidAMQPExchange amqpExchange) throws AMQPException;

	public abstract AMQPQueue createQueueClass(int channel) throws AMQPException;

	public abstract void destroyQueueClass(int channel, QpidAMQPQueue amqpQueue) throws AMQPException;

	public abstract AMQPMessage createMessageClass(int channel, AMQPMessageCallBack messageCb) throws AMQPException;

	public abstract void destoryMessageClass(int channel, QpidAMQPMessage amqpMessage) throws AMQPException;

	/**
	 * Extention point
	 * Other interested parties can obtain a reference to the event manager
	 * and add listeners to get notified of events
	 * 
	 */
	public abstract AMQPEventManager getEventManager();

	/**
	 * Extention point
	 * Other interested parties can obtain a reference to the state manager
	 * and add listeners to get notified of state changes
	 * 
	 */
	public abstract AMQPStateManager getStateManager();

}