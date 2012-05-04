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

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.common.Closeable;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.transport.TransportException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionRegistry implements IConnectionRegistry, Closeable
{
    private List<AMQConnectionModel> _registry = new CopyOnWriteArrayList<AMQConnectionModel>();

    private Logger _logger = Logger.getLogger(ConnectionRegistry.class);

    public void initialise()
    {
        // None required
    }

    /** Close all of the currently open connections. */
    public void close()
    {
        close(IConnectionRegistry.BROKER_SHUTDOWN_REPLY_TEXT);
    }

    public void close(final String replyText)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Closing connection registry :" + _registry.size() + " connections.");
        }
        while (!_registry.isEmpty())
        {
            AMQConnectionModel connection = _registry.get(0);
            closeConnection(connection, AMQConstant.CONNECTION_FORCED, replyText);
        }
    }

    public void closeConnection(AMQConnectionModel connection, AMQConstant cause, String message)
    {
        try
        {
            connection.close(cause, message);
        }
        catch (TransportException e)
        {
            _logger.warn("Error closing connection:" + e.getMessage());
        }
        catch (AMQException e)
        {
            _logger.warn("Error closing connection:" + e.getMessage());
        }
    }

    public void registerConnection(AMQConnectionModel connnection)
    {
        _registry.add(connnection);
    }

    public void deregisterConnection(AMQConnectionModel connnection)
    {
        _registry.remove(connnection);
    }

    public List<AMQConnectionModel> getConnections()
    {
        return new ArrayList<AMQConnectionModel>(_registry);
    }
}
