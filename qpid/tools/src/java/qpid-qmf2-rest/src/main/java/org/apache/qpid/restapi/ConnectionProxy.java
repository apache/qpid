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
import java.util.TimerTask;

// JMS Imports
import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// QMF2 Imports
import org.apache.qpid.qmf2.common.BlockingNotifier;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.util.ConnectionHelper;

/**
 * Contains a Connection object under a "leasehold agreement" whereby the Connection (and associated Sessions and QMF 
 * Consoles) will expire after a period of time.
 * <p>
 * The idea here is to allow a user to create multiple Connection instances (for example to monitor multiple brokers)
 * but by using the lease metaphor we can expire instances that haven't been used for some predetermined period.
 * Using the leashold agreement means that we don't have to rely on users explicitly deleting Connections that they
 * are no longer interested in, because obviously we can't rely on that :-)
 *
 * @author Fraser Adams
 */
public final class ConnectionProxy extends TimerTask implements ExceptionListener
{
    private static final Logger _log = LoggerFactory.getLogger(ConnectionProxy.class);

    private static final int MAX_WORKITEM_QUEUE_SIZE = 20; // Maximum number of items allowed on WorkItem queue.

    // Connections expire after 20 minutes of no use.
    private static final int TIMEOUT_THRESHOLD = (20*60000)/ConnectionStore.PING_PERIOD; 

    // Connections expire after 1 minute if they have never been dereferenced.
    private static final int UNUSED_THRESHOLD = 60000/ConnectionStore.PING_PERIOD; 

    private Connection _connection;
    private Console _console;
    private boolean _connected;
    private int _expireCount;
    private final ConnectionStore _store;
    private final String _name;
    private final String _url;
    private final String _connectionOptions;
    private final boolean _disableEvents;

    /**
     * Actually create the Qpid Connection and QMF2 Console specified in the Constructor.
     */
    private synchronized void createConnection()
    {
        //System.out.println("ConnectionProxy createConnection() name: " + _name + ", thread: " + Thread.currentThread().getId() + ", creating connection to " + _url + ", options " + _connectionOptions);
        try
        {
            _connection = ConnectionHelper.createConnection(_url, _connectionOptions);
            if (_connection != null)
            {       
                _connection.setExceptionListener(this);

                // N.B. creating a Console with a notifier causes the internal WorkQueue to get populated, so care must
                // be taken to manage its size. In a normal Console application the application would only declare this
                // if there was an intention to retrieve work items, but in a fairly general REST API we can't guarantee
                // that clients will. ConsoleLease acts to make the WorkQueue "circular" by deleting items from the
                // front of the WorkQueue if it exceeds a particular size.
                if (_disableEvents)
                {
                    _console = new Console(_name, null, null, null);
                    _console.disableEvents();
                }
                else
                {
                    BlockingNotifier notifier = new BlockingNotifier();
                    _console = new Console(_name, null, notifier, null);
                }
                _console.addConnection(_connection);
                _connected = true;
                _expireCount = UNUSED_THRESHOLD;
                notifyAll();
            }
        }
        catch (Exception ex)
        {
            _log.info("Exception {} caught in ConnectionProxy constructor.", ex.getMessage());
            _connected = false;
        }
    }

    /**
     * This method blocks until the Connection has been created.
     */
    public synchronized void waitForConnection()
    {
        while (!_connected)
        {
            try
            {
                wait();
            }
            catch (InterruptedException ie)
            {
                continue;
            }
        }
    }

    /**
     * This method blocks until the Connection has been created or timeout expires (or wait has been interrupted).
     * @param timeout the maximum time in milliseconds to wait for notification of the connection's availability.
     */
    public synchronized void waitForConnection(long timeout)
    {
        try
        {
            wait(timeout);
        }
        catch (InterruptedException ie)
        { // Ignore
        }
    }

    /**
     * Construct a Proxy to the specified Qpid Connection with the supplied name to be stored in the specified store.
     * @param store The ConnectionStore that we want to store this ConnectionProxy in.
     * @param name A unique name for the Connection that we want to create.
     * @param url A Connection URL using one of the forms supported by {@link org.apache.qpid.qmf2.util.ConnectionHelper}.
     * @param connectionOptions A set of connection options in the form supported by {@link org.apache.qpid.qmf2.util.ConnectionHelper}.
     * @param disableEvents if true create a QMF Console Connection that can only perform synchronous
     * operations like getObjects() and cannot do asynchronous things like Agent discovery or receive Events.
     */
    public ConnectionProxy(final ConnectionStore store, final String name,
                           final String url, final String connectionOptions, final boolean disableEvents)
    {
        _connected = false;
        _store = store;
        _name = name;
        _url = url;
        _connectionOptions = connectionOptions;
        _disableEvents = disableEvents;
    }

