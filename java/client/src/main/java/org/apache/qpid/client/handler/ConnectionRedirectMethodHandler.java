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
package org.apache.qpid.client.handler;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.ConnectionRedirectException;
import org.apache.qpid.client.protocol.AMQProtocolSession;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.ConnectionRedirectBody;
import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.TransportException;

public class ConnectionRedirectMethodHandler implements StateAwareMethodListener<ConnectionRedirectBody>
{
    private static final Logger _logger = LoggerFactory.getLogger(ConnectionRedirectMethodHandler.class);

    private static final int DEFAULT_REDIRECT_PORT = 5672;

    private static ConnectionRedirectMethodHandler _handler = new ConnectionRedirectMethodHandler();

    public static ConnectionRedirectMethodHandler getInstance()
    {
        return _handler;
    }

    private ConnectionRedirectMethodHandler()
    { }

    public void methodReceived(AMQProtocolSession session, ConnectionRedirectBody method, int channelId)
            throws AMQException
    {
        _logger.info("ConnectionRedirect frame received");

        String host = method.getHost().toString();
        // the host is in the form hostname:port with the port being optional
        int portIndex = host.indexOf(':');

        int port;
        if (portIndex == -1)
        {
            port = DEFAULT_REDIRECT_PORT;
        }
        else
        {
            port = Integer.parseInt(host.substring(portIndex + 1));
            host = host.substring(0, portIndex);

        }

        session.notifyError(new ConnectionRedirectException(host,port));

        ByteBufferSender sender = session.getSender();

        // Close the open TCP connection
        try
        {
            sender.close();
        }
        catch(TransportException e)
        {
            //Ignore, they are already logged by the Sender and this
            //is a connection-close being processed by the IoReceiver
            //which will as it closes initiate failover if necessary.
        }
    }

}
