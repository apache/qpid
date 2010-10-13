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
package org.apache.qpid.transport.network.mina;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.CloseFuture;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.WriteFuture;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MinaSender
 */
public class MinaSender implements Sender<java.nio.ByteBuffer>
{
    private static final Logger _log = LoggerFactory.getLogger(MinaSender.class);
    
    private final IoSession _session;
    private WriteFuture _lastWrite;
    private int _idleTimeout = 0;

    public MinaSender(IoSession session)
    {
        _session = session;
    }

    public synchronized void send(java.nio.ByteBuffer buf)
    {
        if (_session.isClosing())
        {
            throw new TransportException("attempted to write to a closed socket");
        }
//        synchronized (this)
        {
            ByteBuffer mina = ByteBuffer.allocate(buf.capacity());
            mina.put(buf);
            mina.flip();
            flush();
            _lastWrite = _session.write(mina);
	        flush();
        }
    }

    public synchronized void flush()
    {
//        synchronized (this)
        {
	        if (_lastWrite != null)
	        {
	            _lastWrite.join();
	            if (!_lastWrite.isWritten())
	            {
	                throw new RuntimeException("Error flushing buffe");
	            }
	        }
        }
    }

    public void close()
    {
        // MINA will sometimes throw away in-progress writes when you ask it to close
        flush();
        CloseFuture closed = _session.close();
        closed.join();
    }
    
    public void setIdleTimeout(int i)
    {
        _idleTimeout = i;
        _session.setWriteTimeout(_idleTimeout);
    }
    
    public long getIdleTimeout()
    {
        return _idleTimeout;
    }
}
