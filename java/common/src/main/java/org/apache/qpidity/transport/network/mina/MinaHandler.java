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

import org.apache.mina.common.*;

import org.apache.mina.transport.socket.nio.SocketAcceptor;
import org.apache.mina.transport.socket.nio.SocketSessionConfig;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.mina.filter.ReadThrottleFilterBuilder;
import org.apache.mina.filter.WriteBufferLimitFilterBuilder;
import org.apache.mina.filter.executor.ExecutorFilter;

import org.apache.qpidity.transport.Binding;
import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.Sender;

import org.apache.qpidity.transport.util.Logger;

import org.apache.qpidity.transport.network.Assembler;
import org.apache.qpidity.transport.network.Disassembler;
import org.apache.qpidity.transport.network.InputHandler;
import org.apache.qpidity.transport.network.OutputHandler;

import static org.apache.qpidity.transport.util.Functions.*;

/**
 * MinaHandler
 *
 * @author Rafael H. Schloming
 */
//RA making this public until we sort out the package issues
public class MinaHandler<E> implements IoHandler
{
    private static final int MAX_FRAME_SIZE = 64 * 1024 - 1;
    /** Default buffer size for pending messages reads */
    private static final String DEFAULT_READ_BUFFER_LIMIT = "262144";
    /** Default buffer size for pending messages writes */
    private static final String DEFAULT_WRITE_BUFFER_LIMIT = "262144";

    private static final Logger log = Logger.get(MinaHandler.class);

    static
    {
        ByteBuffer.setAllocator(new SimpleByteBufferAllocator());
        ByteBuffer.setUseDirectBuffers(Boolean.getBoolean("amqj.enableDirectBuffers"));
    }

    private final Binding<E,java.nio.ByteBuffer> binding;

    private MinaHandler(Binding<E,java.nio.ByteBuffer> binding)
    {
        this.binding = binding;
    }

    public void messageReceived(IoSession ssn, Object obj)
    {
        Attachment<E> attachment = (Attachment<E>) ssn.getAttachment();
        ByteBuffer buf = (ByteBuffer) obj;
        try
        {
            attachment.receiver.received(buf.buf());
        }
        catch (Throwable t)
        {
            log.error(t, "exception handling buffer %s", str(buf.buf()));
            throw new RuntimeException(t);
        }
    }

    public void messageSent(IoSession ssn, Object obj)
    {
        // do nothing
    }

    public void exceptionCaught(IoSession ssn, Throwable e)
    {
        Attachment<E> attachment = (Attachment<E>) ssn.getAttachment();
        attachment.receiver.exception(e);
    }

    /**
     * Invoked by MINA when a MINA session for a new connection is created. This method sets up the filter chain on the
     * session, which filters the events handled by this handler. The filter chain consists of, handing off events
     * to an optional protectio
     *
     * @param session The MINA session.
     * @throws Exception Any underlying exceptions are allowed to fall through to MINA.
     */
    public void sessionCreated(IoSession session) throws Exception
    {
        log.debug("Protocol session created for session " + System.identityHashCode(session));

        if (Boolean.getBoolean("protectio"))
        {
            try
            {
                //Add IO Protection Filters
                IoFilterChain chain = session.getFilterChain();

                session.getFilterChain().addLast("tempExecutorFilterForFilterBuilder", new ExecutorFilter());

                ReadThrottleFilterBuilder readfilter = new ReadThrottleFilterBuilder();
                readfilter.setMaximumConnectionBufferSize(
                        Integer.parseInt(System.getProperty("qpid.read.buffer.limit", DEFAULT_READ_BUFFER_LIMIT)));
                readfilter.attach(chain);

                WriteBufferLimitFilterBuilder writefilter = new WriteBufferLimitFilterBuilder();
                writefilter.setMaximumConnectionBufferSize(
                        Integer.parseInt(System.getProperty("qpid.write.buffer.limit", DEFAULT_WRITE_BUFFER_LIMIT)));
                writefilter.attach(chain);
                session.getFilterChain().remove("tempExecutorFilterForFilterBuilder");

                log.info("Using IO Read/Write Filter Protection");
            }
            catch (Exception e)
            {
                log.error("Unable to attach IO Read/Write Filter Protection :" + e.getMessage());
            }
        }
    }

