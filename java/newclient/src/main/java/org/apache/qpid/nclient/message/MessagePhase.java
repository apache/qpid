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
package org.apache.qpid.nclient.message;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.AbstractPhase;
import org.apache.qpid.nclient.model.ModelPhase;

public class MessagePhase extends AbstractPhase {

	private final BlockingQueue<AMQPApplicationMessage> _queue = new LinkedBlockingQueue<AMQPApplicationMessage>();
	private static final Logger _logger = Logger.getLogger(ModelPhase.class);
	
	public void messageReceived(Object msg) throws AMQPException 
	{
		try 
		{
			_queue.put((AMQPApplicationMessage)msg);
		} 
		catch (InterruptedException e) 
		{
			_logger.error("Error adding message to queue", e);
		}
		super.messageReceived(msg);
	}

	public void messageSent(Object msg) throws AMQPException 
	{		
		super.messageSent(msg);
	}

	public AMQPApplicationMessage getNextMessage()
	{
		return _queue.poll();		
	}
	
	public AMQPApplicationMessage getNextMessage(long timeout, TimeUnit tu) throws InterruptedException
	{
		return _queue.poll(timeout, tu);		
	}
}
