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

import java.net.InetSocketAddress;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.transport.QpidAcceptor;
import org.apache.qpid.server.virtualhost.VirtualHostRegistry;

public class BrokerAdapter extends AbstractAdapter implements Broker, VirtualHostRegistry.RegistryChangeListener
{


    private final IApplicationRegistry _applicationRegistry;
    private String _name;
    private final Map<org.apache.qpid.server.virtualhost.VirtualHost, VirtualHostAdapter> _vhostAdapters =
            new HashMap<org.apache.qpid.server.virtualhost.VirtualHost, VirtualHostAdapter>();
    private final StatisticsAdapter _statistics;
    
    private static final BrokerAdapter INSTANCE = new BrokerAdapter(ApplicationRegistry.getInstance());

    private BrokerAdapter(final IApplicationRegistry instance)
    {
        _applicationRegistry = instance;
        _name = "Broker";
        _statistics = new StatisticsAdapter(instance);

        instance.getVirtualHostRegistry().addRegistryChangeListener(this);
        populateVhosts();
    }

    private void populateVhosts()
    {
        synchronized(_vhostAdapters)
        {
            Collection<org.apache.qpid.server.virtualhost.VirtualHost> actualVhosts =
                    _applicationRegistry.getVirtualHostRegistry().getVirtualHosts();
            for(org.apache.qpid.server.virtualhost.VirtualHost vh : actualVhosts)
            {
                if(!_vhostAdapters.containsKey(vh))
                {
                    _vhostAdapters.put(vh, new VirtualHostAdapter(vh));
                }
            }

        }
    }

    public static Broker getInstance()
    {
        return INSTANCE;
    }

    public Collection<VirtualHost> getVirtualHosts()
    {
        Collection<org.apache.qpid.server.virtualhost.VirtualHost> actualVhosts =
                _applicationRegistry.getVirtualHostRegistry().getVirtualHosts();


        synchronized(_vhostAdapters)
        {
            return new ArrayList<VirtualHost>(_vhostAdapters.values());
        }

    }

    public Collection<Port> getPorts()
    {
        Map<InetSocketAddress, QpidAcceptor> acceptors = _applicationRegistry.getAcceptors();

        return null;  //TODO
    }

    public Collection<AuthenticationProvider> getAuthenticationProviders()
    {
        return null;  //TODO
    }

    public VirtualHost createVirtualHost(final String name,
                                         final State initialState,
                                         final boolean durable,
                                         final LifetimePolicy lifetime,
                                         final long ttl,
                                         final Map<String, Object> attributes)
            throws AccessControlException, IllegalArgumentException
    {
        return null;  //TODO
    }

    public void deleteVirtualHost(final VirtualHost vhost)
        throws AccessControlException, IllegalStateException
    {
        //TODO
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String getName()
    {
        return _name;
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
        return true;
    }

    public void setDurable(final boolean durable)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public LifetimePolicy getLifetimePolicy()
    {
        return LifetimePolicy.PERMANENT;
    }

    public LifetimePolicy setLifetimePolicy(final LifetimePolicy expected, final LifetimePolicy desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public long getTimeToLive()
    {
        return 0;
    }

    public long setTimeToLive(final long expected, final long desired)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    public Statistics getStatistics()
    {
        return _statistics;
    }


    public void virtualHostRegistered(org.apache.qpid.server.virtualhost.VirtualHost virtualHost)
    {
        VirtualHostAdapter adapter = null;
        synchronized (_vhostAdapters)
        {
            if(!_vhostAdapters.containsKey(virtualHost))
            {
                adapter = new VirtualHostAdapter(virtualHost);
                _vhostAdapters.put(virtualHost, adapter);
            }
        }
        if(adapter != null)
        {
            childAdded(adapter);
        }
    }

    public void virtualHostUnregistered(org.apache.qpid.server.virtualhost.VirtualHost virtualHost)
    {
        VirtualHostAdapter adapter = null;

        synchronized (_vhostAdapters)
        {
            adapter = _vhostAdapters.remove(virtualHost);
        }
        if(adapter != null)
        {
            childRemoved(adapter);
        }
    }
}
