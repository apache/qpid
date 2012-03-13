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
package org.apache.qpid.server.model.adapter;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;

final class ConnectionAdapter extends AbstractAdapter implements Connection
{

    private AMQConnectionModel _connection;

    private final Map<AMQSessionModel, SessionAdapter> _sessionAdapters =
            new HashMap<AMQSessionModel, SessionAdapter>();
    private final Statistics _statistics;

    public ConnectionAdapter(final AMQConnectionModel conn)
    {
        _connection = conn;
        _statistics = new StatisticsAdapter(conn);
    }

    public Collection<Session> getSessions()
    {
        List<AMQSessionModel> actualSessions = _connection.getSessionModels();

        synchronized (_sessionAdapters)
        {
            for(AMQSessionModel session : _sessionAdapters.keySet())
            {
                if(!actualSessions.contains(session))
                {
                    _sessionAdapters.remove(session);
                }
            }
            for(AMQSessionModel session : actualSessions)
            {
                if(!_sessionAdapters.containsKey(session))
                {
                    _sessionAdapters.put(session, new SessionAdapter(session));
                }
            }
            return new ArrayList<Session>(_sessionAdapters.values());
        }
    }

    public String getName()
    {
        return _connection.getLogSubject().toLogString();
    }

    public String setName(final String currentName, final String desiredName)
            throws IllegalStateException, AccessControlException
    {
        return null;  //TODO
    }

    public State getActualState()
    {
        return null;  //TODO
    }

    public boolean isDurable()
    {
        return false;  //TODO
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return null;  //TODO
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return null;  //TODO
    }

    public long getTimeToLive()
    {
        return 0;  //TODO
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return 0;  //TODO
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

}
