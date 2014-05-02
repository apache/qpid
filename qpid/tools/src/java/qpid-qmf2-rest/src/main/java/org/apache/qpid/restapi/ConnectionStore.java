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
package org.apache.qpid.restapi;

// Misc Imports
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.console.Console;

/**
 * A ConnectionStore is a container for Qpid Connection Objects, or rather it's a container for ConnectionProxy
 * Objects which wrap Qpid Connections and provide some additional housekeeping behaviour necessary for a distributed
 * system. The ConnectionStore schedules regular housekeeping tasks to be executed on the ConnectionProxy Objects
 * using a java.util.Timer, which scales fairly well.
 *
 * @author Fraser Adams
 */
public class ConnectionStore
{
    /**
     * This represents the time between "pings" to the stored ConnectionProxy Objects. The pings run scheduled tasks
     * such as attempting reconnection if the broker has disconnected and checking lease timeouts.
     */
    public static final int PING_PERIOD = 5000;

    /**
     * This Map is used to associate connection names with their ConnectionProxies. Note that the names are prefixed
     * internally with the authenticated user name to prevent users accidentally (or maliciously) sharing connections.
     */
    private Map<String, ConnectionProxy> _connections = new ConcurrentHashMap<String, ConnectionProxy>();

    /**
     * Create a Timer used to schedule regular checks on ConnectionProxy Objects to see that they are still in use.
     * In essence ConnectionProxy Objects behave in a similar way to RMI Leases in that if they are not used 
     * (dereferenced) within a particular period it is assumed that the client has lost interest and they are reaped.
     */
    private Timer _timer = new Timer(true);

    /**
     * Creates a new ConnectionProxy Object with the given name, which in turn creates a Qpid Connection using the
     * supplied Connection URL and options. In addition it schedules some regular housekeeping on the ConnectionProxy
     * to enable it to manage Connection failures and perform what amounts to distributed garbage collection.
     * When a ConnectionProxy with a given name has been created it is cached and subsequent calls to this method
     * will return the cached instance. If an new instance is required one must first call the delete method.
     * @param name A unique name for the Connection that we want to create.
     * @param url A Connection URL using one of the forms supported by {@link org.apache.qpid.qmf2.util.ConnectionHelper}.
     * @param opts A set of connection options in the form supported by {@link org.apache.qpid.qmf2.util.ConnectionHelper}.
     * @param disableEvents if true create a QMF Console Connection that can only perform synchronous
     * operations like getObjects() and cannot do asynchronous things like Agent discovery or receive Events.
     * @return the ConnectionProxy Object created or cached by this method.
     */
    public synchronized ConnectionProxy create(final String name, final String url, final String opts,
                                               final boolean disableEvents)
    {
        ConnectionProxy connection = _connections.get(name);
        if (connection == null)
        {
            connection = new ConnectionProxy(this, name, url, opts, disableEvents);
            _connections.put(name, connection);
            _timer.schedule(connection, 0, PING_PERIOD);
        }
        return connection;
    }

    /**
     * Closes the named Connection, stops its scheduled housekeeping and removes from the store.
     * @param name the name of the Connection that we want to delete.
     */
    public synchronized void delete(final String name)
    {
        ConnectionProxy connection = _connections.get(name);
        if (connection != null)
        {
            connection.close();
            _connections.remove(name);
        }
    }

    /**
     * Retrieves the named Connection from the store.
     * @param name the name of the Connection that we want to retrieve.
     * @return the ConnectionProxy instance with the given name.
     */
    public ConnectionProxy get(final String name)
    {
        return _connections.get(name);
    }

    /**
     * Return a Map of ConnectionProxies associated with a given user. Note that this method is fairly inefficient
     * as the main _connections Map contains all ConnectionProxies and we normally do a lookup by prefixing the
     * key with the user name in order to demultiplex. It is done this way as we need to look up ConnectionProxy
     * for each API call, whereas looking up all Connections for a user is likely to be rarely called.
     * @param user the security principal associated with a particular user.
     * @return a Map of ConnectionProxy objects associated with the given user.
     */
    public Map<String, ConnectionProxy> getAll(final String user)
    {
        Map<String, ConnectionProxy> map = new ConcurrentHashMap<String, ConnectionProxy>();
        String prefix = user + ".";
        int prefixLength = prefix.length();
        for (Map.Entry<String, ConnectionProxy> entry : _connections.entrySet())
        {
            String key = entry.getKey();
            if (key.startsWith(prefix))
            {
                key = key.substring(prefixLength);
                ConnectionProxy value = entry.getValue();
                map.put(key, value);
            }
        }
        return map;
    }
}


