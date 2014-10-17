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
package org.apache.qpid.server.virtualhostalias;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.HostNameAlias;
import org.apache.qpid.server.model.ManagedObjectFactoryConstructor;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.model.port.AmqpPort;

public class HostNameAliasImpl
        extends AbstractFixedVirtualHostNodeAlias<HostNameAliasImpl>
        implements HostNameAlias<HostNameAliasImpl>
{

    private final Set<InetAddress> _localAddresses = new CopyOnWriteArraySet<>();
    private final Set<String> _localAddressNames = new CopyOnWriteArraySet<>();
    private final Lock _addressLock = new ReentrantLock();
    private final AtomicBoolean _addressesComputed = new AtomicBoolean();


    @ManagedObjectFactoryConstructor
    protected HostNameAliasImpl(final Map<String, Object> attributes, final Port port)
    {
        super(attributes, port);
    }

    protected void onOpen()
    {
        super.onOpen();
        Thread thread = new Thread(new NetworkAddressResolver(), "Network Address Resolver");
        thread.start();
    }


    @Override
    public VirtualHostNode<?> getVirtualHostNode(final String name)
    {
        VirtualHostNode<?> node = null;
        if(matches(name))
        {
            node = getVirtualHostNode();
            if(node == null)
            {
                Broker<?> broker = getPort().getParent(Broker.class);
                String defaultHostName = broker.getDefaultVirtualHost();
                for (VirtualHostNode<?> vhn : broker.getVirtualHostNodes())
                {
                    if (vhn.getName().equals(defaultHostName))
                    {
                        return vhn;
                    }
                }
            }

        }
        return node;
    }

    @Override
    protected boolean matches(final String host)
    {
        while(!_addressesComputed.get())
        {
            Lock lock = _addressLock;
            lock.lock();
            lock.unlock();
        }

        boolean isNetworkAddress = true;
        if (!_localAddressNames.contains(host))
        {
            try
            {
                InetAddress inetAddress = InetAddress.getByName(host);
                if (!_localAddresses.contains(inetAddress))
                {
                    isNetworkAddress = false;
                }
                else
                {
                    _localAddressNames.add(host);
                }
            }
            catch (UnknownHostException e)
            {
                // ignore
                isNetworkAddress = false;
            }
        }
        return isNetworkAddress;

    }

    private class NetworkAddressResolver implements Runnable
    {
        public void run()
        {
            _addressesComputed.set(false);
            Lock lock = _addressLock;

            lock.lock();
            String bindingAddress = ((AmqpPort<?>)getPort()).getBindingAddress();
            try
            {
                Collection<InetAddress> inetAddresses;
                if(bindingAddress == null || bindingAddress.trim().equals("") || bindingAddress.trim().equals("*"))
                {
                    inetAddresses = getAllInetAddresses();
                }
                else
                {
                    inetAddresses = Collections.singleton(InetAddress.getByName(bindingAddress));
                }
                for (InetAddress address : inetAddresses)
                {
                    _localAddresses.add(address);
                    String hostAddress = address.getHostAddress();
                    if (hostAddress != null)
                    {
                        _localAddressNames.add(hostAddress);
                    }
                    String hostName = address.getHostName();
                    if (hostName != null)
                    {
                        _localAddressNames.add(hostName);
                    }
                    String canonicalHostName = address.getCanonicalHostName();
                    if (canonicalHostName != null)
                    {
                        _localAddressNames.add(canonicalHostName);
                    }
                }
            }
            catch (SocketException | UnknownHostException e)
            {
                // ignore
            }
            finally
            {
                _addressesComputed.set(true);
                lock.unlock();
            }
        }

        private Collection<InetAddress> getAllInetAddresses() throws SocketException
        {
            Set<InetAddress> addresses = new HashSet<>();
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces()))
            {
                for (InterfaceAddress inetAddress : networkInterface.getInterfaceAddresses())
                {
                    addresses.add(inetAddress.getAddress());
                }
            }
            return addresses;
        }
    }
}
