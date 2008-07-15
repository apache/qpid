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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.TransportException;
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
    private static AtomicInteger _count = new AtomicInteger();
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
        }
        catch (SocketException e)
        {
            throw new TransportException("Error connecting to broker",e);
        }
        catch (IOException e)
        {
            throw new TransportException("Error connecting to broker",e);
        }

        IoSender sender = new IoSender(_socket);
        Connection con = new Connection
            (new Disassembler(new OutputHandler(sender), 64*1024 - 1),
             delegate);

        con.setConnectionId(_count.incrementAndGet());
        _receiver = new InputHandler(new Assembler(con), InputHandler.State.PROTO_HDR);

        Thread t = new Thread(this);
        t.setName("IO Handler Thread-" + _count.get());
        t.start();

        return con;
    }

    public void run()
    {
        // I set the read_buffer size simillar to SO_RCVBUF
        // Haven't tested with a lower value to see its better or worse
        _readBuf = new byte[_readBufferSize];
        try
        {
            InputStream in = _socket.getInputStream();
            int read = 0;
            while(read != -1)
            {
                read = in.read(_readBuf);
                if (read > 0)
                {
                    ByteBuffer b = ByteBuffer.allocate(read);
                    b.put(_readBuf,0,read);
                    b.flip();
                    _receiver.received(b);
                }
            }
        }
        catch (IOException e)
        {
            _receiver.exception(new Exception("Error getting input stream from the socket",e));
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

    /**
     * Will experiment in a future version with batching
     */
    public static void startBatchingFrames(int connectionId)
    {

    }


}
