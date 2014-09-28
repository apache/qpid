/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 *
 */

package org.apache.qpid.server.protocol.v0_8.handler;

import java.security.AccessControlException;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class QueuePurgeHandler implements StateAwareMethodListener<QueuePurgeBody>
{
    private static final QueuePurgeHandler _instance = new QueuePurgeHandler();

    public static QueuePurgeHandler getInstance()
    {
        return _instance;
    }

    private final boolean _failIfNotFound;

    public QueuePurgeHandler()
    {
        this(true);
    }

    public QueuePurgeHandler(boolean failIfNotFound)
    {
        _failIfNotFound = failIfNotFound;
    }

    public void methodReceived(final AMQProtocolSession<?> connection,
                               QueuePurgeBody body,
                               int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = connection.getVirtualHost();

        AMQChannel channel = connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, connection.getMethodRegistry());
        }
        AMQQueue queue;
        if(body.getQueue() == null)
        {

           //get the default queue on the channel:
           queue = channel.getDefaultQueue();

            if(queue == null)
            {
                if(_failIfNotFound)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,"No queue specified.", connection.getMethodRegistry());
                }
            }
        }
        else
        {
            queue = virtualHost.getQueue(body.getQueue().toString());
        }

        if(queue == null)
        {
            if(_failIfNotFound)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.",
                                               connection.getMethodRegistry());
            }
        }
        else
        {
                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue is exclusive, but not created on this Connection.", connection.getMethodRegistry());
                }

            long purged = 0;
            try
            {
                purged = queue.clearQueue();
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage(), connection.getMethodRegistry());
            }


            if(!body.getNowait())
                {
                    channel.sync();
                    MethodRegistry methodRegistry = connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createQueuePurgeOkBody(purged);
                    connection.writeFrame(responseBody.generateFrame(channelId));

                }
        }
    }
}
