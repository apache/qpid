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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;

import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.server.plugin.ProtocolEngineCreatorComparator;
import org.apache.qpid.server.plugin.QpidServiceLoader;

public class MultiVersionProtocolEngineFactory implements ProtocolEngineFactory
{
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final Broker _broker;
    private final Set<Protocol> _supported;
    private final Protocol _defaultSupportedReply;
    private final SSLContext _sslContext;
    private final boolean _wantClientAuth;
    private final boolean _needClientAuth;
    private final Port _port;
    private final Transport _transport;
    private final ProtocolEngineCreator[] _creators;

    public MultiVersionProtocolEngineFactory(Broker<?> broker,
                                             SSLContext sslContext,
                                             boolean wantClientAuth,
                                             boolean needClientAuth,
                                             final Set<Protocol> supportedVersions,
                                             final Protocol defaultSupportedReply,
                                             Port port,
                                             Transport transport)
    {
        if(defaultSupportedReply != null && !supportedVersions.contains(defaultSupportedReply))
        {
            throw new IllegalArgumentException("The configured default reply (" + defaultSupportedReply
                                             + ") to an unsupported protocol version initiation is itself not supported!");
        }

        _broker = broker;
        _sslContext = sslContext;
        _supported = supportedVersions;
        _defaultSupportedReply = defaultSupportedReply;
        final List<ProtocolEngineCreator> creators = new ArrayList<ProtocolEngineCreator>();
        for(ProtocolEngineCreator c : new QpidServiceLoader().instancesOf(ProtocolEngineCreator.class))
        {
            creators.add(c);
        }
        Collections.sort(creators, new ProtocolEngineCreatorComparator());
        _creators = creators.toArray(new ProtocolEngineCreator[creators.size()]);
        _wantClientAuth = wantClientAuth;
        _needClientAuth = needClientAuth;
        _port = port;
        _transport = transport;
    }

    public ServerProtocolEngine newProtocolEngine()
    {
        return new MultiVersionProtocolEngine(_broker, _sslContext, _wantClientAuth, _needClientAuth,
                                              _supported, _defaultSupportedReply, _port, _transport,
                                              ID_GENERATOR.getAndIncrement(),
                                              _creators);
    }
}
