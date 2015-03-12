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

import static java.util.Collections.newSetFromMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;

import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.AMQConnectionModel;

public class ConnectionRegistry implements IConnectionRegistry
{
    private Logger _logger = Logger.getLogger(ConnectionRegistry.class);

    private final Set<AMQConnectionModel> _registry = newSetFromMap(new ConcurrentHashMap<AMQConnectionModel, Boolean>());
    private final Collection<RegistryChangeListener> _listeners = new ArrayList<>();

    @Override
    public void initialise()
    {
        // None required
    }

    /** Close all of the currently open connections. */
    @Override
    public void close()
    {
        close(IConnectionRegistry.BROKER_SHUTDOWN_REPLY_TEXT);
    }

    @Override
    public void close(final String replyText)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Closing connection registry :" + _registry.size() + " connections.");
        }
        for(AMQConnectionModel conn : _registry)
        {
            conn.stop();
        }

        while (!_registry.isEmpty())
        {
            Iterator<AMQConnectionModel> itr = _registry.iterator();
            while(itr.hasNext())
            {
                AMQConnectionModel connection = itr.next();
                try
                {
                    connection.closeAsync(AMQConstant.CONNECTION_FORCED, replyText);
                }
                catch (Exception e)
                {
                    _logger.warn("Exception closing connection " + connection.getConnectionId() + " from " + connection.getRemoteAddressString(), e);
                }
                finally
                {
                    itr.remove();
                }
            }
        }
    }

    @Override
    public void registerConnection(AMQConnectionModel connection)
    {
        _registry.add(connection);
        synchronized (_listeners)
        {
            for(RegistryChangeListener listener : _listeners)
            {
                listener.connectionRegistered(connection);
            }
        }
    }

    @Override
    public void deregisterConnection(AMQConnectionModel connection)
    {
        _registry.remove(connection);

        synchronized (_listeners)
        {
            for(RegistryChangeListener listener : _listeners)
            {
                listener.connectionUnregistered(connection);
            }
        }
    }

    @Override
    public void addRegistryChangeListener(RegistryChangeListener listener)
    {
        synchronized (_listeners)
        {
            _listeners.add(listener);
        }
    }

    @Override
    public List<AMQConnectionModel> getConnections()
    {
            return new ArrayList<>(_registry);
    }
}
