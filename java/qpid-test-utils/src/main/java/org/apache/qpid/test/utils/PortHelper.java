/*
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
 */
package org.apache.qpid.test.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortHelper
{
    private static final Logger _logger = LoggerFactory.getLogger(PortHelper.class);

    public static final int START_PORT_NUMBER = 10000;

    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 49151;

    private int _timeout = DEFAULT_TIMEOUT_MILLIS;


    private final Set<Integer> _allocatedPorts = new HashSet<>();
    private int _highestIssuedPort = -1;

    /**
     * Gets the next available port starting from given point.
     *
     * @param fromPort the port to scan for availability
     * @throws java.util.NoSuchElementException if there are no ports available
     */
    public int getNextAvailable(int fromPort)
    {
        if ((fromPort < MIN_PORT_NUMBER) || (fromPort > MAX_PORT_NUMBER))
        {
            throw new IllegalArgumentException("Invalid start port: " + fromPort);
        }

        for (int i = fromPort; i <= MAX_PORT_NUMBER; i++)
        {
            if (isPortAvailable(i))
            {
                _allocatedPorts.add(i);
                _highestIssuedPort = Math.max(_highestIssuedPort, i);
                return i;
            }
        }

        throw new NoSuchElementException("Could not find an available port above " + fromPort);
    }

    /**
     * Gets the next available port that is higher than all other port numbers issued
     * thus far.  If no port numbers have been issued, a default is used.
     *
     * @throws java.util.NoSuchElementException if there are no ports available
     */
    public int getNextAvailable()
    {

        if (_highestIssuedPort < 0)
        {
            return getNextAvailable(START_PORT_NUMBER);
        }
        else
        {
            return getNextAvailable(_highestIssuedPort + 1);
        }
    }

    /**
     * Tests that all ports allocated by getNextAvailable are free.
     */
    public void waitUntilAllocatedPortsAreFree()
    {
        waitUntilPortsAreFree(_allocatedPorts);
    }

    public void waitUntilPortsAreFree(Set<Integer> ports)
    {
        _logger.debug("Checking if ports " + ports + " are free...");

        for (Integer port : ports)
        {
            waitUntilPortIsFree(port);
        }

        _logger.debug("ports " + ports + " are free");
    }

    private void waitUntilPortIsFree(int port)
    {
        long startTime = System.currentTimeMillis();
        long deadline = startTime + _timeout;
        boolean alreadyFailed = false;

        while (true)
        {
            if (System.currentTimeMillis() > deadline)
            {
                throw new RuntimeException("Timed out after " + _timeout + " ms waiting for port " + port + " to become available");
            }

            if (isPortAvailable(port))
            {
                if(alreadyFailed)
                {
                    _logger.debug("port " + port + " is now available");
                }
                return;
            }
            else
            {
                alreadyFailed = true;
            }

            try
            {
                Thread.sleep(500);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isPortAvailable(int port)
    {
        ServerSocket serverSocket = null;
        try
        {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true); // ensures that the port is subsequently usable
            serverSocket.bind(new InetSocketAddress(port));

            return true;
        }
        catch (IOException e)
        {
            _logger.debug("port " + port + " is not free");
            return false;
        }
        finally
        {
            if (serverSocket != null)
            {
                try
                {
                    serverSocket.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Couldn't close port "
                                               + port
                                               + " that we created to check its availability", e);
                }
            }
        }
    }

    public void setTimeout(int timeout)
    {
        this._timeout = timeout;
    }
}
