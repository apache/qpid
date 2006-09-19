/*
 *   @(#) $Id: SocketSessionImpl.java 398039 2006-04-28 23:36:27Z proyal $
 *
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.qpid.bio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

/**
 * A simpler alternative to the non-blocking enabled SocketChannel for
 * use with blocking io only. Not threadsafe.
 */
class SimpleSocketChannel implements ByteChannel
{
    private final Socket socket;
    private final OutputStream out;
    private final InputStream in;
    private final byte[] buffer = new byte[2048];

    SimpleSocketChannel(Socket socket) throws IOException
    {
        this.socket = socket;
        out = socket.getOutputStream();
        in = socket.getInputStream();
    }

    Socket socket()
    {
        return socket;
    }

    public int read(ByteBuffer dst) throws IOException
    {
        if (dst == null)
        {
            throw new NullPointerException("Null buffer passed into read");
        }
        int read = in.read(buffer, 0, Math.min(buffer.length, dst.limit() - dst.position()));
        if (read > 0)
        {
            dst.put(buffer, 0, read);
        }
        return read;
    }

    public int write(ByteBuffer dst) throws IOException
    {
        byte[] data = new byte[dst.remaining()];
        dst.get(data);
        out.write(data);
        return data.length;
    }

    public boolean isOpen()
    {
        return socket.isConnected();
    }

    public void close() throws IOException
    {
        socket.close();
    }
}
