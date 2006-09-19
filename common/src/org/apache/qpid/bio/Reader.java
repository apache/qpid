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

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoHandler;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedByInterruptException;

class Reader implements Runnable
{
    private final IoHandler handler;
    private final SocketSessionImpl session;
    private final ByteChannel channel;
    private volatile boolean stopped;

    Reader(IoHandler handler, SocketSessionImpl session)
    {
        this.handler = handler;
        this.session = session;
        channel = session.getChannel();
    }

    void stop()
    {
        stopped = true;
    }

    public void run()
    {
        while (!stopped)
        {
            try
            {
                ByteBuffer buffer = ByteBuffer.allocate(session.getReadBufferSize());
                int read = channel.read(buffer.buf());
                if(read > 0)
                {
                    buffer.flip();
                    ((SocketFilterChain) session.getFilterChain()).messageReceived(session, buffer);
                }
                else
                {
                    stopped = true;
                }
            }
            catch (ClosedByInterruptException e)
            {
                stopped = true;
            }
            catch (IOException e)
            {
                if (!stopped)
                {
                    signalException(e);
                    session.close();
                }
            }
            catch (Exception e)
            {
                if (!stopped)
                {
                    signalException(e);
                }
            }
        }
    }

    private void signalException(Exception e)
    {
        try
        {
            handler.exceptionCaught(session, e);
        }
        catch (Exception e2)
        {
            e.printStackTrace();
        }
    }
}
