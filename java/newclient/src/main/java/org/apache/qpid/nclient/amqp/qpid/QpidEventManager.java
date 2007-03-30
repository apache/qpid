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
package org.apache.qpid.nclient.amqp.qpid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.nclient.amqp.event.AMQPEventManager;
import org.apache.qpid.nclient.amqp.event.AMQPMethodEvent;
import org.apache.qpid.nclient.amqp.event.AMQPMethodListener;
import org.apache.qpid.nclient.core.AMQPException;

/**
 * This class registeres with the ModelPhase as a AMQMethodListener, 
 * to receive method events and then it distributes methods to other listerners
 * using a filtering criteria. The criteria is channel id and method body class.
 * The method listeners are added and removed dynamically
 * 
 * <p/>
 */
public class QpidEventManager implements AMQPEventManager
{
    private static final Logger _logger = Logger.getLogger(QpidEventManager.class);

    private Map<Integer, Map> _channelMap = new ConcurrentHashMap<Integer, Map>();

    /**
     * ------------------------------------------------
     * methods introduced by AMQEventManager
     * ------------------------------------------------
     */
    public void addMethodEventListener(int channelId, Class clazz, AMQPMethodListener l)
    {
	Map<Class, List> _methodListenerMap;
	if (_channelMap.containsKey(channelId))
	{
	    _methodListenerMap = _channelMap.get(channelId);

	}
	else
	{
	    _methodListenerMap = new ConcurrentHashMap<Class, List>();
	    _channelMap.put(channelId, _methodListenerMap);
	}

	List<AMQPMethodListener> _listeners;
	if (_methodListenerMap.containsKey(clazz))
	{
	    _listeners = _methodListenerMap.get(clazz);
	}
	else
	{
	    _listeners = new ArrayList<AMQPMethodListener>();
	    _methodListenerMap.put(clazz, _listeners);
	}

	_listeners.add(l);

    }

    public void removeMethodEventListener(int channelId, Class clazz, AMQPMethodListener l)
    {
	if (_channelMap.containsKey(channelId))
	{
	    Map<Class, List> _methodListenerMap = _channelMap.get(channelId);

	    if (_methodListenerMap.containsKey(clazz))
	    {
		List<AMQPMethodListener> _listeners = _methodListenerMap.get(clazz);
		_listeners.remove(l);
	    }

	}
    }

    /* (non-Javadoc)
     * @see org.apache.qpid.nclient.model.AMQStateManager#methodReceived(org.apache.qpid.protocol.AMQMethodEvent)
     */
    public <B extends AMQMethodBody> boolean notifyEvent(AMQPMethodEvent<B> evt) throws AMQPException
    {
	if (_channelMap.containsKey(evt.getChannelId()))
	{
	    Map<Class, List> _methodListenerMap = _channelMap.get(evt.getChannelId());

	    if (_methodListenerMap.containsKey(evt.getMethod().getClass()))
	    {

		List<AMQPMethodListener> _listeners = _methodListenerMap.get(evt.getMethod().getClass());
		for (AMQPMethodListener l : _listeners)
		{
		    l.methodReceived(evt);
		}

		return (_listeners.size() > 0);
	    }

	}

	return false;
    }
}
