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
package org.apache.qpid.server.connection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.AMQProtocolSession;

public class ConnectionRegistry implements IConnectionRegistry, Closeable
{
    private List<AMQProtocolSession> _registry = new CopyOnWriteArrayList<AMQProtocolSession>();

    private Logger _logger = Logger.getLogger(ConnectionRegistry.class);

    public void initialise()
    {
        // None required
    }
    
    public void expireClosedChannels()
    {
        for (AMQProtocolSession connection : _registry)
        {
            connection.closeIfLingeringClosedChannels();
        }
    }

    /** Close all of the currently open connections. */
    public void close()
    {
        while (!_registry.isEmpty())
        {
            AMQProtocolSession connection = _registry.get(0);

            try
            {
                connection.closeConnection(0, new AMQConnectionException(AMQConstant.INTERNAL_ERROR, "Broker is shutting down",
                                                                         0, 0,
                                                                         connection.getProtocolOutputConverter().getProtocolMajorVersion(),
                                                                         connection.getProtocolOutputConverter().getProtocolMinorVersion(),
                                                                         (Throwable) null), true);
            }
            catch (AMQException e)
            {
                _logger.warn("Error closing connection:" + e.getMessage());
            }
        }
    }

    public void registerConnection(AMQProtocolSession connnection)
    {
        _registry.add(connnection);
    }

    public void deregisterConnection(AMQProtocolSession connnection)
    {
        _registry.remove(connnection);
    }
}
