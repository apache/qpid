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

import org.apache.qpid.server.protocol.AmqpProtocolVersion;
import org.apache.qpid.transport.network.NetworkTransport;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class QpidAcceptor
{
    public enum Transport
    {
        TCP("TCP"),
        SSL("TCP/SSL");

        private final String _asString;

        Transport(String asString)
        {
            _asString = asString;
        }

        public String toString()
        {
            return _asString;
        }
    }

    private NetworkTransport _networkTransport;
    private Transport _transport;
    private Set<AmqpProtocolVersion> _supported;


    public QpidAcceptor(NetworkTransport transport, Transport protocol, Set<AmqpProtocolVersion> supported)
    {
        _networkTransport = transport;
        _transport = protocol;
        _supported = Collections.unmodifiableSet(new HashSet<AmqpProtocolVersion>(supported));
    }

    public NetworkTransport getNetworkTransport()
    {
        return _networkTransport;
    }

    public Transport getTransport()
    {
        return _transport;
    }

    public Set<AmqpProtocolVersion> getSupported()
    {
        return _supported;
    }

    public String toString()
    {
        return _transport.toString();
    }    
}
