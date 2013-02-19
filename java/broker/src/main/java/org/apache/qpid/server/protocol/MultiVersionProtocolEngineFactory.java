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

import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.model.Broker;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class MultiVersionProtocolEngineFactory implements ProtocolEngineFactory
{
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    private final Broker _broker;
    private final Set<AmqpProtocolVersion> _supported;
    private final AmqpProtocolVersion _defaultSupportedReply;

    public MultiVersionProtocolEngineFactory(Broker broker,
            final Set<AmqpProtocolVersion> supportedVersions, final AmqpProtocolVersion defaultSupportedReply)
    {
        if(defaultSupportedReply != null && !supportedVersions.contains(defaultSupportedReply))
        {
            throw new IllegalArgumentException("The configured default reply (" + defaultSupportedReply
                                             + ") to an unsupported protocol version initiation is itself not supported!");
        }

        _broker = broker;
        _supported = supportedVersions;
        _defaultSupportedReply = defaultSupportedReply;
    }

    public ServerProtocolEngine newProtocolEngine()
    {
        return new MultiVersionProtocolEngine(_broker, _supported, _defaultSupportedReply, ID_GENERATOR.getAndIncrement());
    }

}
