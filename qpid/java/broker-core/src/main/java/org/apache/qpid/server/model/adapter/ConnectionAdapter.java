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
import java.security.Principal;
import java.util.*;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.*;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.protocol.SessionModelListener;

public final class ConnectionAdapter extends AbstractConfiguredObject<ConnectionAdapter> implements Connection<ConnectionAdapter>,
                                                                                             SessionModelListener
{
    private AMQConnectionModel _connection;

    private final Map<AMQSessionModel, SessionAdapter> _sessionAdapters =
            new HashMap<AMQSessionModel, SessionAdapter>();

    @ManagedAttributeField
    private String _remoteAddress;
    @ManagedAttributeField
    private String _localAddress;
    @ManagedAttributeField
    private String _clientId;
    @ManagedAttributeField
    private String _clientVersion;
    @ManagedAttributeField
    private boolean _incoming;
    @ManagedAttributeField
    private Transport _transport;
    @ManagedAttributeField
    private Port _port;
    @ManagedAttributeField
    private String _remoteProcessName;
    @ManagedAttributeField
    private String _remoteProcessPid;

    public ConnectionAdapter(final AMQConnectionModel conn, TaskExecutor taskExecutor)
    {
        super(parentsMap(conn.getVirtualHost()),createAttributes(conn), taskExecutor);
        _connection = conn;
        open();
        conn.addSessionListener(this);
    }

    private static Map<String, Object> createAttributes(final AMQConnectionModel conn)
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(ID, UUID.randomUUID());
        attributes.put(NAME, conn.getRemoteAddressString().replaceAll("/", ""));
        attributes.put(CLIENT_ID, conn.getClientId() );
        attributes.put(CLIENT_VERSION, conn.getClientVersion());
        attributes.put(TRANSPORT, conn.getTransport());
        attributes.put(PORT, conn.getPort());
        attributes.put(INCOMING, true);
        attributes.put(REMOTE_ADDRESS, conn.getRemoteAddressString());
        attributes.put(DURABLE, false);
        return attributes;
    }

    @Override
    public String getClientId()
    {
        return _clientId;
    }

    @Override
    public String getClientVersion()
    {
        return _clientVersion;
    }

    @Override
    public boolean isIncoming()
    {
        return _incoming;
    }

    @Override
    public String getLocalAddress()
    {
        return _localAddress;
    }

    @Override
    public String getPrincipal()
    {
        final Principal authorizedPrincipal = _connection.getAuthorizedPrincipal();
        return authorizedPrincipal == null ? null : authorizedPrincipal.getName();
    }

    @Override
    public String getRemoteAddress()
    {
        return _remoteAddress;
    }

    @Override
    public String getRemoteProcessName()
    {
        return _remoteProcessName;
    }

    @Override
    public String getRemoteProcessPid()
    {
        return _remoteProcessPid;
    }

    @Override
    public long getSessionCountLimit()
    {
        return _connection.getSessionCountLimit();
    }

    @Override
    public Transport getTransport()
    {
        return _transport;
    }

    @Override
    public Port getPort()
    {
        return _port;
    }

    public Collection<Session> getSessions()
    {
        synchronized (_sessionAdapters)
        {
            return new ArrayList<Session>(_sessionAdapters.values());
        }
    }

    /**
     * Retrieve the SessionAdapter instance keyed by the AMQSessionModel from this Connection.
     * @param session the AMQSessionModel used to index the SessionAdapter.
     * @return the requested SessionAdapter.
     */
    SessionAdapter getSessionAdapter(AMQSessionModel session)
    {
        synchronized (_sessionAdapters)
        {
            return _sessionAdapters.get(session);
        }
    }

    public void delete()
    {
        _connection.close(AMQConstant.CONNECTION_FORCED, "Connection closed by external action");
    }

    public State getState()
    {
        return null;  //TODO
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return null;  //TODO
    }

    @Override
    public Object getAttribute(String name)
    {

        if(name.equals(PRINCIPAL))
        {
            final Principal authorizedPrincipal = _connection.getAuthorizedPrincipal();
            return authorizedPrincipal == null ? null : authorizedPrincipal.getName();
        }
        else if(name.equals(PROPERTIES))
        {

        }
        else if(name.equals(SESSION_COUNT_LIMIT))
        {
            return _connection.getSessionCountLimit();
        }
        else if(name.equals(TRANSPORT))
        {
            return String.valueOf(_connection.getTransport());
        }
        else if(name.equals(PORT))
        {
            Port port = _connection.getPort();
            return String.valueOf(port == null ? null : port.getName());
        }
        return super.getAttribute(name);
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

    @Override
    protected boolean setState(State currentState, State desiredState)
    {
        // TODO: add state management
        return false;
    }

    @Override
    public Object setAttribute(final String name, final Object expected, final Object desired) throws IllegalStateException,
            AccessControlException, IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on connection is not supported.");
    }

    @Override
    public void setAttributes(final Map<String, Object> attributes) throws IllegalStateException, AccessControlException,
            IllegalArgumentException
    {
        throw new UnsupportedOperationException("Changing attributes on connection is not supported.");
    }

    @Override
    public long getBytesIn()
    {
        return _connection.getDataReceiptStatistics().getTotal();
    }

    @Override
    public long getBytesOut()
    {
        return _connection.getDataDeliveryStatistics().getTotal();
    }

    @Override
    public long getMessagesIn()
    {
        return _connection.getMessageReceiptStatistics().getTotal();
    }

    @Override
    public long getMessagesOut()
    {
        return _connection.getMessageDeliveryStatistics().getTotal();
    }

    @Override
    public long getLastIoTime()
    {
        return _connection.getLastIoTime();
    }

    @Override
    public int getSessionCount()
    {
        return _connection.getSessionModels().size();
    }

    @Override
    public void sessionAdded(final AMQSessionModel<?, ?> session)
    {
        synchronized (_sessionAdapters)
        {
            if(!_sessionAdapters.containsKey(session))
            {
                SessionAdapter adapter = new SessionAdapter(this, session, getTaskExecutor());
                _sessionAdapters.put(session, adapter);
                childAdded(adapter);
            }
        }
    }

    @Override
    public void sessionRemoved(final AMQSessionModel<?, ?> session)
    {
        synchronized (_sessionAdapters)
        {
            SessionAdapter adapter = _sessionAdapters.remove(session);
            if(adapter != null)
            {
                childRemoved(adapter);
            }
        }
    }
}
