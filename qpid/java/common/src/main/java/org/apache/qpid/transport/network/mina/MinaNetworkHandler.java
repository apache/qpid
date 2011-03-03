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
package org.apache.qpid.transport.network.mina;

import static org.apache.qpid.transport.util.Functions.str;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.ReadThrottleFilterBuilder;
import org.apache.mina.filter.SSLFilter;
import org.apache.mina.util.SessionUtil;
import org.apache.qpid.protocol.ReceiverFactory;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.NetworkTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MinaNetworkHandler
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class MinaNetworkHandler extends IoHandlerAdapter 
{
    private static final Logger _log = LoggerFactory.getLogger(MinaNetworkHandler.class);
    
    /** Default buffer size for pending messages reads */
    private static final String DEFAULT_READ_BUFFER_LIMIT = "262144";
    
    private NetworkTransport _transport = null;
    private SSLContextFactory _sslFactory = null;
    private ReceiverFactory _factory = null;

    public MinaNetworkHandler(NetworkTransport transport, SSLContextFactory sslFactory, ReceiverFactory factory)
    {
        _transport = transport;
        _sslFactory = sslFactory;
        _factory = factory;
    }

    public MinaNetworkHandler(NetworkTransport transport, SSLContextFactory sslFactory)
    {
        this(transport, sslFactory, null);
    }
    
    @Override
    public void messageReceived(IoSession session, Object message)
    {
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) session.getAttachment();
        ByteBuffer buf = (ByteBuffer) message;
        try
        {
            receiver.received(buf.buf());
        }
        catch (RuntimeException re)
        {
            receiver.exception(re);
        }
    }

    @Override
    public void exceptionCaught(IoSession ssn, Throwable e)
    {
        _log.error("Exception caught by Mina", e);
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) ssn.getAttachment();
        receiver.exception(e);
    }

    /**
     * Invoked by MINA when a MINA session for a new connection is created. This method sets up the filter chain on the
     * session, which filters the events handled by this handler. The filter chain consists of, handing off events
     * to an optional protectio
     *
     * @param session The MINA session.
     * @throws Exception Any underlying exceptions are allowed to fall through to MINA.
     */
    @Override
    public void sessionCreated(IoSession session) throws Exception
    {
        _log.debug("Created session: " + System.identityHashCode(session));
        SessionUtil.initialize(session);
        
        IoFilterChain chain = session.getFilterChain();
        
        if (_sslFactory != null)
        {
            chain.addFirst("sslFilter", new SSLFilter(_sslFactory.buildServerContext()));
        }

        // Add IO Protection Filters
        if (Boolean.getBoolean("protectio"))
        {
            try
            {
                ReadThrottleFilterBuilder readfilter = new ReadThrottleFilterBuilder();
                readfilter.setMaximumConnectionBufferSize(Integer.parseInt(System.getProperty("qpid.read.buffer.limit", DEFAULT_READ_BUFFER_LIMIT)));
                readfilter.attach(chain);
                _log.info("Using IO Read Filter Protection");
            }
            catch (Exception e)
            {
                _log.error("Unable to attach IO Read Filter Protection", e);
            }
        }
 
         if (_factory != null)
         {
             NetworkConnection network = new MinaNetworkConnection(session);
           
             Receiver<java.nio.ByteBuffer> receiver = _factory.newReceiver(_transport, network);
             session.setAttachment(receiver);
         }
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception
    {
        _log.debug("closed: " + System.identityHashCode(session));
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) session.getAttachment();
        receiver.closed();
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) throws Exception
    {
        if (status == IdleStatus.READER_IDLE || status == IdleStatus.BOTH_IDLE)
        {
            session.close();
        }
    }
}
