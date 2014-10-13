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
package org.apache.qpid.server.protocol.v0_8;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;

import org.apache.log4j.Logger;

import org.apache.qpid.codec.MarkableDataInput;
import org.apache.qpid.codec.ServerDecoder;
import org.apache.qpid.framing.AMQFrameDecodingException;
import org.apache.qpid.server.util.ServerScopedRuntimeException;

public class BrokerDecoder extends ServerDecoder
{
    private static final Logger _logger = Logger.getLogger(BrokerDecoder.class);
    private final AMQProtocolEngine _connection;
    /**
     * Creates a new AMQP decoder.
     *
     * @param connection
     */
    public BrokerDecoder(final AMQProtocolEngine connection)
    {
        super(connection);
        _connection = connection;
    }

    @Override
    protected void processFrame(final int channelId, final byte type, final long bodySize, final MarkableDataInput in)
            throws AMQFrameDecodingException, IOException
    {
        long startTime = 0;
        if (_logger.isDebugEnabled())
        {
            startTime = System.currentTimeMillis();
        }
        Subject subject;
        AMQChannel channel = _connection.getChannel(channelId);
        if(channel == null)
        {
            subject = _connection.getSubject();
        }
        else
        {
            _connection.channelRequiresSync(channel);

            subject = channel.getSubject();
        }
        try
        {
            Subject.doAs(subject, new PrivilegedExceptionAction<Object>()
            {
                @Override
                public Void run() throws IOException, AMQFrameDecodingException
                {
                    doProcessFrame(channelId, type, bodySize, in);
                    return null;
                }
            });
            if(_logger.isDebugEnabled())
            {
                _logger.debug("Frame handled in " + (System.currentTimeMillis() - startTime) + " ms.");
            }

        }
        catch (PrivilegedActionException e)
        {
            Throwable cause = e.getCause();
            if(cause instanceof IOException)
            {
                throw (IOException) cause;
            }
            else if(cause instanceof AMQFrameDecodingException)
            {
                throw (AMQFrameDecodingException) cause;
            }
            else if(cause instanceof RuntimeException)
            {
                throw (RuntimeException) cause;
            }
            else throw new ServerScopedRuntimeException(cause);
        }

    }


    private void doProcessFrame(final int channelId, final byte type, final long bodySize, final MarkableDataInput in)
            throws AMQFrameDecodingException, IOException
    {
        super.processFrame(channelId, type, bodySize, in);

    }

}
