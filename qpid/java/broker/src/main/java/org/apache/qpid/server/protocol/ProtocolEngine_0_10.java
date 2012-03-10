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

import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConfiguredObject;
import org.apache.qpid.server.configuration.ConnectionConfig;
import org.apache.qpid.server.configuration.ConnectionConfigType;
import org.apache.qpid.server.configuration.VirtualHostConfig;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.transport.ServerConnection;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.Disassembler;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.NetworkConnection;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

public class ProtocolEngine_0_10  extends InputHandler implements ServerProtocolEngine, ConnectionConfig
{
    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;

    private NetworkConnection _network;
    private long _readBytes;
    private long _writtenBytes;
    private ServerConnection _connection;
    private final UUID _id;
    private final IApplicationRegistry _appRegistry;
    private long _createTime = System.currentTimeMillis();

    public ProtocolEngine_0_10(ServerConnection conn,
                               NetworkConnection network,
                               final IApplicationRegistry appRegistry)
    {
        super(new Assembler(conn));
        _connection = conn;
        _connection.setConnectionConfig(this);

        _id = appRegistry.getConfigStore().createId();
        _appRegistry = appRegistry;

        if(network != null)
        {
            setNetworkConnection(network);
        }


        _connection.onOpen(new Runnable()
        {
            public void run()
            {
                getConfigStore().addConfiguredObject(ProtocolEngine_0_10.this);
            }
        });

    }

    public void setNetworkConnection(NetworkConnection network)
    {
        setNetworkConnection(network, network.getSender());
    }

    public void setNetworkConnection(NetworkConnection network, Sender<ByteBuffer> sender)
    {
        _network = network;

        _connection.setSender(new Disassembler(sender, MAX_FRAME_SIZE));

        // FIXME Two log messages to maintain compatibility with earlier protocol versions
        _connection.getLogActor().message(ConnectionMessages.OPEN(null, null, null, false, false, false));
        _connection.getLogActor().message(ConnectionMessages.OPEN(null, "0-10", null, false, true, false));
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _network.getLocalAddress();
    }

    public void received(final ByteBuffer buf)
    {
        super.received(buf);
        _connection.receivedComplete();
    }

    public long getReadBytes()
    {
        return _readBytes;
    }

    public long getWrittenBytes()
    {
        return _writtenBytes;
    }

    public void writerIdle()
    {
        //Todo
    }

    public void readerIdle()
    {
        //Todo
    }

    public VirtualHostConfig getVirtualHost()
    {
        return _connection.getVirtualHost();
    }

    public String getAddress()
    {
        return getRemoteAddress().toString();
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
        return _connection.getAuthorizedPrincipal() == null ? null : _connection.getAuthorizedPrincipal().getName();
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

    public long getConnectionId()
    {
        return _connection.getConnectionId();
    }
}
