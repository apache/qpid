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
package org.apache.qpid.client.transport;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;
import org.apache.qpid.protocol.ProtocolEngineFactory;
import org.apache.qpid.transport.network.VMBrokerMap;
import org.apache.qpid.transport.network.mina.MinaNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The TransportConnection is a helper class responsible for connecting to an AMQ server. It sets up the underlying
 * connector, which currently always uses TCP/IP sockets. It creates the "protocol handler" which deals with MINA
 * protocol events. <p/> Could be extended in future to support different transport types by turning this into concrete
 * class/interface combo.
 */
public class TransportConnection
{
    private static ITransportConnection _instance;

    private static VmPipeAcceptor _acceptor;
    private static int _currentInstance = -1;
    private static int _currentVMPort = -1;

    private static Logger _logger = LoggerFactory.getLogger(TransportConnection.class);

    private static final String DEFAULT_QPID_SERVER = "org.apache.qpid.server.protocol.AMQProtocolEngineFactory";

    public static final int DEFAULT_VM_PORT = 1;

    public static void createVMBroker(int port) throws AMQVMBrokerCreationException
    {
        synchronized(TransportConnection.class)
        {
            if (_acceptor == null)
            {
                _acceptor = new VmPipeAcceptor();

                IoServiceConfig config = _acceptor.getDefaultConfig();
            }
        }
        synchronized (VMBrokerMap.class)
        {

            if (!VMBrokerMap.contains(port))
            {
                _logger.info("Creating InVM Qpid.AMQP listening on port " + port);
                IoHandlerAdapter provider = null;
                try
                {
                    VmPipeAddress pipe = new VmPipeAddress(port);

                    provider = createBrokerInstance(port);

                    _acceptor.bind(pipe, provider);

                    VMBrokerMap.add(port, pipe);
                    _logger.info("Created InVM Qpid.AMQP listening on port " + port);
                }
                catch (IOException e)
                {
                    _logger.error("Got IOException.", e);

                    // Try and unbind provider
                    try
                    {
                        VmPipeAddress pipe = new VmPipeAddress(port);

                        try
                        {
                            _acceptor.unbind(pipe);
                        }
                        catch (Exception ignore)
                        {
                            // ignore
                        }

                        if (provider == null)
                        {
                            provider = createBrokerInstance(port);
                        }

                        _acceptor.bind(pipe, provider);
                        VMBrokerMap.add(port, pipe);
                        _logger.info("Created InVM Qpid.AMQP listening on port " + port);
                    }
                    catch (IOException justUseFirstException)
                    {
                        String because;
                        if (e.getCause() == null)
                        {
                            because = e.toString();
                        }
                        else
                        {
                            because = e.getCause().toString();
                        }

                        throw new AMQVMBrokerCreationException(null, port, because + " Stopped binding of InVM Qpid.AMQP", e);
                    }
                }

            }
            else
            {
                _logger.info("InVM Qpid.AMQP on port " + port + " already exits.");
            }
        }
    }

    private static IoHandlerAdapter createBrokerInstance(int port) throws AMQVMBrokerCreationException
    {
        String protocolProviderClass = System.getProperty("amqj.protocolprovider.class", DEFAULT_QPID_SERVER);
        _logger.info("Creating Qpid protocol provider: " + protocolProviderClass);

        // can't use introspection to get Provider as it is a server class.
        // need to go straight to IoHandlerAdapter but that requries the queues and exchange from the ApplicationRegistry which we can't access.

        // get right constructor and pass in instancec ID - "port"
        IoHandlerAdapter provider;
        try
        {
            Class[] cnstr = {Integer.class};
            Object[] params = {port};

            ProtocolEngineFactory engineFactory = (ProtocolEngineFactory) Class.forName(protocolProviderClass).getConstructor(cnstr).newInstance(params);
            provider = new MinaNetworkHandler(null, engineFactory);

            _logger.info("Created VMBroker Instance:" + port);
        }
        catch (Exception e)
        {
            _logger.info("Unable to create InVM Qpid.AMQP on port " + port + ". Because: " + e.getCause());
            String because;
            if (e.getCause() == null)
            {
                because = e.toString();
            }
            else
            {
                because = e.getCause().toString();
            }

            AMQVMBrokerCreationException amqbce =
                    new AMQVMBrokerCreationException(null, port, because + " Stopped InVM Qpid.AMQP creation", e);
            throw amqbce;
        }

        return provider;
    }

    public static void killAllVMBrokers()
    {
        _logger.info("Killing all VM Brokers");
        synchronized(TransportConnection.class)
        {
            if (_acceptor != null)
            {
                _acceptor.unbindAll();
            }
            synchronized (VMBrokerMap.class)
            {
                VMBrokerMap.clear();
            }
            _acceptor = null;
        }
        _currentInstance = -1;
        _currentVMPort = -1;
    }

    public static void killVMBroker(int port)
    {
        synchronized (VMBrokerMap.class)
        {
            if (VMBrokerMap.contains(port))
            {
                _logger.info("Killing VM Broker:" + port);
                VmPipeAddress address = VMBrokerMap.remove(port);
                // This does need to be sychronized as otherwise mina can hang
                // if a new connection is made
                _acceptor.unbind(address);
            }
        }
    }

}
