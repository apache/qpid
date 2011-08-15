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

import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.framing.FrameHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.qpid.amqp_1_0.transport.FrameOutputHandler;
import org.apache.qpid.amqp_1_0.type.FrameBody;

import org.apache.qpid.protocol.ProtocolEngine;
import org.apache.qpid.server.configuration.ConfigStore;
import org.apache.qpid.server.configuration.ConnectionConfigType;
import org.apache.qpid.server.protocol.v1_0.Connection_1_0;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.transport.network.NetworkConnection;

import java.io.PrintWriter;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.Principal;
import java.util.UUID;

public class AMQPSASLEngine_1_0_0 implements ProtocolEngine, FrameOutputHandler
{
    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;

    private NetworkConnection _networkDriver;
    private long _readBytes;
    private long _writtenBytes;
    private final UUID _id;
    private final IApplicationRegistry _appRegistry;
    private long _createTime = System.currentTimeMillis();

    private static final int BUF_SIZE = 8;
    private static final ByteBuffer HEADER =
            ByteBuffer.wrap(new byte[]
                    {
                        (byte)'A',
                        (byte)'M',
                        (byte)'Q',
                        (byte)'P',
                        (byte) 0,
                        (byte) 1,
                        (byte) 0,
                        (byte) 0
                    });

    private FrameWriter _frameWriter;
    private FrameHandler _frameHandler;
    private ByteBuffer _buf = ByteBuffer.allocate(1024 * 1024);
    private Object _sendLock = new Object();
    private byte _major;
    private byte _minor;
    private byte _revision;
    private ConnectionEndpoint _conn;


    static enum State {
        A,
        M,
        Q,
        P,
        PROTOCOL,
        MAJOR,
        MINOR,
        REVISION,
        FRAME
    }

    private State _state = State.A;

    public AMQPSASLEngine_1_0_0(NetworkConnection networkDriver,
                                final IApplicationRegistry appRegistry)
    {


        _id = appRegistry.getConfigStore().createId();
        _appRegistry = appRegistry;
        setNetworkDriver(networkDriver);


        // FIXME Two log messages to maintain compatinbility with earlier protocol versions
//        _connection.getLogActor().message(ConnectionMessages.OPEN(null, null, false, false));
//        _connection.getLogActor().message(ConnectionMessages.OPEN(null, "0-10", false, true));
    }

    public void setNetworkDriver(NetworkConnection driver)
    {
        _networkDriver = driver;


        _conn = new ConnectionEndpoint(new Container(),_appRegistry.getAuthenticationManager());
        _conn.setConnectionEventListener(new Connection_1_0(_appRegistry));
        _conn.setFrameOutputHandler(this);


        _frameWriter =  new FrameWriter(_conn.getDescribedTypeRegistry());
        _frameHandler = new FrameHandler(_conn);

        _networkDriver.getSender().send(HEADER.duplicate());



    }

    public SocketAddress getRemoteAddress()
    {
        return _networkDriver.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _networkDriver.getLocalAddress();
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

    public String getAddress()
    {
        return getRemoteAddress().toString();
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

    public boolean isDurable()
    {
        return false;
    }

    public synchronized void received(ByteBuffer msg)
    {
        _readBytes += msg.remaining();
            switch(_state)
            {
                case A:
                    if(msg.hasRemaining())
                    {
                        msg.get();
                    }
                    else
                    {
                        break;
                    }
                case M:
                    if(msg.hasRemaining())
                    {
                        msg.get();
                    }
                    else
                    {
                        _state = State.M;
                        break;
                    }

                case Q:
                    if(msg.hasRemaining())
                    {
                        msg.get();
                    }
                    else
                    {
                        _state = State.Q;
                        break;
                    }
                case P:
                    if(msg.hasRemaining())
                    {
                        msg.get();
                    }
                    else
                    {
                        _state = State.P;
                        break;
                    }
                case PROTOCOL:
                    if(msg.hasRemaining())
                    {
                        msg.get();
                    }
                    else
                    {
                        _state = State.PROTOCOL;
                        break;
                    }
                case MAJOR:
                    if(msg.hasRemaining())
                    {
                        _major = msg.get();
                    }
                    else
                    {
                        _state = State.MAJOR;
                        break;
                    }
                case MINOR:
                    if(msg.hasRemaining())
                    {
                        _minor = msg.get();
                    }
                    else
                    {
                        _state = State.MINOR;
                        break;
                    }
                case REVISION:
                    if(msg.hasRemaining())
                    {
                        _revision = msg.get();

                        _state = State.FRAME;
                    }
                    else
                    {
                        _state = State.REVISION;
                        break;
                    }
                case FRAME:
                    _frameHandler.parse(msg);
            }

    }

    public void exception(Throwable t)
    {
        t.printStackTrace();
    }

    public void closed()
    {
        // todo

    }

    public long getCreateTime()
    {
        return _createTime;
    }


    public boolean canSend()
    {
        return true;
    }

    public void send(AMQFrame frame)
    {
        send(frame, null);
    }
    public void send(AMQFrame frame, ByteBuffer buffer)
    {

        synchronized(_sendLock)
        {

            if(_buf.remaining() < MAX_FRAME_SIZE)
            {
                _buf = ByteBuffer.allocate(1024*1024);
            }

            _frameWriter.setValue(frame);

            ByteBuffer dup = _buf.slice();

            _frameWriter.writeToBuffer(dup);

            _buf.position(_buf.position()+dup.position());

            dup.flip();
            _writtenBytes += dup.limit();
            _networkDriver.getSender().send(dup);

        }
    }

    public void close()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setLogOutput(final PrintWriter out)
    {
        //TODO
    }


}
