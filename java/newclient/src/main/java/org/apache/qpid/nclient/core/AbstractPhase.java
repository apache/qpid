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
package org.apache.qpid.nclient.core;

public abstract class AbstractPhase implements Phase {

	protected PhaseContext _ctx;
	protected Phase _nextInFlowPhase;
	protected Phase _nextOutFlowPhase;	
	
	
	/**
	 * ------------------------------------------------
	 * Phase - method introduced by Phase
	 * ------------------------------------------------
	 */    
	public void init(PhaseContext ctx,Phase nextInFlowPhase, Phase nextOutFlowPhase) {
		_nextInFlowPhase = nextInFlowPhase;
		_nextOutFlowPhase = nextOutFlowPhase; 
		_ctx = ctx;
	}
	
	/**
	 * The start is called from the top
	 * of the pipe and is propogated the
	 * bottom.
	 * 
	 * Each phase can override this to do
	 * any phase specific logic related
	 * pipe.start()
	 */
	public void start()throws AMQPException
	{
	    if(_nextOutFlowPhase != null)
	    {
		_nextOutFlowPhase.start();
	    }
	}
	
	/**
	 * Each phase can override this to do
	 * any phase specific cleanup
	 */
	public void close()throws AMQPException
	{
	    
	}
	
	public void messageReceived(Object frame) throws AMQPException 
	{
		if(_nextInFlowPhase != null)
		{
			_nextInFlowPhase.messageReceived(frame);
		}
	}

	public void messageSent(Object frame) throws AMQPException 
	{		
		if (_nextOutFlowPhase != null)
		{
			_nextOutFlowPhase.messageSent(frame);
		}
	}
	
	public PhaseContext getPhaseContext()
	{
		return _ctx;
	}

	public Phase getNextInFlowPhase() {
		return _nextInFlowPhase;
	}

	public Phase getNextOutFlowPhase() {
		return _nextOutFlowPhase;
	}
	
	
}
