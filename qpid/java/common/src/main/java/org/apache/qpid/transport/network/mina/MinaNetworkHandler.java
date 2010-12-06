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

import static org.apache.qpid.transport.util.Functions.*;
import static org.apache.qpid.configuration.ClientProperties.*;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.ExecutorThreadModel;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.LoggingFilter;
import org.apache.mina.filter.ReadThrottleFilterBuilder;
import org.apache.mina.filter.SSLFilter;
import org.apache.mina.filter.executor.ExecutorFilter;
import org.apache.mina.util.SessionUtil;
import org.apache.qpid.protocol.ReceiverFactory;
import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.network.NetworkConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MinaNetworkHandler
 */
public class MinaNetworkHandler extends IoHandlerAdapter 
{
    private static final Logger _log = LoggerFactory.getLogger(MinaNetworkHandler.class);
    
    private MinaNetworkTransport _transport = null;
    private SSLContextFactory _sslFactory = null;
    private ReceiverFactory _factory = null;
    private boolean _debug = false;

    public MinaNetworkHandler(MinaNetworkTransport transport, SSLContextFactory sslFactory, ReceiverFactory factory)
    {
        _transport = transport;
        _sslFactory = sslFactory;
        _factory = factory;
        _debug = Boolean.getBoolean("amqj.protocol.debug");
    }

    public MinaNetworkHandler(MinaNetworkTransport transport, SSLContextFactory sslFactory)
    {
        this(transport, sslFactory, null);
    }
    
    public void messageReceived(IoSession session, Object message)
    {
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) session.getAttachment();
        ByteBuffer buf = (ByteBuffer) message;
        try
        {
            receiver.received(buf.buf());
        }
        catch (Throwable t)
        {
            _log.error("Exception handling buffer: " + str(buf.buf()), t);
            throw new RuntimeException(t);
        }
    }

    public void exceptionCaught(IoSession ssn, Throwable e)
    {
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) ssn.getAttachment();
        _log.error("Caught exception in transport layer", e);
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
    public void sessionCreated(IoSession session) throws Exception
    {
        _log.info("Created MINA session: " + System.identityHashCode(session));
        SessionUtil.initialize(session);

        IoFilterChain chain = session.getFilterChain();
        if (chain.contains(ExecutorThreadModel.class.getName()))
        {
            chain.remove(ExecutorThreadModel.class.getName());
        }
        IoFilterAdapter filter = new ExecutorFilter(_transport.getExecutor());
        chain.addFirst("sessionExecutor", filter);
        
        // Add SSL filter
        if (_sslFactory != null)
        {
	        if (_factory != null)
	        {
	            chain.addFirst("sslFilter", new SSLFilter(_sslFactory.buildServerContext()));
	        }
	        else
	        {
	            chain.addFirst("sslFilter", new SSLFilter(_sslFactory.buildClientContext()));
	        }
        }
        
        // Add IO Protection Read Filter
        if (Boolean.getBoolean(PROTECTIO_PROP_NAME))
        {
            try
            {
                ReadThrottleFilterBuilder readFilter = new ReadThrottleFilterBuilder();
                readFilter.setMaximumConnectionBufferSize(Integer.getInteger(READ_BUFFER_LIMIT_PROP_NAME, READ_BUFFER_LIMIT_DEFAULT));
                readFilter.attach(chain);
                _log.info("Using IO Read/Write Filter Protection");
            }
            catch (Exception e)
            {
                _log.error("Unable to attach IO Read/Write Filter Protection", e);
            }
        }
        
        // Add logging filter
        if (_debug)
        {
            LoggingFilter logFilter = new LoggingFilter();
            chain.addLast("logging", logFilter);
        }

        if (_factory != null)
        {
	        NetworkConnection network = new MinaNetworkConnection(session);
	        
            Receiver<java.nio.ByteBuffer> receiver = _factory.newReceiver(_transport, network);
		    session.setAttachment(receiver);
        }
    }

    public void sessionClosed(IoSession session) throws Exception
    {
        Receiver<java.nio.ByteBuffer> receiver = (Receiver) session.getAttachment();
        receiver.closed();
    }
    
    public void sessionIdle(IoSession session, IdleStatus status)
    {
        if (status == IdleStatus.READER_IDLE || status == IdleStatus.BOTH_IDLE)
        {
	        _log.info("Idle MINA session: " + System.identityHashCode(session));
            session.close();
        }
    }
}
