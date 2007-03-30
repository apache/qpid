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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.nclient.amqp.state.AMQPStateChangedEvent;
import org.apache.qpid.nclient.amqp.state.AMQPStateListener;
import org.apache.qpid.nclient.amqp.state.AMQPStateManager;
import org.apache.qpid.nclient.amqp.state.AMQPStateType;
import org.apache.qpid.nclient.core.AMQPException;

public class QpidStateManager implements AMQPStateManager
{

	private static final Logger _logger = Logger.getLogger(QpidStateManager.class);

    private Map<AMQPStateType, List<AMQPStateListener>> _listernerMap = new ConcurrentHashMap<AMQPStateType, List<AMQPStateListener>>();

    public void addListener(AMQPStateType stateType, AMQPStateListener l) throws AMQException
    {
    	List<AMQPStateListener> list;
    	if(_listernerMap.containsKey(stateType))
    	{
    		list = _listernerMap.get(stateType);
    	}
    	else
    	{
    		list = new ArrayList<AMQPStateListener>();
    		_listernerMap.put(stateType, list);
    	}
    	list.add(l);
    }

    public void removeListener(AMQPStateType stateType, AMQPStateListener l) throws AMQException
    {    	
    	if(_listernerMap.containsKey(stateType))
    	{
    		List<AMQPStateListener> list = _listernerMap.get(stateType);
    		list.remove(l);
    	}
    }
    
    public void notifyStateChanged(AMQPStateChangedEvent event) throws AMQPException
    {
     	
    	if(_listernerMap.containsKey(event.getStateType()))
    	{
    		List<AMQPStateListener> list = _listernerMap.get(event.getStateType());
    		for(AMQPStateListener l: list)
    		{
    			l.stateChanged(event);
    		}
    	}
    	else
    	{
    		_logger.warn("There are no registered listerners for state type" + event.getStateType());
    	}
    }

}
