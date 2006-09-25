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
package org.apache.qpid.transport;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.transport.ITransportConnection;
import org.apache.qpid.pool.PoolingFilter;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.jms.BrokerDetails;
import org.apache.log4j.Logger;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoServiceConfig;
import org.apache.mina.transport.vmpipe.VmPipeAddress;
import org.apache.mina.transport.vmpipe.VmPipeConnector;

import java.io.IOException;

public class VmPipeTransportConnection implements ITransportConnection
{
    private static final Logger _logger = Logger.getLogger(VmPipeTransportConnection.class);

    private static int _port;

    public VmPipeTransportConnection(int port)
    {
        _port = port;
    }

    public void connect(AMQProtocolHandler protocolHandler, BrokerDetails brokerDetail) throws IOException
    {
        final VmPipeConnector ioConnector = new VmPipeConnector();
        final IoServiceConfig cfg = ioConnector.getDefaultConfig();
        ReferenceCountingExecutorService executorService = ReferenceCountingExecutorService.getInstance();
        PoolingFilter asyncRead = new PoolingFilter(executorService, PoolingFilter.READ_EVENTS,
                                                    "AsynchronousReadFilter");
        cfg.getFilterChain().addFirst("AsynchronousReadFilter", asyncRead);
        PoolingFilter asyncWrite = new PoolingFilter(executorService, PoolingFilter.WRITE_EVENTS,
                                                     "AsynchronousWriteFilter");
        cfg.getFilterChain().addLast("AsynchronousWriteFilter", asyncWrite);
        
        final VmPipeAddress address = new VmPipeAddress(_port);
        _logger.info("Attempting connection to " + address);
        ConnectFuture future = ioConnector.connect(address, protocolHandler);
        // wait for connection to complete
        future.join();
        // we call getSession which throws an IOException if there has been an error connecting
        future.getSession();
    }
}
