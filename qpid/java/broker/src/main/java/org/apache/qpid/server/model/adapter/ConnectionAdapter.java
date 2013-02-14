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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Session;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.stats.StatisticsGatherer;

final class ConnectionAdapter extends AbstractAdapter implements Connection
{
    private AMQConnectionModel _connection;

    private final Map<AMQSessionModel, SessionAdapter> _sessionAdapters =
            new HashMap<AMQSessionModel, SessionAdapter>();
    private final Statistics _statistics;

    public ConnectionAdapter(final AMQConnectionModel conn, TaskExecutor taskExecutor)
    {
        super(UUIDGenerator.generateRandomUUID(), taskExecutor);
        _connection = conn;
        _statistics = new ConnectionStatisticsAdapter(conn);
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
                    _sessionAdapters.put(session, new SessionAdapter(session, getTaskExecutor()));
                }
            }
            return new ArrayList<Session>(_sessionAdapters.values());
        }
    }

    public void delete()
    {
        try
        {
            _connection.close(AMQConstant.CONNECTION_FORCED, "Connection closed by external action");
        }
        catch(AMQException e)
        {
            throw new IllegalStateException(e);
        }
    }

    public String getName()
    {
        final String remoteAddressString = _connection.getRemoteAddressString();
        return remoteAddressString.replaceAll("/","");
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

    @Override
    public Object getAttribute(String name)
    {

        if(name.equals(ID))
        {
            return getId();
        }
        else if (name.equals(NAME))
        {
            return getName();
        }
        else if(name.equals(CLIENT_ID))
        {
            return _connection.getClientId();
        }
        else if(name.equals(CLIENT_VERSION))
        {
            return _connection.getClientVersion();
        }
        else if(name.equals(INCOMING))
        {

        }
        else if(name.equals(LOCAL_ADDRESS))
        {

        }
        else if(name.equals(PRINCIPAL))
        {
            return _connection.getPrincipalAsString();
        }
        else if(name.equals(PROPERTIES))
        {

        }
        else if(name.equals(REMOTE_ADDRESS))
        {
            return _connection.getRemoteAddressString();
        }
        else if(name.equals(REMOTE_PROCESS_NAME))
        {

        }
        else if(name.equals(REMOTE_PROCESS_PID))
        {

        }
        else if(name.equals(SESSION_COUNT_LIMIT))
        {
            return _connection.getSessionCountLimit();
        }
        return super.getAttribute(name);
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        final HashSet<String> attrNames = new HashSet<String>(super.getAttributeNames());
        attrNames.addAll(Connection.AVAILABLE_ATTRIBUTES);
        return Collections.unmodifiableCollection(attrNames);
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Session.class)
        {
            return (Collection<C>) getSessions();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public <C extends ConfiguredObject> C addChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        if(childClass == Session.class)
        {
            throw new IllegalStateException();
        }
        else
        {
            throw new IllegalArgumentException("Cannot create a child of class " + childClass.getSimpleName());
        }

    }

    private class ConnectionStatisticsAdapter extends StatisticsAdapter
    {
        public ConnectionStatisticsAdapter(StatisticsGatherer applicationRegistry)
        {
            super(applicationRegistry);
        }

        @Override
        public Collection<String> getStatisticNames()
        {
            return Connection.AVAILABLE_STATISTICS;
        }

        @Override
        public Object getStatistic(String name)
        {
            if(LAST_IO_TIME.equals(name))
            {
                return _connection.getLastIoTime();
            }
            else if(SESSION_COUNT.equals(name))
            {
                return _connection.getSessionModels().size();
            }
            return super.getStatistic(name);
        }
    }

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        // TODO: add state management
        return false;
    }
}
