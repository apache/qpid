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
package org.apache.qpid.server.transport;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.plugin.TransportProviderFactory;

@PluggableService
public class TCPandSSLTransportProviderFactory implements TransportProviderFactory
{

    private static final String TYPE = "TCPandSSL";

    @Override
    public Set<Set<Transport>> getSupportedTransports()
    {
        return new HashSet<Set<Transport>>(Arrays.asList(EnumSet.of(Transport.TCP),
                                                         EnumSet.of(Transport.SSL),
                                                         EnumSet.of(Transport.TCP,Transport.SSL)));
    }

    @Override
    public TransportProvider getTransportProvider(final Set<Transport> transports)
    {
        return new TCPandSSLTransportProvider();
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
