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
package org.apache.qpid.nclient.transport;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.apache.log4j.Logger;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;
import org.apache.mina.filter.SSLFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.AMQBody;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersionList;
import org.apache.qpid.nclient.config.ClientConfiguration;
import org.apache.qpid.nclient.core.AMQPException;
import org.apache.qpid.nclient.core.AbstractPhase;
import org.apache.qpid.nclient.core.AMQPConstants;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.ssl.BogusSSLContextFactory;

/**
 * The Transport Phase corresponds to the Layer 1 in AMQP It works at the Frame
 * layer
 * 
 */
public class TransportPhase extends AbstractPhase implements IoHandler, ProtocolVersionList
{

    private static final Logger _logger = Logger
            .getLogger(TransportPhase.class);

    private IoSession _ioSession;
    private BrokerDetails _brokerDetails;
    
    protected WriteFuture _lastWriteFuture;

    /**
     * ------------------------------------------------ 
     * Phase - methods introduced by Phase 
     * ------------------------------------------------
     */
    
    public void start()throws AMQPException
    {
	_brokerDetails = (BrokerDetails)_ctx.getProperty(AMQPConstants.AMQP_BROKER_DETAILS);
	IoConnector ioConnector = (IoConnector)_ctx.getProperty(AMQPConstants.MINA_IO_CONNECTOR);        
        
	final SocketAddress address;
	if (ioConnector instanceof VmPipeConnector)
	{
	    address = new VmPipeAddress(_brokerDetails.getPort());
	}
	else
	{
            address = new InetSocketAddress(_brokerDetails.getHost(), _brokerDetails.getPort());
            _logger.info("Attempting connection to " + address);
            
	}
	
        ConnectFuture future = ioConnector.connect(address,this);
        
        // wait for connection to complete
        if (future.join(_brokerDetails.getTimeout()))
        {
            // we call getSession which throws an IOException if there has been an error connecting
            future.getSession();
        }
        else
        {
            throw new AMQPException("Timeout waiting for connection.");
        }	
    }

    public void messageReceived(Object frame) throws AMQPException
    {
        super.messageReceived(frame);
    }

    public void messageSent(Object frame) throws AMQPException
    {
        _ioSession.write(frame);
    }

    /**
     * ------------------------------------------------ 
     * IoHandler - methods introduced by IoHandler 
     * ------------------------------------------------
     */
    public void sessionIdle(IoSession session, IdleStatus status)
            throws Exception
    {
        _logger.debug("Protocol Session for [ " + this +  " : " + session + "] idle: "
                + status);
        if (IdleStatus.WRITER_IDLE.equals(status))
        {
            // write heartbeat frame:
            _logger.debug("Sent heartbeat");
            session.write(HeartbeatBody.FRAME);
            // HeartbeatDiagnostics.sent();
        } else if (IdleStatus.READER_IDLE.equals(status))
        {
            // failover:
            // HeartbeatDiagnostics.timeout();
            _logger.warn("Timed out while waiting for heartbeat from peer.");
            session.close();
        }
    }

    public void messageReceived(IoSession session, Object message)
            throws Exception
    {
        AMQFrame frame = (AMQFrame) message;
        final AMQBody bodyFrame = frame.getBodyFrame();

        if (bodyFrame instanceof HeartbeatBody)
        {
            _logger.debug("Received heartbeat");
        } else
        {
            messageReceived(frame);
        }        
    }

    public void messageSent(IoSession session, Object message) throws Exception
    {
        _logger.debug("Sent frame " + message);
    }

    public void exceptionCaught(IoSession session, Throwable cause)
            throws Exception
    {
        // Need to handle failover
	_logger.info("Exception caught for [ " + this + " : Session " + System.identityHashCode(session) + "]",cause);
        //sessionClosed(session);
    }

    public void sessionClosed(IoSession session) throws Exception
    {
        // Need to handle failover
        _logger.info("Protocol Session for [ " + this + " :  " +  System.identityHashCode(session) + "] closed");
    }

    public void sessionCreated(IoSession session) throws Exception
    {
        _logger.info("Protocol session created for  " + this +  " session : "
                + System.identityHashCode(session));

        final ProtocolCodecFilter pcf = new ProtocolCodecFilter(
                new AMQCodecFactory(false));

        if (ClientConfiguration.get().getBoolean(
                AMQPConstants.USE_SHARED_READ_WRITE_POOL))
        {
            session.getFilterChain().addBefore("AsynchronousWriteFilter",
                    "protocolFilter", pcf);
        } 
        else
        {
            session.getFilterChain().addLast("protocolFilter", pcf);
        }
        // we only add the SSL filter where we have an SSL connection
        if (_brokerDetails.useSSL())
        {
            // FIXME: Bogus context cannot be used in production.
            SSLFilter sslFilter = new SSLFilter(BogusSSLContextFactory
                    .getInstance(false));
            sslFilter.setUseClientMode(true);
            session.getFilterChain().addBefore("protocolFilter", "ssl",
                    sslFilter);
        }

        try
        {

            ReadWriteThreadModel threadModel = ReadWriteThreadModel
                    .getInstance();
            threadModel.getAsynchronousReadFilter().createNewJobForSession(
                    session);
            threadModel.getAsynchronousWriteFilter().createNewJobForSession(
                    session);
        } catch (RuntimeException e)
        {
            e.printStackTrace();
        }

        _ioSession = session;
        doAMQPConnectionNegotiation();
    }

    public void sessionOpened(IoSession session) throws Exception
    {
        _logger.info("Protocol session opened for  " + this +  " : session "
                + System.identityHashCode(session));
    }

    /**
     * ---------------------------------------------------------- 
     * Protocol related methods
     * ----------------------------------------------------------
     */
    private void doAMQPConnectionNegotiation()
    {
        int i = pv.length - 1;
        _logger.debug("Engaging in connection negotiation");
        writeFrame(new ProtocolInitiation(pv[i][PROTOCOL_MAJOR], pv[i][PROTOCOL_MINOR]));
    }

    /**
     * ---------------------------------------------------------- 
     * Write Operations 
     * ----------------------------------------------------------
     */
    public void writeFrame(AMQDataBlock frame)
    {
        writeFrame(frame, false);
    }

    public void writeFrame(AMQDataBlock frame, boolean wait)
    {
        WriteFuture f = _ioSession.write(frame);
        if (wait)
        {
            // fixme -- time out?
            f.join();
        } else
        {
            _lastWriteFuture = f;
        }
    }

    /**
     * ----------------------------------------------------------- 
     * Failover section 
     * -----------------------------------------------------------
     */
}
