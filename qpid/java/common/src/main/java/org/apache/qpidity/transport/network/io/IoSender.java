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
package org.apache.qpidity.transport.network.io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.qpidity.transport.Sender;

public class IoSender implements Sender<java.nio.ByteBuffer>
{
    private final Object lock = new Object();
    private Socket _socket;
    private OutputStream _outStream;

    public IoSender(Socket socket)
    {
        this._socket = socket;
        try
        {
            _outStream = _socket.getOutputStream();
        }
        catch(IOException e)
        {
            throw new RuntimeException("Error getting output stream for socket",e);
        }
    }

    /*
     * Currently I don't implement any in memory buffering
     * and just write straight to the wire.
     * I want to experiment with buffering and see if I can
     * get more performance, all though latency will suffer
     * a bit.
     */
    public void send(java.nio.ByteBuffer buf)
    {
        write(buf);
    }

    public void flush()
    {
        // pass
    }

    /* The extra copying sucks.
     * If I know for sure that the buf is backed
     * by an array then I could do buf.array()
     */
    private void write(java.nio.ByteBuffer buf)
    {
        byte[] array = new byte[buf.remaining()];
        buf.get(array);
        if( _socket.isConnected())
        {
            synchronized (lock)
            {
                try
                {
                    _outStream.write(array);
                }
                catch(Exception e)
                {
                    e.fillInStackTrace();
                    throw new RuntimeException("Error trying to write to the socket",e);
                }
            }
        }
        else
        {
            throw new RuntimeException("Trying to write on a closed socket");
        }
    }

    /*
     * Haven't used this, but the intention is
     * to experiment with it in the future.
     * Also need to make sure the buffer size
     * is configurable
     */
    public void setStartBatching()
    {
    }

    public void close()
    {
        synchronized (lock)
        {
            try
            {
                _socket.close();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
