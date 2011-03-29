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
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChannelToSessionMap
{
    private final AMQSession[] _fastAccessSessions = new AMQSession[16];
    private final LinkedHashMap<Integer, AMQSession> _slowAccessSessions = new LinkedHashMap<Integer, AMQSession>();
    private int _size = 0;
    private static final int FAST_CHANNEL_ACCESS_MASK = 0xFFFFFFF0;
    private AtomicInteger _idFactory = new AtomicInteger(0);
    private int _maxChannelID;
    private int _minChannelID;

    public AMQSession get(int channelId)
    {
        if ((channelId & FAST_CHANNEL_ACCESS_MASK) == 0)
        {
            return _fastAccessSessions[channelId];
        }
        else
        {
            return _slowAccessSessions.get(channelId);
        }
    }

    public AMQSession put(int channelId, AMQSession session)
    {
        AMQSession oldVal;
        if ((channelId & FAST_CHANNEL_ACCESS_MASK) == 0)
        {
            oldVal = _fastAccessSessions[channelId];
            _fastAccessSessions[channelId] = session;
        }
        else
        {
            oldVal = _slowAccessSessions.put(channelId, session);
        }
        if ((oldVal != null) && (session == null))
        {
            _size--;
        }
        else if ((oldVal == null) && (session != null))
        {
            _size++;
        }

        return session;

    }

    public AMQSession remove(int channelId)
    {
        AMQSession session;
        if ((channelId & FAST_CHANNEL_ACCESS_MASK) == 0)
        {
            session = _fastAccessSessions[channelId];
            _fastAccessSessions[channelId] = null;
        }
        else
        {
            session = _slowAccessSessions.remove(channelId);
        }

        if (session != null)
        {
            _size--;
        }
        return session;

    }

    public Collection<AMQSession> values()
    {
        ArrayList<AMQSession> values = new ArrayList<AMQSession>(size());

        for (int i = 0; i < 16; i++)
        {
            if (_fastAccessSessions[i] != null)
            {
                values.add(_fastAccessSessions[i]);
            }
        }
        values.addAll(_slowAccessSessions.values());

        return values;
    }

    public int size()
    {
        return _size;
    }

    public void clear()
    {
        _size = 0;
        _slowAccessSessions.clear();
        for (int i = 0; i < 16; i++)
        {
            _fastAccessSessions[i] = null;
        }
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
            if ((id & FAST_CHANNEL_ACCESS_MASK) == 0)
            {
                done = (_fastAccessSessions[id] == null);
            }
            else
            {
                done = (!_slowAccessSessions.keySet().contains(id));
            }
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