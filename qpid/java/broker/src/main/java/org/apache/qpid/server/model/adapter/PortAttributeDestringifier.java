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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.util.MapValueConverter;

public class PortAttributeDestringifier
{
    private static final int DEFAULT_BUFFER_SIZE = 262144;
    private static final boolean DEFAULT_TCP_NO_DELAY = true;
    private static final boolean DEFAULT_WANT_CLIENT_AUTH = false;
    private static final boolean DEFAULT_NEED_CLIENT_AUTH = false;

    // TODO : implement a generic functionality to convert string attribute  values into corresponding java types
    public Map<String, Object> destringify(Map<String, Object> attributes)
    {
        Map<String, Object> destringifiedAttributes = new HashMap<String, Object>(attributes);

        final Set<Protocol> protocolSet = MapValueConverter.getEnumSetAttribute(Port.PROTOCOLS, attributes, Protocol.class);
        destringifiedAttributes.put(Port.PROTOCOLS, protocolSet);

        final Set<Transport> transportSet = MapValueConverter.getEnumSetAttribute(Port.TRANSPORTS, attributes, Transport.class);
        destringifiedAttributes.put(Port.TRANSPORTS, transportSet);

        Integer port = MapValueConverter.getIntegerAttribute(Port.PORT, attributes);
        destringifiedAttributes.put(Port.PORT, port);

        boolean tcpNoDelay = MapValueConverter.getBooleanAttribute(Port.TCP_NO_DELAY, attributes, DEFAULT_TCP_NO_DELAY);
        int receiveBufferSize = MapValueConverter.getIntegerAttribute(Port.RECEIVE_BUFFER_SIZE, attributes,
                DEFAULT_BUFFER_SIZE);
        int sendBufferSize = MapValueConverter.getIntegerAttribute(Port.SEND_BUFFER_SIZE, attributes, DEFAULT_BUFFER_SIZE);
        boolean needClientAuth = MapValueConverter.getBooleanAttribute(Port.NEED_CLIENT_AUTH, attributes,
                DEFAULT_NEED_CLIENT_AUTH);
        boolean wantClientAuth = MapValueConverter.getBooleanAttribute(Port.WANT_CLIENT_AUTH, attributes,
                DEFAULT_WANT_CLIENT_AUTH);

        destringifiedAttributes.put(Port.TCP_NO_DELAY, tcpNoDelay);
        destringifiedAttributes.put(Port.RECEIVE_BUFFER_SIZE, receiveBufferSize);
        destringifiedAttributes.put(Port.SEND_BUFFER_SIZE, sendBufferSize);
        destringifiedAttributes.put(Port.NEED_CLIENT_AUTH, needClientAuth);
        destringifiedAttributes.put(Port.WANT_CLIENT_AUTH, wantClientAuth);
        return destringifiedAttributes;
    }
}
