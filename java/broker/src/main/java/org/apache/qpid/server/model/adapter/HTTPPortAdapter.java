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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.VirtualHostAlias;

public class HTTPPortAdapter extends AbstractAdapter implements Port
{
    private final BrokerAdapter _broker;
    private int _port;
    public HTTPPortAdapter(BrokerAdapter brokerAdapter, int port)
    {
        _broker = brokerAdapter;
        _port = port;

    }

    @Override
    public String getBindingAddress()
    {
        return "0.0.0.0";
    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public Collection<Transport> getTransports()
    {
        return Collections.singleton(Transport.TCP);        
    }

    @Override
    public void addTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException(); // TODO - Implement
    }

    @Override
    public Transport removeTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();   // TODO - Implement
    }

    @Override
    public Collection<Protocol> getProtocols()
    {
        return Collections.singleton(Protocol.HTTP);
    }

    @Override
    public void addProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException(); // TODO - Implement
    }

    @Override
    public Protocol removeProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();   // TODO - Implement
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostBindings()
    {
        return Collections.emptySet();
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return Collections.emptySet();  // TODO - Implement
    }

    @Override
    public String getName()
    {
        return getBindingAddress() + ":" + getPort();  // TODO - Implement
    }

    @Override
    public String setName(String currentName, String desiredName) throws IllegalStateException, AccessControlException
    {
        throw new IllegalStateException();  // TODO - Implement
    }

    @Override
    public State getActualState()
    {
        return State.ACTIVE;
    }

    @Override
    public boolean isDurable()
    {
        return false;  // TODO - Implement
    }

    @Override
    public void setDurable(boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    @Override
    public LifetimePolicy setLifetimePolicy(LifetimePolicy expected, LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();   // TODO - Implement
    }

    @Override
    public long getTimeToLive()
    {
        return 0;  // TODO - Implement
    }

    @Override
    public long setTimeToLive(long expected, long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();  // TODO - Implement
    }

    @Override
    public Statistics getStatistics()
    {
        return NoStatistics.getInstance();
    }

    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public <C extends ConfiguredObject> C createChild(Class<C> childClass, Map<String, Object> attributes, ConfiguredObject... otherParents)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object getAttribute(String name)
    {
        if(ID.equals(name))
        {
            return getId();
        }
        else if(NAME.equals(name))
        {
            return getName();
        }
        else if(STATE.equals(name))
        {
            return getActualState();
        }
        else if(DURABLE.equals(name))
        {
            return isDurable();
        }
        else if(LIFETIME_POLICY.equals(name))
        {
            return getLifetimePolicy();
        }
        else if(TIME_TO_LIVE.equals(name))
        {
            return getTimeToLive();
        }
        else if(CREATED.equals(name))
        {

        }
        else if(UPDATED.equals(name))
        {

        }
        else if(BINDING_ADDRESS.equals(name))
        {
            return getBindingAddress();
        }
        else if(PORT.equals(name))
        {
            return getPort();
        }
        else if(PROTOCOLS.equals(name))
        {
            return getProtocols();
        }
        else if(TRANSPORTS.equals(name))
        {
            return getTransports();
        }

        return super.getAttribute(name);    //TODO - Implement
    }

    @Override
    public Collection<String> getAttributeNames()
    {
        return AVAILABLE_ATTRIBUTES;
    }

    @Override
    public Object setAttribute(String name, Object expected, Object desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        return super.setAttribute(name, expected, desired);    //TODO - Implement
    }
}