    public void sessionOpened(final IoSession ssn)
    {
        log.debug("opened: %s", this);
        E endpoint = binding.endpoint(new MinaSender(ssn));
        Attachment<E>  attachment =
            new Attachment<E>(endpoint, binding.receiver(endpoint));

        // We need to synchronize and notify here because the MINA
        // connect future returns the session prior to the attachment
        // being set. This is arguably a bug in MINA.
        synchronized (ssn)
        {
            ssn.setAttachment(attachment);
            ssn.notifyAll();
        }
    }

    public void sessionClosed(IoSession ssn)
    {
        log.debug("closed: %s", ssn);
        Attachment<E> attachment = (Attachment<E>) ssn.getAttachment();
        attachment.receiver.closed();
        ssn.setAttachment(null);
    }

    public void sessionIdle(IoSession ssn, IdleStatus status)
    {
        // do nothing
    }

    private static class Attachment<E>
    {

        E endpoint;
        Receiver<java.nio.ByteBuffer> receiver;

        Attachment(E endpoint, Receiver<java.nio.ByteBuffer> receiver)
        {
            this.endpoint = endpoint;
            this.receiver = receiver;
        }
    }

    public static final void accept(String host, int port,
                                    Binding<?,java.nio.ByteBuffer> binding)
        throws IOException
    {
        accept(new InetSocketAddress(host, port), binding);
    }

    public static final <E> void accept(SocketAddress address,
                                        Binding<E,java.nio.ByteBuffer> binding)
        throws IOException
    {
        IoAcceptor acceptor = new SocketAcceptor();
        acceptor.bind(address, new MinaHandler<E>(binding));
    }
                   
    public static final <E> E connect(String host, int port,
                                      Binding<E,java.nio.ByteBuffer> binding)
    {
        return connect(new InetSocketAddress(host, port), binding);
    }

    public static final <E> E connect(SocketAddress address,
                                      Binding<E,java.nio.ByteBuffer> binding)
    {
        MinaHandler<E> handler = new MinaHandler<E>(binding);
        SocketConnector connector = new SocketConnector();
        IoServiceConfig acceptorConfig = connector.getDefaultConfig();
        acceptorConfig.setThreadModel(ThreadModel.MANUAL);
        SocketSessionConfig scfg = (SocketSessionConfig) acceptorConfig.getSessionConfig();
        scfg.setTcpNoDelay(Boolean.getBoolean("amqj.tcpNoDelay"));
        Integer sendBufferSize = Integer.getInteger("amqj.sendBufferSize");
        if (sendBufferSize != null && sendBufferSize > 0)
        {
            scfg.setSendBufferSize(sendBufferSize);
        }
        Integer receiveBufferSize = Integer.getInteger("amqj.receiveBufferSize");
        if (receiveBufferSize != null && receiveBufferSize > 0)
        {
            scfg.setReceiveBufferSize(receiveBufferSize);
        }       
        connector.setWorkerTimeout(0);
        ConnectFuture cf = connector.connect(address, handler);
        cf.join();
        IoSession ssn = cf.getSession();

        // We need to synchronize and wait here because the MINA
        // connect future returns the session prior to the attachment
        // being set. This is arguably a bug in MINA.
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

        Attachment<E> attachment = (Attachment<E>) ssn.getAttachment();
        return attachment.endpoint;
    }

    public static final void accept(String host, int port,
                                    ConnectionDelegate delegate)
        throws IOException
    {
        accept(host, port, new ConnectionBinding
               (delegate, InputHandler.State.PROTO_HDR));
    }

    public static final Connection connect(String host, int port,
                                           ConnectionDelegate delegate)
    {
        return connect(host, port, new ConnectionBinding
                       (delegate, InputHandler.State.PROTO_HDR));
    }

    private static class ConnectionBinding
        implements Binding<Connection,java.nio.ByteBuffer>
    {

        private final ConnectionDelegate delegate;
        private final InputHandler.State state;

        ConnectionBinding(ConnectionDelegate delegate,
                          InputHandler.State state)
        {
            this.delegate = delegate;
            this.state = state;
        }

        public Connection endpoint(Sender<java.nio.ByteBuffer> sender)
        {
            // XXX: hardcoded max-frame
            return new Connection
                (new Disassembler(new OutputHandler(sender), MAX_FRAME_SIZE), delegate);
        }

        public Receiver<java.nio.ByteBuffer> receiver(Connection conn)
        {
            return new InputHandler(new Assembler(conn), state);
        }

    }

}
