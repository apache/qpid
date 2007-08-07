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
package org.apache.qpidity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.SimpleByteBufferAllocator;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketConnector;


/**
 * MinaHandler
 *
 * @author Rafael H. Schloming
 */

class MinaHandler implements IoHandler
{

    private final ConnectionDelegate delegate;
    private final InputHandler.State state;

    public MinaHandler(ConnectionDelegate delegate, InputHandler.State state)
    {
        this.delegate = delegate;
        this.state = state;
    }

    public void messageReceived(IoSession ssn, Object obj)
    {
        Connection conn = (Connection) ssn.getAttachment();
        ByteBuffer buf = (ByteBuffer) obj;
        conn.getInputHandler().handle(buf.buf());
    }

    public void messageSent(IoSession ssn, Object obj)
    {
        System.out.println("TX: " + obj);
    }

    public void exceptionCaught(IoSession ssn, Throwable e)
    {
        e.printStackTrace();
    }

    public void sessionCreated(final IoSession ssn)
    {
        System.out.println("created " + ssn);
    }

    public void sessionOpened(final IoSession ssn)
    {
        System.out.println("opened " + ssn);
        Connection conn = new Connection(new Handler<java.nio.ByteBuffer>()
                                         {
                                             public void handle(java.nio.ByteBuffer buf)
                                             {
                                                 ssn.write(ByteBuffer.wrap(buf));
                                             }
                                         },
                                         delegate,
                                         state);
        ssn.setAttachment(conn);
        // XXX
        synchronized (ssn)
        {
            ssn.notifyAll();
        }
    }

    public void sessionClosed(IoSession ssn)
    {
        System.out.println("closed " + ssn);
        ssn.setAttachment(null);
    }

    public void sessionIdle(IoSession ssn, IdleStatus status)
    {
        System.out.println(status);
    }

    public static final void main(String[] args) throws IOException
    {
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        if (args[0].equals("accept")) {
            accept("0.0.0.0", 5672, SessionDelegateStub.source());
        } else if (args[0].equals("connect")) {
            connect("0.0.0.0", 5672, SessionDelegateStub.source());
        }
    }

    public static final void accept(String host, int port,
                                    ConnectionDelegate delegate)
        throws IOException
    {
        IoAcceptor acceptor = new SocketAcceptor();
        acceptor.bind(new InetSocketAddress(host, port),
                      new MinaHandler(delegate, InputHandler.State.PROTO_HDR));
    }

    public static final Connection connect(String host, int port,
                                           ConnectionDelegate delegate)
    {
        MinaHandler handler = new MinaHandler(delegate,
                                              InputHandler.State.FRAME_HDR);
        SocketAddress addr = new InetSocketAddress(host, port);
        IoConnector connector = new SocketConnector();
        ConnectFuture cf = connector.connect(addr, handler);
        cf.join();
        IoSession ssn = cf.getSession();
        // XXX
        synchronized (ssn)
        {
            while (ssn.getAttachment() == null)
            {
                try
                {
                    ssn.wait();
                }
                catch (InterruptedException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
        Connection conn = (Connection) ssn.getAttachment();
        return conn;
    }

}
