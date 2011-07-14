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

package org.apache.qpid.transport.network.io;

import java.net.Socket;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.qpid.ssl.SSLContextFactory;
import org.apache.qpid.transport.Binding;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.transport.network.security.ssl.SSLReceiver;
import org.apache.qpid.transport.network.security.ssl.SSLSender;
import org.apache.qpid.transport.util.Logger;

/**
 * This class provides a socket based transport using the java.io
 * classes.
 *
 * The following params are configurable via JVM arguments
 * TCP_NO_DELAY - amqj.tcpNoDelay
 * SO_RCVBUF    - amqj.receiveBufferSize
 * SO_SNDBUF    - amqj.sendBufferSize
 */
public final class IoTransport<E>
{

    static
    {
        org.apache.mina.common.ByteBuffer.setAllocator
            (new org.apache.mina.common.SimpleByteBufferAllocator());
        org.apache.mina.common.ByteBuffer.setUseDirectBuffers
            (Boolean.getBoolean("amqj.enableDirectBuffers"));
    }

    private static final Logger log = Logger.get(IoTransport.class);

    private static int DEFAULT_READ_WRITE_BUFFER_SIZE = 64 * 1024;
    private static int readBufferSize = Integer.getInteger
        ("amqj.receiveBufferSize", DEFAULT_READ_WRITE_BUFFER_SIZE);
    private static int writeBufferSize = Integer.getInteger
        ("amqj.sendBufferSize", DEFAULT_READ_WRITE_BUFFER_SIZE);

    private Socket socket;
    private Sender<ByteBuffer> sender;
    private E endpoint;
    private IoReceiver receiver;
    private long timeout = 60000;

    IoTransport(Socket socket, Binding<E,ByteBuffer> binding, boolean ssl)
    {
        this.socket = socket;

        if (ssl)
        {
            setupSSLTransport(socket, binding);
        }
        else
        {
            setupTransport(socket, binding);
        }
    }

    private void setupTransport(Socket socket, Binding<E, ByteBuffer> binding)
    {
        IoSender ios = new IoSender(socket, 2*writeBufferSize, timeout);
        ios.initiate();

        this.sender = ios;
        this.endpoint = binding.endpoint(sender);
        this.receiver = new IoReceiver(socket, binding.receiver(endpoint),
                                       2*readBufferSize, timeout);
        this.receiver.initiate();

        ios.registerCloseListener(this.receiver);
    }

    private void setupSSLTransport(Socket socket, Binding<E, ByteBuffer> binding)
    {
        SSLEngine engine = null;
        SSLContext sslCtx;
        try
        {
            sslCtx = createSSLContext();
        }
        catch (Exception e)
        {
            throw new TransportException("Error creating SSL Context", e);
        }

        try
        {
            engine = sslCtx.createSSLEngine();
            engine.setUseClientMode(true);
        }
        catch(Exception e)
        {
            throw new TransportException("Error creating SSL Engine", e);
        }
        IoSender ios = new IoSender(socket, 2*writeBufferSize, timeout);
        ios.initiate();
        this.sender = new SSLSender(engine,ios);
        this.endpoint = binding.endpoint(sender);
        this.receiver = new IoReceiver(socket, new SSLReceiver(engine,binding.receiver(endpoint),(SSLSender)sender),
                2*readBufferSize, timeout);
        this.receiver.initiate();
        ios.registerCloseListener(this.receiver);

        log.info("SSL Sender and Receiver initiated");
    }

    public Sender<ByteBuffer> getSender()
    {
        return sender;
    }

    public IoReceiver getReceiver()
    {
        return receiver;
    }

    public Socket getSocket()
    {
        return socket;
    }

    private SSLContext createSSLContext() throws Exception
    {
        String trustStorePath = System.getProperty("javax.net.ssl.trustStore");
        String trustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword");
        String trustStoreCertType = System.getProperty("qpid.ssl.trustStoreCertType","SunX509");
                
        String keyStorePath = System.getProperty("javax.net.ssl.keyStore",trustStorePath);
        String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword",trustStorePassword);
        String keyStoreCertType = System.getProperty("qpid.ssl.keyStoreCertType","SunX509");
        
        SSLContextFactory sslContextFactory = new SSLContextFactory(trustStorePath,trustStorePassword,
                                                                    trustStoreCertType,keyStorePath,
                                                                    keyStorePassword,keyStoreCertType);
        
        return sslContextFactory.buildServerContext();
        
    }

}
