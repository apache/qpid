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

import org.apache.log4j.Logger;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoServiceConfig;


import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.socket.nio.SocketConnector;
import org.apache.qpid.client.AMQBrokerDetails;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.client.vmbroker.AMQVMBrokerCreationException;


import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The TransportConnection is a helper class responsible for connecting to an AMQ server. It sets up
 * the underlying connector, which currently always uses TCP/IP sockets. It creates the
 * "protocol handler" which deals with MINA protocol events.
 * <p/>
 * Could be extended in future to support different transport types by turning this into concrete class/interface
 * combo.
 */
public class TransportConnection
{
    private static ITransportConnection _instance;

    private static Map _inVmPipeAddress = new HashMap();
    private static VmPipeAcceptor _acceptor;
    private static int _currentInstance = -1;
    private static int _currentVMPort = -1;

    private static final int TCP = 0;
    private static final int VM = 1;

    private static Logger _logger = Logger.getLogger(TransportConnection.class);

    private static final String DEFAULT_QPID_SERVER = "org.apache.qpid.server.protocol.AMQPFastProtocolHandler";

    static
    {
        _acceptor = new VmPipeAcceptor();

        IoServiceConfig config = _acceptor.getDefaultConfig();

        config.setThreadModel(new ReadWriteThreadModel());
    }

    public static ITransportConnection getInstance() throws AMQTransportConnectionException
    {
        AMQBrokerDetails details = new AMQBrokerDetails();
        details.setTransport(BrokerDetails.TCP);
        return getInstance(details);
    }

    public static ITransportConnection getInstance(BrokerDetails details) throws AMQTransportConnectionException
    {
        int transport = getTransport(details.getTransport());

        if (transport == -1)

        {
            throw new AMQNoTransportForProtocolException(details);
        }

        if (transport == _currentInstance)

        {
            if (transport == VM)
            {
                if (_currentVMPort == details.getPort())
                {
                    return _instance;
                }
            }
            else
            {
                return _instance;
            }
        }

        _currentInstance = transport;

        switch (transport)

        {
            case TCP:
                _instance = new SocketTransportConnection(new SocketTransportConnection.SocketConnectorFactory()
                {
                    public IoConnector newSocketConnector()
                    {
                        SocketConnector result;
                        //FIXME - this needs to be sorted to use the new Mina MultiThread SA.
                        if (Boolean.getBoolean("qpidnio"))
                        {
                            _logger.fatal("Using Qpid NIO - sysproperty 'qpidnio' is set.");
//                            result = new org.apache.qpid.nio.SocketConnector(); // non-blocking connector
                        }
//                        else
                        {
                            _logger.info("Using Mina NIO");
                            result = new SocketConnector(); // non-blocking connector
                        }

                        // Don't have the connector's worker thread wait around for other connections (we only use
                        // one SocketConnector per connection at the moment anyway). This allows short-running
                        // clients (like unit tests) to complete quickly.
                        result.setWorkerTimeout(0);

                        return result;
                    }
                });
                break;
            case VM:
            {
                _instance = getVMTransport(details, Boolean.getBoolean("amqj.AutoCreateVMBroker"));
                break;
            }
        }

        return _instance;
    }

    private static int getTransport(String transport)
    {
        if (transport.equals(BrokerDetails.TCP))
        {
            return TCP;
        }

        if (transport.equals(BrokerDetails.VM))
        {
            return VM;
        }

        return -1;
    }

    private static ITransportConnection getVMTransport(BrokerDetails details, boolean AutoCreate) throws AMQVMBrokerCreationException
    {
        int port = details.getPort();

        if (!_inVmPipeAddress.containsKey(port))
        {
            if (AutoCreate)
            {
                createVMBroker(port);
            }
            else
            {
                throw new AMQVMBrokerCreationException(port, "VM Broker on port " + port + " does not exist. Auto create disabled.");
            }
        }

        return new VmPipeTransportConnection(port);
    }


    public static void createVMBroker(int port) throws AMQVMBrokerCreationException
    {


        if (!_inVmPipeAddress.containsKey(port))
        {
            _logger.info("Creating InVM Qpid.AMQP listening on port " + port);
            IoHandlerAdapter provider = null;
            try
            {
                VmPipeAddress pipe = new VmPipeAddress(port);

                provider = createBrokerInstance(port);

                _acceptor.bind(pipe, provider);

                _inVmPipeAddress.put(port, pipe);
                _logger.info("Created InVM Qpid.AMQP listening on port " + port);
            }
            catch (IOException e)
            {
                _logger.error(e);

                //Try and unbind provider
                try
                {
                    VmPipeAddress pipe = new VmPipeAddress(port);

                    try
                    {
                        _acceptor.unbind(pipe);
                    }
                    catch (Exception ignore)
                    {
                        //ignore
                    }

                    if (provider == null)
                    {
                        provider = createBrokerInstance(port);
                    }

                    _acceptor.bind(pipe, provider);
                    _inVmPipeAddress.put(port, pipe);
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

                    throw new AMQVMBrokerCreationException(port, because + " Stopped binding of InVM Qpid.AMQP");
                }
            }
        }
        else
        {
            _logger.info("InVM Qpid.AMQP on port " + port + " already exits.");
        }

    }

    private static IoHandlerAdapter createBrokerInstance(int port) throws AMQVMBrokerCreationException
    {
        String protocolProviderClass = System.getProperty("amqj.protocolprovider.class", DEFAULT_QPID_SERVER);
        _logger.info("Creating Qpid protocol provider: " + protocolProviderClass);

        // can't use introspection to get Provider as it is a server class.
        // need to go straight to IoHandlerAdapter but that requries the queues and exchange from the ApplicationRegistry which we can't access.

        //get right constructor and pass in instancec ID - "port"
        IoHandlerAdapter provider;
        try
        {
            Class[] cnstr = {Integer.class};
            Object[] params = {port};
            provider = (IoHandlerAdapter) Class.forName(protocolProviderClass).getConstructor(cnstr).newInstance(params);
            //Give the broker a second to create
            _logger.info("Created VMBroker Instance:" + port);
        }
        catch (Exception e)
        {
            _logger.info("Unable to create InVM Qpid.AMQP on port " + port + ". Because: " + e.getCause());
            _logger.error(e);
            String because;
            if (e.getCause() == null)
            {
                because = e.toString();
            }
            else
            {
                because = e.getCause().toString();
            }


            throw new AMQVMBrokerCreationException(port, because + " Stopped InVM Qpid.AMQP creation");
        }

        return provider;
    }

    public static void killAllVMBrokers()
    {
        _logger.info("Killing all VM Brokers");
        _acceptor.unbindAll();

        Iterator keys = _inVmPipeAddress.keySet().iterator();

        while (keys.hasNext())
        {
            int id = (Integer) keys.next();
            _inVmPipeAddress.remove(id);
        }

    }

    public static void killVMBroker(int port)
    {
        VmPipeAddress pipe = (VmPipeAddress) _inVmPipeAddress.get(port);
        if (pipe != null)
        {
            _logger.info("Killing VM Broker:" + port);
            _inVmPipeAddress.remove(port);
            _acceptor.unbind(pipe);
        }
    }

}
