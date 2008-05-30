/*
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
 */

package org.apache.qpidity.transport.network.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.network.Assembler;
import org.apache.qpidity.transport.network.Disassembler;
import org.apache.qpidity.transport.network.InputHandler;
import org.apache.qpidity.transport.network.OutputHandler;
import org.apache.qpidity.transport.util.Logger;

/**
 * This class provides a synchronous IO implementation using
 * the java.io classes. The IoHandler runs in its own thread.
 * The following params are configurable via JVM arguments
 * TCP_NO_DELAY - amqj.tcpNoDelay
 * SO_RCVBUF    - amqj.receiveBufferSize
 * SO_SNDBUF    - amqj.sendBufferSize
 */
public class IoHandler implements Runnable
{
    private static int DEFAULT_READ_WRITE_BUFFER_SIZE = 64 * 1024;

    private Receiver<ByteBuffer> _receiver;
    private Socket _socket;
    private byte[] _readBuf;
    private static Map<Integer,IoSender> _handlers = new ConcurrentHashMap<Integer,IoSender>();
    private AtomicInteger _count = new AtomicInteger();
    private int _readBufferSize;
    private int _writeBufferSize;

    private static final Logger log = Logger.get(IoHandler.class);

    private IoHandler()
    {
        _readBufferSize = Integer.getInteger("amqj.receiveBufferSize",DEFAULT_READ_WRITE_BUFFER_SIZE);
        _writeBufferSize = Integer.getInteger("amqj.sendBufferSize",DEFAULT_READ_WRITE_BUFFER_SIZE);
    }

    public static final Connection connect(String host, int port,
            ConnectionDelegate delegate)
    {
        IoHandler handler = new IoHandler();
        return handler.connectInternal(host,port,delegate);
    }

    private Connection connectInternal(String host, int port,
            ConnectionDelegate delegate)
    {
        try
        {
            InetAddress address = InetAddress.getByName(host);
            _socket = new Socket();
            _socket.setReuseAddress(true);
            _socket.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));

            log.debug("default-SO_RCVBUF : " + _socket.getReceiveBufferSize());
            log.debug("default-SO_SNDBUF : " + _socket.getSendBufferSize());

            _socket.setSendBufferSize(_writeBufferSize);
            _socket.setReceiveBufferSize(_readBufferSize);

            log.debug("new-SO_RCVBUF : " + _socket.getReceiveBufferSize());
            log.debug("new-SO_SNDBUF : " + _socket.getSendBufferSize());

            if (address != null)
            {
                _socket.connect(new InetSocketAddress(address, port));
            }
            while (!_socket.isConnected())
            {

            }

        }
        catch (SocketException e)
        {
            log.error(e,"Error connecting to broker");
        }
        catch (IOException e)
        {
            log.error(e,"Error connecting to broker");
        }

        IoSender sender = new IoSender(_socket);
        Connection con = new Connection
            (new Disassembler(new OutputHandler(sender), 64*1024 - 1),
             delegate);

        con.setConnectionId(_count.incrementAndGet());
        _handlers.put(con.getConnectionId(),sender);

        _receiver = new InputHandler(new Assembler(con), InputHandler.State.PROTO_HDR);

        Thread t = new Thread(this);
        t.setName("IO Handler Thread-" + _count.get());
        t.start();

        return con;
    }

    public void run()
    {
        // I set the read buffer size simillar to SO_RCVBUF
        // Haven't tested with a lower value to see its better or worse
        _readBuf = new byte[_readBufferSize];
        try
        {
            InputStream in = _socket.getInputStream();
            int read = 0;
            while(_socket.isConnected())
            {
                try
                {
                    read = in.read(_readBuf);
                    if (read > 0)
                    {
                        ByteBuffer b = ByteBuffer.allocate(read);
                        b.put(_readBuf,0,read);
                        b.flip();
                        //byte[] temp = new byte[read];
                        //System.arraycopy(_readBuf, 0,temp, 0, read);
                        //ByteBuffer b = ByteBuffer.wrap(temp);
                        _receiver.received(b);
                    }
                }
                catch(Exception e)
                {
                    throw new RuntimeException("Error reading from socket input stream",e);
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error getting input stream from the socket",e);
        }
        finally
        {
            try
            {
                _socket.close();
            }
            catch(Exception e)
            {
                log.error(e,"Error closing socket");
            }
        }
    }

    public static void startBatchingFrames(int connectionId)
    {
        IoSender sender = _handlers.get(connectionId);
        sender.setStartBatching();
    }


}
