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

import java.net.SocketAddress;
import java.security.Principal;

import junit.framework.TestCase;

import org.apache.qpid.transport.ByteBufferSender;
import org.apache.qpid.transport.network.NetworkConnection;
import org.apache.qpid.transport.network.TransportActivity;

public class IdleTimeoutTickerTest extends TestCase implements TransportActivity, NetworkConnection
{
    private IdleTimeoutTicker _ticker;
    private static final int DEFAULT_TIMEOUT = 567890;
    private long _lastReadTime;
    private long _lastWriteTime;
    private long _currentTime;
    private int _maxWriteIdle;
    private int _maxReadIdle;
    private boolean _readerIdle;
    private boolean _writerIdle;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _ticker = new IdleTimeoutTicker(this, DEFAULT_TIMEOUT);
        _ticker.setConnection(this);
        _readerIdle = false;
        _writerIdle = false;
        _lastReadTime = 0l;
        _lastWriteTime = 0l;
        _maxReadIdle = 0;
        _maxWriteIdle = 0;
    }

    public void testNoIdle() throws Exception
    {
        _maxReadIdle = 4;
        _maxWriteIdle = 2;
        _lastReadTime = 0;
        _lastWriteTime = 1500;
        _currentTime = 3000;
        // Current time = 3s,
        // last read = 0s, max read idle = 4s, should check in 1s
        // last write = 1.5s, max write idle = 2s, should check in 0.5s
        long nextTime = _ticker.tick(_currentTime);
        assertEquals("Incorrect next tick calculation", 500l, nextTime);
        assertFalse("Incorrectly caused reader idle", _readerIdle);
        assertFalse("Incorrectly caused writer idle", _writerIdle);


        // Current time = 3.4s,
        // last read = 0s, max read idle = 4s, should check in 0.6s
        // last write = 3.1s, max write idle = 2s, should check in 1.7s
        _lastWriteTime = 3100;
        _currentTime = 3400;
        nextTime = _ticker.tick(_currentTime);
        assertEquals("Incorrect next tick calculation", 600l, nextTime);
        assertFalse("Incorrectly caused reader idle", _readerIdle);
        assertFalse("Incorrectly caused writer idle", _writerIdle);

        _maxReadIdle = 0;
        nextTime = _ticker.tick(_currentTime);
        assertEquals("Incorrect next tick calculation", 1700l, nextTime);
        assertFalse("Incorrectly caused reader idle", _readerIdle);
        assertFalse("Incorrectly caused writer idle", _writerIdle);

        _maxWriteIdle = 0;
        nextTime = _ticker.tick(_currentTime);
        assertEquals("Incorrect next tick calculation", DEFAULT_TIMEOUT, nextTime);
        assertFalse("Incorrectly caused reader idle", _readerIdle);
        assertFalse("Incorrectly caused writer idle", _writerIdle);

    }

    public void testReaderIdle() throws Exception
    {
        _maxReadIdle = 4;
        _maxWriteIdle = 0;
        _lastReadTime = 0;
        _lastWriteTime = 2500;
        _currentTime = 4000;
        // Current time = 4s,
        // last read = 0s, max read idle = 4s, reader idle
        long nextTime = _ticker.tick(_currentTime);

        assertTrue(_readerIdle);
        assertFalse(_writerIdle);

        _readerIdle = false;

        // last write = 2.5s, max write idle = 2s, should check in 0.5s
        _maxWriteIdle = 2;
        nextTime = _ticker.tick(_currentTime);
        assertTrue(_readerIdle);
        assertFalse(_writerIdle);

        _readerIdle = false;
        // last write = 1.5s, max write idle = 2s, should check in 0.5s

        _lastWriteTime = 1500;
        nextTime = _ticker.tick(_currentTime);

        assertTrue(_readerIdle);
        assertTrue(_writerIdle);

    }

    public void testWriterIdle() throws Exception
    {
        _maxReadIdle = 0;
        _maxWriteIdle = 2;
        _lastReadTime = 0;
        _lastWriteTime = 1500;
        _currentTime = 4000;
        // Current time = 4s,
        // last write = 1.5s, max write idle = 2s, writer idle
        long nextTime = _ticker.tick(_currentTime);

        assertTrue(_writerIdle);
        assertFalse(_readerIdle);
        assertEquals(2000l,nextTime);

        _writerIdle = false;
        _lastWriteTime = 1500;
        _maxReadIdle = 5;

        nextTime = _ticker.tick(_currentTime);

        assertTrue(_writerIdle);
        assertFalse(_readerIdle);
        assertEquals(1000l,nextTime);

    }

        //-------------------------------------------------------------------------
    // Implement TransportActivity methods
    //-------------------------------------------------------------------------

    @Override
    public long getLastReadTime()
    {
        return _lastReadTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime;
    }

    @Override
    public void writerIdle()
    {
        _writerIdle = true;
        _lastWriteTime = _currentTime;
    }

    @Override
    public void readerIdle()
    {
        _readerIdle = true;
    }

    //-------------------------------------------------------------------------
    // Implement NetworkConnection methods
    // Only actually use those relating to idle timeouts
    //-------------------------------------------------------------------------

    @Override
    public ByteBufferSender getSender()
    {
        return null;
    }

    @Override
    public void start()
    {
    }

    @Override
    public void close()
    {
    }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return null;
    }

    @Override
    public SocketAddress getLocalAddress()
    {
        return null;
    }

    @Override
    public void setMaxWriteIdle(int sec)
    {
        _maxWriteIdle = sec;
    }

    @Override
    public void setMaxReadIdle(int sec)
    {
        _maxReadIdle = sec;
    }

    @Override
    public Principal getPeerPrincipal()
    {
        return null;
    }

    @Override
    public int getMaxReadIdle()
    {
        return _maxReadIdle;
    }

    @Override
    public int getMaxWriteIdle()
    {
        return _maxWriteIdle;
    }
}
