/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.vmbroker;

import org.apache.qpid.client.transport.TransportConnection;
import org.apache.qpid.pool.ReadWriteThreadModel;
import org.apache.qpid.server.protocol.AMQPProtocolProvider;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.util.NullApplicationRegistry;
import org.apache.qpid.transport.VmPipeTransportConnection;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.vmpipe.VmPipeAcceptor;
import org.apache.mina.transport.vmpipe.VmPipeAddress;

/**
 * This class is a useful base class when you want to run a test where the
 * broker and the client(s) run in the same VM.
 *
 * Once the VmPipeBroker is initialise, and the client transport
 * is overridden (ignores any connection strings) and connects to this
 * in-VM broker instead (rather than using TCP/IP sockets). This means
 * that clients will run unmodified.
 */
public class VmPipeBroker
{
    private static final Logger _logger = Logger.getLogger(VmPipeBroker.class);

    private IoAcceptor _acceptor;

    public void initialiseBroker() throws Exception
    {
        try
        {
            ApplicationRegistry.initialise(new NullApplicationRegistry());
        }
        catch (ConfigurationException e)
        {
            _logger.error("Error configuring application: " + e, e);
            throw e;
        }

        _acceptor = new VmPipeAcceptor();
        IoServiceConfig config = _acceptor.getDefaultConfig();     

        config.setThreadModel(new ReadWriteThreadModel());
        _acceptor.bind(new VmPipeAddress(1),
                      new AMQPProtocolProvider().getHandler());

        _logger.info("InVM Qpid.AMQP listening on port " + 1);
        TransportConnection.setInstance(new VmPipeTransportConnection());
    }

    public void killBroker()
    {
        _acceptor.unbindAll();
        _acceptor = null;
    }
}
