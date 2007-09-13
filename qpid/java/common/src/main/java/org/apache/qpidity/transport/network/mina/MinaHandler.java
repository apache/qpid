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
package org.apache.qpidity.transport.network.mina;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.SimpleByteBufferAllocator;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketConnector;

import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.Sender;

import org.apache.qpidity.transport.network.Assembler;
import org.apache.qpidity.transport.network.Disassembler;
import org.apache.qpidity.transport.network.InputHandler;
import org.apache.qpidity.transport.network.OutputHandler;


/**
 * MinaHandler
 *
 * @author Rafael H. Schloming
 */
//RA making this public until we sort out the package issues
public class MinaHandler implements IoHandler
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
        Attachment attachment = (Attachment) ssn.getAttachment();
        ByteBuffer buf = (ByteBuffer) obj;
        attachment.receiver.received(buf.buf());
    }

    public void messageSent(IoSession ssn, Object obj)
    {
        // do nothing
    }

    public void exceptionCaught(IoSession ssn, Throwable e)
    {
        e.printStackTrace();
    }

    public void sessionCreated(final IoSession ssn)
    {
        // do nothing
    }

    public void sessionOpened(final IoSession ssn)
    {
        System.out.println("opened " + ssn);
        // XXX: hardcoded version + max-frame
        Connection conn = new Connection
            (new Disassembler(new OutputHandler(new MinaSender(ssn)),
                              (byte)0, (byte)10, 64*1024),
             delegate);
        // XXX: hardcoded version
        Receiver<java.nio.ByteBuffer> receiver =
            new InputHandler(new Assembler(conn, (byte)0, (byte)10), state);
        ssn.setAttachment(new Attachment(conn, receiver));
        // XXX
        synchronized (ssn)
        {
            ssn.notifyAll();
        }
    }

    public void sessionClosed(IoSession ssn)
    {
        System.out.println("closed " + ssn);
        Attachment attachment = (Attachment) ssn.getAttachment();
        attachment.receiver.closed();
        ssn.setAttachment(null);
    }

    public void sessionIdle(IoSession ssn, IdleStatus status)
    {
        // do nothing
    }

    private class Attachment
    {

        Connection connection;
        Receiver<java.nio.ByteBuffer> receiver;

        Attachment(Connection connection,
                   Receiver<java.nio.ByteBuffer> receiver)
        {
            this.connection = connection;
            this.receiver = receiver;
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
        SocketConnector connector = new SocketConnector();
        connector.setWorkerTimeout(0);
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
        Attachment attachment = (Attachment) ssn.getAttachment();
        return attachment.connection;
    }

}
