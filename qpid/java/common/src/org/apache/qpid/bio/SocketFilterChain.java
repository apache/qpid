/*
 *   @(#) $Id: SocketFilterChain.java 398039 2006-04-28 23:36:27Z proyal $
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
import org.apache.mina.common.IoFilter.WriteRequest;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.support.AbstractIoFilterChain;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;

/**
 */
class SocketFilterChain extends AbstractIoFilterChain
{

    SocketFilterChain(IoSession parent)
    {
        super(parent);
    }

    protected void doWrite(IoSession session, WriteRequest writeRequest) throws Exception
    {
        SocketSessionImpl s = (SocketSessionImpl) session;

        //write to socket
        try
        {
            s.getChannel().write(((ByteBuffer) writeRequest.getMessage()).buf());

            //notify of completion
            writeRequest.getFuture().setWritten(true);
        }
        catch(ClosedByInterruptException e)
        {
            writeRequest.getFuture().setWritten(false);
        }
    }

    protected void doClose(IoSession session) throws IOException
    {
        SocketSessionImpl s = (SocketSessionImpl) session;
        s.shutdown();
    }
}
