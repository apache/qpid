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

    public void sessionCreated(IoSession ssn)
    {
        System.out.println("created " + ssn);
    }

    public void sessionOpened(final IoSession ssn)
    {
        Connection conn = new Connection(new Handler<java.nio.ByteBuffer>()
                                         {
                                             public void handle(java.nio.ByteBuffer buf)
                                             {
                                                 ssn.write(ByteBuffer.wrap(buf));
                                             }
                                         });
        ssn.setAttachment(conn);
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
            accept(args);
        } else if (args[0].equals("connect")) {
            connect(args);
        }
    }

    public static final void accept(String[] args) throws IOException
    {
        IoAcceptor acceptor = new SocketAcceptor();
        acceptor.bind(new InetSocketAddress("0.0.0.0", 5672), new MinaHandler());
    }

    public static final void connect(String[] args)
    {
        IoConnector connector = new SocketConnector();
        ConnectFuture cf = connector.connect(new InetSocketAddress("0.0.0.0", 5672), new MinaHandler());
        cf.join();
        IoSession ssn = cf.getSession();
        ByteBuffer bb = ByteBuffer.allocate(1024);
        bb.put("AMQP".getBytes());
        bb.flip();
        for (int i = 0; i < 10; i++) {
            ssn.write(bb);
        }
    }

}
