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

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.transport.network.NetworkConnection;

public class MultiVersionProtocolEngineFactory implements ProtocolEngineFactory
{
    private static final Set<AmqpProtocolVersion> ALL_VERSIONS = EnumSet.allOf(AmqpProtocolVersion.class);
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final IApplicationRegistry _appRegistry;
    private final String _fqdn;
    private final Set<AmqpProtocolVersion> _supported;

    public MultiVersionProtocolEngineFactory()
    {
        this("localhost", ALL_VERSIONS);
    }

    public MultiVersionProtocolEngineFactory(String fqdn)
    {
        this(fqdn, ALL_VERSIONS);
    }

    public MultiVersionProtocolEngineFactory(String fqdn, Set<AmqpProtocolVersion> supportedVersions)
    {
        _appRegistry = ApplicationRegistry.getInstance();
        _fqdn = fqdn;
        _supported = supportedVersions;
    }

    public ServerProtocolEngine newProtocolEngine(NetworkConnection network)
    {
        return new MultiVersionProtocolEngine(_appRegistry, _fqdn, _supported, network, ID_GENERATOR.getAndIncrement());
    }
}
