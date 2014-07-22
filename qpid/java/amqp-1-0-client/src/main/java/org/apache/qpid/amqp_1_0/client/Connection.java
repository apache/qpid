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
package org.apache.qpid.amqp_1_0.client;

import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ServiceLoader;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

import org.apache.qpid.amqp_1_0.framing.ConnectionHandler;
import org.apache.qpid.amqp_1_0.framing.ExceptionHandler;
import org.apache.qpid.amqp_1_0.transport.ConnectionEndpoint;
import org.apache.qpid.amqp_1_0.transport.Container;
import org.apache.qpid.amqp_1_0.transport.Predicate;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.SaslFrameBody;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.Error;

public class Connection implements ExceptionHandler
{
    private static final int MAX_FRAME_SIZE = 65536;

    private String _address;
    private ConnectionEndpoint _conn;
    private int _sessionCount;
    private Runnable _connectionErrorTask;
    private Error _socketError;


    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password) throws ConnectionException
    {
        this(address, port, username, password, MAX_FRAME_SIZE);
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password, String remoteHostname) throws ConnectionException
    {
        this(address, port, username, password, MAX_FRAME_SIZE,new Container(),remoteHostname);
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final int maxFrameSize) throws ConnectionException
    {
        this(address,port,username,password,maxFrameSize,new Container());
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final Container container) throws ConnectionException
    {
        this(address,port,username,password,MAX_FRAME_SIZE,container);
    }

    public Connection(final String address,
                      final int port,
                      final String username,
                      final String password,
                      final int maxFrameSize,
                      final Container container) throws ConnectionException
    {
        this(address,port,username,password,maxFrameSize,container, null);
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final int maxFrameSize,
                  final Container container,
                  final String remoteHostname) throws ConnectionException
    {
        this(address,port,username,password,maxFrameSize,container,remoteHostname,false,-1);
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final Container container,
                  final boolean ssl) throws ConnectionException
    {
        this(address, port, username, password, MAX_FRAME_SIZE,container,null,ssl,-1);
    }

    public Connection(final String address,
                  final int port,
                  final String username,
                  final String password,
                  final String remoteHost,
                  final boolean ssl) throws ConnectionException
    {
        this(address, port, username, password, MAX_FRAME_SIZE,new Container(),remoteHost,ssl,-1);
    }

    public Connection(final String address,
                      final int port,
                      final String username,
                      final String password,
                      final Container container,
                      final String remoteHost,
                      final boolean ssl,
                      final int channelMax) throws ConnectionException
    {
        this(address, port, username, password, MAX_FRAME_SIZE,container,remoteHost,ssl,
             channelMax);
    }


    public Connection(final String protocol,
                      final String address,
                      final int port,
                      final String username,
                      final String password,
                      final Container container,
                      final String remoteHost,
                      final SSLContext sslContext,
                      final int channelMax) throws ConnectionException
    {
        this(protocol, address, port, username, password, MAX_FRAME_SIZE,container,remoteHost,sslContext,
             channelMax);
    }

    public Connection(final String address,
                      final int port,
                      final String username,
                      final String password,
                      final int maxFrameSize,
                      final Container container,
                      final String remoteHostname,
                      boolean ssl,
                      int channelMax) throws ConnectionException
    {
        this(ssl?"amqp":"amqps",address,port,username,password,maxFrameSize,container,remoteHostname,getSslContext(ssl),channelMax);
    }

    private static SSLContext getSslContext(final boolean ssl) throws ConnectionException
    {
        try
        {
            return ssl ? SSLContext.getDefault() : null;
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new ConnectionException(e);
        }
    }

    public Connection(final String protocol,
                      final String address,
                      final int port,
                      final String username,
                      final String password,
                      final int maxFrameSize,
                      final Container container,
                      final String remoteHostname,
                      SSLContext sslContext,
                      int channelMax) throws ConnectionException
    {

        _address = address;


        Principal principal = username == null ? null : new Principal()
        {

            public String getName()
            {
                return username;
            }
        };
        _conn = new ConnectionEndpoint(container, principal, password);
        if(channelMax >= 0)
        {
            _conn.setChannelMax((short)channelMax);
        }
        _conn.setDesiredMaxFrameSize(UnsignedInteger.valueOf(maxFrameSize));
        _conn.setRemoteHostname(remoteHostname);

        ConnectionHandler.FrameOutput<FrameBody> out = new ConnectionHandler.FrameOutput<FrameBody>(_conn);

        ConnectionHandler.BytesSource src;

        if(_conn.requiresSASL())
        {
            ConnectionHandler.FrameOutput<SaslFrameBody> saslOut = new ConnectionHandler.FrameOutput<SaslFrameBody>(_conn);

            src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(_conn, (byte)'A',
                                                                                                       (byte)'M',
                                                                                                       (byte)'Q',
                                                                                                       (byte)'P',
                                                                                                       (byte)3,
                                                                                                       (byte)1,
                                                                                                       (byte)0,
                                                                                                       (byte)0),
                                                               new ConnectionHandler.FrameToBytesSourceAdapter(saslOut.asFrameSource(),_conn.getDescribedTypeRegistry()),
                                                               new ConnectionHandler.HeaderBytesSource(_conn, (byte)'A',
                                                                                                       (byte)'M',
                                                                                                       (byte)'Q',
                                                                                                       (byte)'P',
                                                                                                       (byte)0,
                                                                                                       (byte)1,
                                                                                                       (byte)0,
                                                                                                       (byte)0),
                                                               new ConnectionHandler.FrameToBytesSourceAdapter(out.asFrameSource(),_conn.getDescribedTypeRegistry())
            );

            _conn.setSaslFrameOutput(saslOut);
        }
        else
        {
            src =  new ConnectionHandler.SequentialBytesSource(new ConnectionHandler.HeaderBytesSource(_conn,(byte)'A',
                                                                                                       (byte)'M',
                                                                                                       (byte)'Q',
                                                                                                       (byte)'P',
                                                                                                       (byte)0,
                                                                                                       (byte)1,
                                                                                                       (byte)0,
                                                                                                       (byte)0),
                                                               new ConnectionHandler.FrameToBytesSourceAdapter(out.asFrameSource(),_conn.getDescribedTypeRegistry())
            );
        }

        TransportProvider transportProvider = getTransportProvider(protocol);

        transportProvider.connect(_conn,address,port, sslContext, this);


        try
        {
            _conn.open();
        }
        catch(RuntimeException e)
        {
            transportProvider.close();
        }

    }

    private TransportProvider getTransportProvider(final String protocol) throws ConnectionException
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ServiceLoader<TransportProviderFactory> providerFactories = ServiceLoader.load(TransportProviderFactory.class, classLoader);

        for(TransportProviderFactory tpf : providerFactories)
        {
            if(tpf.getSupportedTransports().contains(protocol))
            {
                return tpf.getProvider(protocol);
            }
        }

        throw new ConnectionException("Unknown protocol: " + protocol);
    }

    private Connection(ConnectionEndpoint endpoint)
    {
        _conn = endpoint;
    }


    public Session createSession() throws ConnectionException
    {
        checkNotClosed();
        Session session = new Session(this,String.valueOf(_sessionCount++));
        return session;
    }

    void checkNotClosed() throws ConnectionClosedException
    {
        if(getEndpoint().isClosed())
        {
            Error remoteError = getEndpoint().getRemoteError();
            if(remoteError == null)
            {
                remoteError = new Error();
                remoteError.setDescription("Connection closed for unknown reason");

            }
            throw new ConnectionClosedException(remoteError);
        }
    }

    public ConnectionEndpoint getEndpoint()
    {
        return _conn;
    }

    public void awaitOpen() throws TimeoutException, InterruptedException
    {
        getEndpoint().waitUntil(new Predicate()
        {
            @Override
            public boolean isSatisfied()
            {
                return getEndpoint().isOpen() || getEndpoint().isClosed();
            }
        });

    }

    public void close() throws ConnectionErrorException
    {
        _conn.close();

        try
        {
            _conn.waitUntil(new Predicate()
            {
                @Override
                public boolean isSatisfied()
                {
                    return _conn.closedForInput();
                }
            });
        }
        catch (InterruptedException e)
        {
            throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR, "Interrupted while waiting for connection closure");
        }
        catch (TimeoutException e)
        {
            throw new ConnectionErrorException(AmqpError.INTERNAL_ERROR, "Timed out while waiting for connection closure");
        }
        if(_conn.getRemoteError() != null)
        {
            throw new ConnectionErrorException(_conn.getRemoteError());
        }

    }

    /**
     * Set the connection error task that will be used as a callback for any socket read/write errors.
     *
     * @param connectionErrorTask connection error task
     */
    public void setConnectionErrorTask(Runnable connectionErrorTask)
    {
        _connectionErrorTask = connectionErrorTask;
    }

    /**
     * Return the connection error for any socket read/write error that has occurred
     *
     * @return connection error
     */
    public Error getConnectionError()
    {
        return _socketError;
    }

    @Override
    public void handleException(Exception exception)
    {
        Error socketError = new Error();
        socketError.setDescription(exception.getClass() + ": " + exception.getMessage());
        socketError.setCondition(ConnectionError.SOCKET_ERROR);
        _socketError = socketError;
        if(_connectionErrorTask != null)
        {
            Thread thread = new Thread(_connectionErrorTask);
            thread.start();
        }
    }
}
