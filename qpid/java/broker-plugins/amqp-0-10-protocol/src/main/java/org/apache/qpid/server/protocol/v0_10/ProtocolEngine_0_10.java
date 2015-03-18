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
package org.apache.qpid.server.protocol.v0_10;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.security.auth.Subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.model.Consumer;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.Constant;
import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.NetworkConnection;


public class ProtocolEngine_0_10  extends InputHandler implements ServerProtocolEngine
{
    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;
    private static final Logger _logger = LoggerFactory.getLogger(ProtocolEngine_0_10.class);


    private NetworkConnection _network;
    private long _readBytes;
    private long _writtenBytes;
    private ServerConnection _connection;

    private long _createTime = System.currentTimeMillis();
    private volatile long _lastReadTime = _createTime;
    private volatile long _lastWriteTime = _createTime;
    private volatile boolean _transportBlockedForWriting;

    private final AtomicReference<Thread> _messageAssignmentSuspended = new AtomicReference<>();

    private final AtomicBoolean _stateChanged = new AtomicBoolean();
    private final AtomicReference<Action<ServerProtocolEngine>> _workListener = new AtomicReference<>();


    public ProtocolEngine_0_10(ServerConnection conn,
                               NetworkConnection network)
    {
        super(new ServerAssembler(conn));
        _connection = conn;

        if(network != null)
        {
            setNetworkConnection(network, network.getSender());
        }
    }

    @Override
    public boolean isMessageAssignmentSuspended()
    {
        Thread lock = _messageAssignmentSuspended.get();
        return lock != null && _messageAssignmentSuspended.get() != Thread.currentThread();
    }

    @Override
    public void setMessageAssignmentSuspended(final boolean messageAssignmentSuspended)
    {
        _messageAssignmentSuspended.set(messageAssignmentSuspended ? Thread.currentThread() : null);

        for(AMQSessionModel<?,?> session : _connection.getSessionModels())
        {
            for (Consumer<?> consumer : session.getConsumers())
            {
                ConsumerImpl consumerImpl = (ConsumerImpl) consumer;
                if (!messageAssignmentSuspended)
                {
                    consumerImpl.getTarget().notifyCurrentState();
                }
                else
                {
                    // ensure that by the time the method returns, no consumer can be in the process of
                    // delivering a message.
                    consumerImpl.getSendLock();
                    consumerImpl.releaseSendLock();
                }
            }
        }
    }



    public void setNetworkConnection(final NetworkConnection network, final ByteBufferSender sender)
    {
        if(!getSubject().equals(Subject.getSubject(AccessController.getContext())))
        {
            Subject.doAs(getSubject(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    setNetworkConnection(network,sender);
                    return null;
                }
            });
        }
        else
        {
            _connection.getEventLogger().message(ConnectionMessages.OPEN(null, null, null, null, false, false, false, false));
            _network = network;

            _connection.setNetworkConnection(network);
            ServerDisassembler disassembler = new ServerDisassembler(wrapSender(sender), Constant.MIN_MAX_FRAME_SIZE);
            _connection.setSender(disassembler);
            _connection.addFrameSizeObserver(disassembler);
            // FIXME Two log messages to maintain compatibility with earlier protocol versions
            _connection.getEventLogger().message(ConnectionMessages.OPEN(null, "0-10", null, null, false, true, false, false));

        }
    }

    private ByteBufferSender wrapSender(final ByteBufferSender sender)
    {
        return new ByteBufferSender()
        {
            @Override
            public void send(ByteBuffer msg)
            {
                _lastWriteTime = System.currentTimeMillis();
                sender.send(msg);
            }

            @Override
            public void flush()
            {
                sender.flush();

            }

            @Override
            public void close()
            {
                sender.close();

            }
        };
    }

    @Override
    public long getLastReadTime()
    {
        return _lastReadTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime;
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
        _lastReadTime = System.currentTimeMillis();
        if(_connection.getAuthorizedPrincipal() == null &&
           (_lastReadTime - _createTime) > _connection.getPort().getContextValue(Long.class,
                                                                                 Port.CONNECTION_MAXIMUM_AUTHENTICATION_DELAY) )
        {
            Subject.doAs(_connection.getAuthorizedSubject(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {

                    _logger.warn("Connection has taken more than "
                                 + _connection.getPort()
                            .getContextValue(Long.class, Port.CONNECTION_MAXIMUM_AUTHENTICATION_DELAY)
                                 + "ms to establish identity.  Closing as possible DoS.");
                    _connection.getEventLogger().message(ConnectionMessages.IDLE_CLOSE());
                    _network.close();
                    return null;
                }
            });
        }
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

    @Override
    public void encryptedTransport()
    {
    }

    public void writerIdle()
    {
        _connection.doHeartBeat();
    }

    public void readerIdle()
    {
        Subject.doAs(_connection.getAuthorizedSubject(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    _connection.getEventLogger().message(ConnectionMessages.IDLE_CLOSE());
                    _network.close();
                    return null;
                }
            });

    }

    public String getAddress()
    {
        return getRemoteAddress().toString();
    }

    public boolean isDurable()
    {
        return false;
    }

    @Override
    public void closed()
    {
        super.closed();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public long getConnectionId()
    {
        return _connection.getConnectionId();
    }

    @Override
    public Subject getSubject()
    {
        return _connection.getAuthorizedSubject();
    }

    @Override
    public boolean isTransportBlockedForWriting()
    {
        return _transportBlockedForWriting;
    }

    @Override
    public void setTransportBlockedForWriting(final boolean blocked)
    {
        _transportBlockedForWriting = blocked;
        _connection.transportStateChanged();
    }

    @Override
    public void processPending()
    {
        _connection.processPending();

    }

    @Override
    public boolean hasWork()
    {
        return _stateChanged.get();
    }

    @Override
    public void notifyWork()
    {
        _stateChanged.set(true);

        final Action<ServerProtocolEngine> listener = _workListener.get();
        if(listener != null)
        {
            listener.performAction(this);
        }
    }

    @Override
    public void clearWork()
    {
        _stateChanged.set(false);
    }

    @Override
    public void setWorkListener(final Action<ServerProtocolEngine> listener)
    {
        _workListener.set(listener);
    }
}
