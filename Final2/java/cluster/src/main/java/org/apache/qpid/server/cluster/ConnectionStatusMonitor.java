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
package org.apache.qpid.server.cluster;

import java.io.IOException;

class ConnectionStatusMonitor
{
    private boolean _complete;
    private boolean _redirected;
    private String _host;
    private int _port;
    private RuntimeException _error;

    synchronized void opened()
    {
        _complete = true;
        notifyAll();
    }

    synchronized void redirect(String host, int port)
    {
        _complete = true;
        _redirected = true;
        this._host = host;
        this._port = port;
    }

    synchronized void failed(RuntimeException e)
    {
        _error = e;
        _complete = true;
    }

    synchronized boolean waitUntilOpen() throws InterruptedException
    {
        while (!_complete)
        {
            wait();
        }
        if (_error != null)
        {
            throw _error;
        }
        return !_redirected;
    }

    synchronized boolean isOpened()
    {
        return _complete;
    }

    String getHost()
    {
        return _host;
    }

    int getPort()
    {
        return _port;
    }
}
