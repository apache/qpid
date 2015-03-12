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

package org.apache.qpid.transport.network.io;

import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.Ticker;
import org.apache.qpid.transport.network.TransportActivity;

public class IdleTimeoutTicker implements Ticker
{
    private final TransportActivity _transport;
    private final int _defaultTimeout;
    private NetworkConnection _connection;

    public IdleTimeoutTicker(TransportActivity transport, int defaultTimeout)
    {
        _transport = transport;
        _defaultTimeout = defaultTimeout;
    }

    @Override
    public int getTimeToNextTick(long currentTime)
    {
        long nextTime = -1;
        final long maxReadIdle = 1000l * _connection.getMaxReadIdle();

        if(maxReadIdle > 0)
        {
            nextTime = _transport.getLastReadTime() + maxReadIdle;
        }

        long maxWriteIdle = 1000l * _connection.getMaxWriteIdle();

        if(maxWriteIdle > 0)
        {
            long writeTime = _transport.getLastWriteTime() + maxWriteIdle;
            if(nextTime == -1l || writeTime < nextTime)
            {
                nextTime = writeTime;
            }
        }
        return nextTime == -1 ? _defaultTimeout : (int) (nextTime - currentTime);
    }

    @Override
    public int tick(long currentTime)
    {
        // writer Idle
        long maxWriteIdle = 1000l * _connection.getMaxWriteIdle();
        if(maxWriteIdle > 0 && maxWriteIdle+ _transport.getLastWriteTime() <= currentTime)
        {
            _transport.writerIdle();
        }
        // reader Idle
        final long maxReadIdle = 1000l * _connection.getMaxReadIdle();
        if(maxReadIdle > 0 && maxReadIdle+ _transport.getLastReadTime() <= currentTime)
        {

            _transport.readerIdle();
        }
        return getTimeToNextTick(currentTime);
    }

    public void setConnection(NetworkConnection connection)
    {
        _connection = connection;
    }
}
