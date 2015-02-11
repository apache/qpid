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
package org.apache.qpid.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChannelToSessionMap
{
    private final Map<Integer, AMQSession> _sessionMap = new ConcurrentHashMap<>();
    private AtomicInteger _idFactory = new AtomicInteger(0);
    private int _maxChannelID;
    private int _minChannelID;

    public AMQSession get(int channelId)
    {
        return _sessionMap.get(channelId);
    }

    public void put(int channelId, AMQSession session)
    {
        _sessionMap.put(channelId, session);
    }

    public void remove(int channelId)
    {
        _sessionMap.remove(channelId);
    }

    public Collection<AMQSession> values()
    {
        return new ArrayList<>(_sessionMap.values());
    }

    public int size()
    {
        return _sessionMap.size();
    }

    public void clear()
    {
        _sessionMap.clear();
    }

    /*
     * Synchronized on whole method so that we don't need to consider the
     * increment-then-reset path in too much detail
     */
    public synchronized int getNextChannelId()
    {
        int id = _minChannelID;

        boolean done = false;
        while (!done)
        {
            id = _idFactory.getAndIncrement();
            if (id == _maxChannelID)
            {
                //go back to the start
                _idFactory.set(_minChannelID);
            }

            done = (!_sessionMap.keySet().contains(id));
        }

        return id;
    }

    public void setMaxChannelID(int maxChannelID)
    {
        _maxChannelID = maxChannelID;
    }

    public void setMinChannelID(int minChannelID)
    {
        _minChannelID = minChannelID;
        _idFactory.set(_minChannelID);
    }
}