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
package org.apache.qpid.server;

import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.transport.NetworkDriver;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ConnectionDelegate;
import org.apache.qpid.transport.network.Disassembler;

public class ProtocolEngineFactory_0_10 implements ProtocolEngineFactory
{
    private ConnectionDelegate _delegate;

    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;


    public ProtocolEngineFactory_0_10(ConnectionDelegate delegate)
    {
        _delegate = delegate;
    }

    public ProtocolEngine newProtocolEngine(NetworkDriver networkDriver)
    {
        Connection conn = new Connection();
        conn.setConnectionDelegate(_delegate);
        Disassembler dis = new Disassembler(networkDriver, MAX_FRAME_SIZE);
        conn.setSender(dis);
        return new ProtocolEngine_0_10(conn, networkDriver);  //To change body of implemented methods use File | Settings | File Templates.
    }
}
