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

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.Disassembler;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.server.configuration.*;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.registry.IApplicationRegistry;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

public class ProtocolEngine_0_10  extends InputHandler implements Receiver<ByteBuffer>, ConnectionConfig
{
    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;

    private NetworkConnection _network;
    private ServerConnection _connection;
    private final UUID _id;
    private final IApplicationRegistry _appRegistry;
    private long _createTime = System.currentTimeMillis();

    public ProtocolEngine_0_10(ServerConnection conn, final IApplicationRegistry appRegistry, NetworkConnection network)
    {
        super(new Assembler(conn));
        _connection = conn;
        _connection.setConnectionConfig(this);
        _network = network;
        _id = appRegistry.getConfigStore().createId();
        _appRegistry = appRegistry;

        _connection.setSender(new Disassembler(_network.getSender(), MAX_FRAME_SIZE));
        _connection.onOpen(new Runnable()
        {
            public void run()
            {
                getConfigStore().addConfiguredObject(ProtocolEngine_0_10.this);
            }
        });

        _connection.getLogActor().message(ConnectionMessages.OPEN(null, "0-10", false, true));
    }

    public VirtualHostConfig getVirtualHost()
    {
        return _connection.getVirtualHost();
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    public Boolean isIncoming()
    {
        return true;
    }

    public Boolean isSystemConnection()
    {
        return false;
    }

    public Boolean isFederationLink()
    {
        return false;
    }

    public String getAuthId()
    {
        return _connection.getAuthorizationID();
    }

    public String getRemoteProcessName()
    {
        return null;
    }

    public Integer getRemotePID()
    {
        return null;
    }

    public Integer getRemoteParentPID()
    {
        return null;
    }

    public ConfigStore getConfigStore()
    {
        return _appRegistry.getConfigStore();
    }

    public UUID getId()
    {
        return _id;
    }

    public ConnectionConfigType getConfigType()
    {
        return ConnectionConfigType.getInstance();
    }

    public ConfiguredObject getParent()
    {
        return getVirtualHost();
    }

    public boolean isDurable()
    {
        return false;
    }

    @Override
    public void closed()
    {
        super.closed();
        getConfigStore().removeConfiguredObject(this);
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public Boolean isShadow()
    {
        return false;
    }
    
    public void mgmtClose()
    {
        _connection.mgmtClose();
    }
}
