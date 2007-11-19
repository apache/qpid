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

import org.apache.log4j.Logger;
import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.SSLFilter;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.util.SessionUtil;
import org.apache.qpid.AMQException;
import org.apache.qpid.codec.AMQCodecFactory;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQProtocolHeaderException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ConnectionCloseBody;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.framing.ProtocolInitiation;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.transport.ConnectorConfiguration;
import org.apache.qpid.ssl.SSLContextFactory;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * The protocol handler handles "protocol events" for all connections. The state
 * associated with an individual connection is accessed through the protocol session.
 *
 * We delegate all frame (message) processing to the AMQProtocolSession which wraps
 * the state for the connection.
 *
 */
public class AMQPFastProtocolHandler extends IoHandlerAdapter
{
    private static final Logger _logger = Logger.getLogger(AMQPFastProtocolHandler.class);

    private final IApplicationRegistry _applicationRegistry;


    public AMQPFastProtocolHandler(Integer applicationRegistryInstance)
    {
        this(ApplicationRegistry.getInstance(applicationRegistryInstance));
    }

    public AMQPFastProtocolHandler(IApplicationRegistry applicationRegistry)
    {
        _applicationRegistry = applicationRegistry;
        _logger.debug("AMQPFastProtocolHandler created");
    }

    protected AMQPFastProtocolHandler(AMQPFastProtocolHandler handler)
    {
        this(handler._applicationRegistry);
    }

    public void sessionCreated(IoSession protocolSession) throws Exception
    {
        SessionUtil.initialize(protocolSession);
        final AMQCodecFactory codecFactory = new AMQCodecFactory(true);

        createSession(protocolSession, _applicationRegistry, codecFactory);
        _logger.info("Protocol session created for:" + protocolSession.getRemoteAddress());

        final ProtocolCodecFilter pcf = new ProtocolCodecFilter(codecFactory);

        ConnectorConfiguration connectorConfig = ApplicationRegistry.getInstance().
                getConfiguredObject(ConnectorConfiguration.class);
        if (connectorConfig.enableExecutorPool)
        {
            if (connectorConfig.enableSSL && isSSLClient(connectorConfig, protocolSession))
            {
                String keystorePath = connectorConfig.keystorePath;
                String keystorePassword = connectorConfig.keystorePassword;
                String certType = connectorConfig.certType;
                SSLContextFactory sslContextFactory = new SSLContextFactory(keystorePath, keystorePassword, certType);
                protocolSession.getFilterChain().addAfter("AsynchronousReadFilter", "sslFilter",
                                                          new SSLFilter(sslContextFactory.buildServerContext()));
            }
            protocolSession.getFilterChain().addBefore("AsynchronousWriteFilter", "protocolFilter", pcf);
        }
        else
        {
            protocolSession.getFilterChain().addLast("protocolFilter", pcf);
            if (connectorConfig.enableSSL && isSSLClient(connectorConfig, protocolSession))
            {
                String keystorePath = connectorConfig.keystorePath;
                String keystorePassword = connectorConfig.keystorePassword;
                String certType = connectorConfig.certType;
                SSLContextFactory sslContextFactory = new SSLContextFactory(keystorePath, keystorePassword, certType);
                protocolSession.getFilterChain().addBefore("protocolFilter", "sslFilter",
                                                           new SSLFilter(sslContextFactory.buildServerContext()));
            }

        }
    }

    /**
     * Separated into its own, protected, method to allow easier reuse
     */
    protected void createSession(IoSession session, IApplicationRegistry applicationRegistry, AMQCodecFactory codec) throws AMQException
    {
        new AMQMinaProtocolSession(session, applicationRegistry.getVirtualHostRegistry(), codec);
    }

    public void sessionOpened(IoSession protocolSession) throws Exception
    {
        _logger.info("Session opened for:" + protocolSession.getRemoteAddress());
    }

    public void sessionClosed(IoSession protocolSession) throws Exception
    {
        _logger.info("Protocol Session closed for:" + protocolSession.getRemoteAddress());
        final AMQProtocolSession amqProtocolSession = AMQMinaProtocolSession.getAMQProtocolSession(protocolSession);
        //fixme  -- this can be null
        if (amqProtocolSession != null)
        {
            amqProtocolSession.closeSession();
        }
    }

    public void sessionIdle(IoSession session, IdleStatus status) throws Exception
    {
        _logger.debug("Protocol Session [" + this + "] idle: " + status + " :for:" + session.getRemoteAddress());
        if (IdleStatus.WRITER_IDLE.equals(status))
        {
            //write heartbeat frame:
            session.write(HeartbeatBody.FRAME);
        }
        else if (IdleStatus.READER_IDLE.equals(status))
        {
            //failover:
            throw new IOException("Timed out while waiting for heartbeat from peer.");
        }

    }

    public void exceptionCaught(IoSession protocolSession, Throwable throwable) throws Exception
    {
        AMQProtocolSession session = AMQMinaProtocolSession.getAMQProtocolSession(protocolSession);
        if (throwable instanceof AMQProtocolHeaderException)
        {

            protocolSession.write(new ProtocolInitiation(ProtocolVersion.getLatestSupportedVersion()));

            protocolSession.close();

            _logger.error("Error in protocol initiation " + session + ":" + protocolSession.getRemoteAddress() + " :" + throwable.getMessage(), throwable);
        }
        else if (throwable instanceof IOException)
        {
            _logger.error("IOException caught in" + session + ", session closed implictly: " + throwable, throwable);
        }
        else
        {
            _logger.error("Exception caught in" + session + ", closing session explictly: " + throwable, throwable);

            // Be aware of possible changes to parameter order as versions change.
            protocolSession.write(ConnectionCloseBody.createAMQFrame(0,
                                                                     session.getProtocolMajorVersion(),
                                                                     session.getProtocolMinorVersion(),    // AMQP version (major, minor)
                                                                     0,    // classId
                                                                     0,    // methodId
                                                                     200,    // replyCode
                                                                     new AMQShortString(throwable.getMessage())    // replyText
            ));
            protocolSession.close();
        }
    }

    /**
     * Invoked when a message is received on a particular protocol session. Note that a
     * protocol session is directly tied to a particular physical connection.
     * @param protocolSession the protocol session that received the message
     * @param message the message itself (i.e. a decoded frame)
     * @throws Exception if the message cannot be processed
     */
    public void messageReceived(IoSession protocolSession, Object message) throws Exception
    {
        final AMQProtocolSession amqProtocolSession = AMQMinaProtocolSession.getAMQProtocolSession(protocolSession);

        if (message instanceof AMQDataBlock)
        {
            amqProtocolSession.dataBlockReceived((AMQDataBlock) message);
                        
        }
        else if (message instanceof ByteBuffer)
        {
            throw new IllegalStateException("Handed undecoded ByteBuffer buf = " + message);
        }
        else
        {
            throw new IllegalStateException("Handed unhandled message. message.class = " + message.getClass() + " message = " + message);
        }
    }

    /**
     * Called after a message has been sent out on a particular protocol session
     * @param protocolSession the protocol session (i.e. connection) on which this
     * message was sent
     * @param object the message (frame) that was encoded and sent
     * @throws Exception if we want to indicate an error
     */
    public void messageSent(IoSession protocolSession, Object object) throws Exception
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Message sent: " + object);
        }
    }

    protected boolean isSSLClient(ConnectorConfiguration connectionConfig,
                                  IoSession protocolSession)
    {
        InetSocketAddress addr = (InetSocketAddress) protocolSession.getLocalAddress();
        return addr.getPort() == connectionConfig.sslPort;
    }
}
