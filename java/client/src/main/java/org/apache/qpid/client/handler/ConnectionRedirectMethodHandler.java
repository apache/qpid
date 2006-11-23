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
package org.apache.qpid.client.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.protocol.AMQMethodEvent;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.StateAwareMethodListener;
import org.apache.qpid.framing.ConnectionRedirectBody;

public class ConnectionRedirectMethodHandler implements StateAwareMethodListener
{
    private static final Logger _logger = Logger.getLogger(ConnectionRedirectMethodHandler.class);

    private static final int DEFAULT_REDIRECT_PORT = 5672;

    private static ConnectionRedirectMethodHandler _handler = new ConnectionRedirectMethodHandler();

    public static ConnectionRedirectMethodHandler getInstance()
    {
        return _handler;
    }

    private ConnectionRedirectMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent evt) throws AMQException
    {
        _logger.info("ConnectionRedirect frame received");
        ConnectionRedirectBody method = (ConnectionRedirectBody) evt.getMethod();

        // the host is in the form hostname:port with the port being optional
        int portIndex = method.host.indexOf(':');
        String host;
        int port;
        if (portIndex == -1)
        {
            host = method.host;
            port = DEFAULT_REDIRECT_PORT;
        }
        else
        {
            host = method.host.substring(0, portIndex);
            port = Integer.parseInt(method.host.substring(portIndex + 1));
        }
        evt.getProtocolSession().failover(host, port);
    }
}
