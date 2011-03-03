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

import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.Receiver;
import org.apache.qpid.transport.network.Transport;
import org.apache.qpid.transport.util.Logger;

/**
 * IoNetworkHandler handles incoming data and sends it to the {@link Receiver}.
 */
public class IoNetworkHandler implements Runnable
{
    private static final Logger _log = Logger.get(IoNetworkHandler.class);

    private final Receiver<ByteBuffer> _receiver;
    private final int _bufSize;
    private final Socket _socket;

    public IoNetworkHandler(Socket socket, Receiver<ByteBuffer> receiver, int bufSize)
    {
        _socket = socket;
        _receiver = receiver;
        _bufSize = bufSize;
    }

    public void run()
    {
        final int threshold = _bufSize / 2;

        // I set the read buffer size simillar to SO_RCVBUF
        // Haven't tested with a lower value to see if it's better or worse
        byte[] buffer = new byte[_bufSize];
        try
        {
            InputStream in = _socket.getInputStream();
            int read = 0;
            int offset = 0;
            while ((read = in.read(buffer, offset, _bufSize-offset)) != -1)
            {
                if (read > 0)
                {
                    ByteBuffer b = ByteBuffer.wrap(buffer,offset,read);
                    _receiver.received(b);
                    offset+=read;
                    if (offset > threshold)
                    {
                        offset = 0;
                        buffer = new byte[_bufSize];
                    }
                }
            }
        }
        catch (Throwable t)
        {
            if (!(Transport.WINDOWS &&
                  t instanceof SocketException &&
                  t.getMessage().equalsIgnoreCase("socket closed") &&
                  _socket.isClosed()) && _socket.isConnected())
            {
                _receiver.exception(t);
            }
        }
        finally
        {
            _receiver.closed();
            try
            {
                _socket.close();
            }
            catch(Exception e)
            {
                _log.warn(e, "Error closing socket");
            }
        }
    }
}