    /**
     * The exception listener for the underlying Qpid Connection. This is used to trigger the ConnectionProxy internal
     * reconnect logic. N.B. ConnectionProxy uses its own reconnection logic for two reasons: firstly  the Qpid auto
     * retry mechanism has some undesireable and unreliable behaviours prior to Qpid version 0.16 and secondly the
     * Qpid auto retry mechanism is transparent whereas we actually <b>want</b> to detect connection failures in the REST
     * API so that we can report failures back to the client.
     * @param jmse The JMSException that has caused onException to be triggered.
     */
    public void onException(JMSException jmse)
    {
        _log.info("ConnectionProxy onException {}", jmse.getMessage());
        _connected = false;
    }

    /**
     * This method is called periodically by {@link org.apache.qpid.restapi.ConnectionStore} to carry out a number
     * of housekeeping tasks. It checks if the Qpid Connection is still connected and if not it attempts to reconnect
     * it also checks whether the Connection "lease" has run out and if it has it tidies up the Connection. Finally
     * it restricts the size of the QMF2 WorkItem queue as the REST API has no control over whether a client is or
     * is not interested in being notified of QMF2 Events.
     */
    public void run()
    {
        if (_connected)
        {
            //System.out.println("ConnectionProxy name: " + _name + ", thread: " + Thread.currentThread().getId() + ", WorkItem count = " + _console.getWorkitemCount());

            while (_console.getWorkitemCount() > MAX_WORKITEM_QUEUE_SIZE)
            {
                _console.getNextWorkitem();
            }

            _expireCount--;
            //System.out.println("ConnectionProxy name: " + _name + ", thread: " + Thread.currentThread().getId() + ", expireCount = " + _expireCount);
            if (_expireCount == 0)
            {
                _store.delete(_name);
            }
        }
        else
        {
            createConnection();
        }
    }

    /**
     * Stops scheduled housekeeping, destroys any attached QMF2 Console instances then closes the Qpid Connection.
     */
    public synchronized void close()
    {
        //System.out.println("ConnectionProxy close() name: " + _name + ", thread: " + Thread.currentThread().getId() + ", expireCount = " + _expireCount);

        cancel();

        try
        {
            _console.destroy();
            _connection.close();
        }
        catch (Exception e)
        { // Log and Ignore
            _log.info("ConnectionProxy close() caught Exception {}", e.getMessage());
        }
    }

    /**
     * Retrieves the QMF2 Console that we've associated with this Connection.
     * @return The QMF2 Console that we've associated with this Connection.
     */
    public Console getConsole()
    {
        _expireCount = TIMEOUT_THRESHOLD;
        return _console;
    }

    /**
     * Returns whether or not the Connection is currently connected to the broker. This is used by the REST API to
     * tell any clients about the Connection state.
     * @return true if currently connected or false if not.
     */
    public boolean isConnected()
    {
        _expireCount = TIMEOUT_THRESHOLD;
        return _connected;
    }

    /**
     * Returns the Connection URL String used to create the Connection.
     * @return The Connection URL String used to create the Connection.
     */
    public String getUrl()
    {
        _expireCount = TIMEOUT_THRESHOLD;
        return _url;
    }

    /**
     * Returns the Connection options String used to create the Connection.
     * @return The Connection options String used to create the Connection.
     */
    public String getConnectionOptions()
    {
        _expireCount = TIMEOUT_THRESHOLD;
        return _connectionOptions;
    }

    /**
     * Returns a String representation of a ConnectionProxy.
     * @return The String representation of this ConnectionProxy Object.
     */
    @Override
    public String toString()
    {
        // The reason we use JSON.toMap on the string is because it is fairly tolerant and doesn't need pure JSON
        // if we then call JSON.fromMap we get a pure JSON String.
        return "{" + "\"url\":\"" + _url + "\",\"connectionOptions\":" + 
                JSON.fromMap(JSON.toMap(_connectionOptions)) + "}";
    }
}

