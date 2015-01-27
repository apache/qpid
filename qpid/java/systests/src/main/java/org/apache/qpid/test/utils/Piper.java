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
package org.apache.qpid.test.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

public final class Piper extends Thread
{
    private static final Logger LOGGER = Logger.getLogger(Piper.class);

    private final BufferedReader _in;
    private final Logger _out;
    private final String _ready;
    private final CountDownLatch _latch;
    private final String _stopped;
    private volatile boolean _seenReady;
    private volatile String _stopLine;

    public Piper(InputStream in, String ready, String stopped, String threadName, String loggerName)
    {
        super(threadName);
        _in = new BufferedReader(new InputStreamReader(in));
        _out = Logger.getLogger(loggerName);
        _ready = ready;
        _stopped = stopped;
        _seenReady = false;

        if (this._ready != null && !this._ready.equals(""))
        {
            this._latch = new CountDownLatch(1);
        }
        else
        {
            this._latch = null;
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException
    {
        if (_latch == null)
        {
            return true;
        }
        else
        {
            _latch.await(timeout, unit);
            return _seenReady;
        }
    }

    public void run()
    {
        try
        {
            String line;
            while ((line = _in.readLine()) != null)
            {
                _out.info(line);

                if (_latch != null && line.contains(_ready))
                {
                    _seenReady = true;
                    _latch.countDown();
                }

                if (!_seenReady && line.contains(_stopped))
                {
                    _stopLine = line;
                }
            }
        }
        catch (IOException e)
        {
            LOGGER.warn(e.getMessage() + " : Broker stream from unexpectedly closed; last log lines written by Broker may be lost.");
        }
        finally
        {
            if (_latch != null)
            {
                _latch.countDown();
            }
        }
    }

    public String getStopLine()
    {
        return _stopLine;
    }

    String getReady()
    {
        return _ready;
    }
}