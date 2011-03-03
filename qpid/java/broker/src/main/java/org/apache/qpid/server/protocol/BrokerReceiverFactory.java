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
package org.apache.qpid.server.protocol;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Set;

import org.apache.qpid.protocol.ReceiverFactory;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.NetworkTransport;

public class BrokerReceiverFactory implements ReceiverFactory
{
    public enum VERSION { v0_8, v0_9, v0_9_1, v0_10 };

    private static final Set<VERSION> ALL_VERSIONS = EnumSet.allOf(VERSION.class);

    private final IApplicationRegistry _appRegistry;
    private final String _fqdn;
    private final Set<VERSION> _supported;

    public BrokerReceiverFactory()
    {
        this("localhost", ALL_VERSIONS);
    }

    public BrokerReceiverFactory(String fqdn)
    {
        this(fqdn, ALL_VERSIONS);
    }

    public BrokerReceiverFactory(String fqdn, Set<VERSION> supportedVersions)
    {
        _appRegistry = ApplicationRegistry.getInstance();
        _fqdn = fqdn;
        _supported = supportedVersions;
    }

    public Receiver<ByteBuffer> newReceiver(NetworkTransport transport, NetworkConnection network)
    {
        return new BrokerReceiver(_appRegistry, _fqdn, _supported, transport, network);
    }
}
