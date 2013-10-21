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
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.Set;

import org.apache.log4j.Logger;

public class PortHelper
{
    private static final Logger _logger = Logger.getLogger(PortHelper.class);

    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private int timeout = DEFAULT_TIMEOUT_MILLIS;

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
        long deadline = startTime + timeout;
        boolean alreadyFailed = false;

        while (true)
        {
            if (System.currentTimeMillis() > deadline)
            {
                throw new RuntimeException("Timed out after " + timeout + " ms waiting for port " + port + " to become available");
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
        DatagramSocket datagramSocket = null;

        try
        {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true); // ensures that the port is subsequently usable
            datagramSocket = new DatagramSocket(port);
            datagramSocket.setReuseAddress(true);
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
                    throw new RuntimeException("Couldn't close port " + port + " that we created to check its availability", e);
                }
            }
            if(datagramSocket != null)
            {
                datagramSocket.close();
            }
        }
    }

    public void setTimeout(int timeout)
    {
        this.timeout = timeout;
    }
}
