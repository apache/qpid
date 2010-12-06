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
import org.apache.qpid.transport.network.Transport;

/**
 * MinaSender
 */
public class MinaSender implements Sender<java.nio.ByteBuffer>
{
    private final IoSession _session;
    private int _idle = 0;
    private WriteFuture _written;

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
        _written = _session.write(ByteBuffer.wrap(buf));
    }

    public synchronized void flush()
    {
        if (_written != null)
        {
	        _written.join(Transport.DEFAULT_TIMEOUT);
	        if (!_written.isWritten())
	        {
	            throw new TransportException("Error flushing data buffer");
	        }
        }
    }

    public synchronized void close()
    {
        flush();
        CloseFuture closed = _session.close();
        closed.join();
    }
    
    public void setIdleTimeout(int idle)
    {
        _idle = idle;
        _session.setWriteTimeout(_idle);
    }
    
    public long getIdleTimeout()
    {
        return _idle;
    }
}
