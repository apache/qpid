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
package org.apache.qpid.nclient.amqp.state;

public class AMQPStateChangedEvent 
{
	private AMQPState _oldState;
	
	private AMQPState _newState;

	private AMQPStateType _stateType;

	public AMQPStateChangedEvent(AMQPState oldState, AMQPState newState, AMQPStateType stateType)
	{
		_oldState  = oldState;
		_newState  = newState;
		_stateType = stateType; 
	}
	
	public AMQPState getNewState() 
	{
		return _newState;
	}

	public void setNewState(AMQPState newState) 
	{
		this._newState = newState;
	}

	public AMQPState getOldState() 
	{
		return _oldState;
	}

	public void setOldState(AMQPState oldState) 
	{
		this._oldState = oldState;
	}

	public AMQPStateType getStateType() 
	{
		return _stateType;
	}

	public void setStateType(AMQPStateType stateType) 
	{
		this._stateType = stateType;
	}
	
}
