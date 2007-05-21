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
package org.apache.qpid.server.handler;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.messageStore.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.exception.InternalErrorException;
import org.apache.qpid.server.exception.QueueDoesntExistException;

public class QueueDeleteHandler implements StateAwareMethodListener<QueueDeleteBody>
{
    private static final QueueDeleteHandler _instance = new QueueDeleteHandler();

    public static QueueDeleteHandler getInstance()
    {
        return _instance;
    }

    private final boolean _failIfNotFound;

    public QueueDeleteHandler()
    {
        this(true);
    }

    public QueueDeleteHandler(boolean failIfNotFound)
    {
        _failIfNotFound = failIfNotFound;

    }

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<QueueDeleteBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();
        MessageStore store = virtualHost.getMessageStore();

        QueueDeleteBody body = evt.getMethod();
        AMQQueue queue;
        if (body.queue == null)
        {
            AMQChannel channel = session.getChannel(evt.getChannelId());

            if (channel == null)
            {
                throw body.getChannelNotFoundException(evt.getChannelId());
            }

            //get the default queue on the channel:            
            queue = channel.getDefaultQueue();
        }
        else
        {
            queue = queueRegistry.getQueue(body.queue);
        }

        if (queue == null)
        {
            if (_failIfNotFound)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.queue + " does not exist.");
            }
        }
        else
        {
            if (body.ifEmpty && !queue.isEmpty())
            {
                throw body.getChannelException(AMQConstant.IN_USE, "Queue: " + body.queue + " is not empty.");
            }
            else if (body.ifUnused && !queue.isUnused())
            {
                // TODO - Error code
                throw body.getChannelException(AMQConstant.IN_USE, "Queue: " + body.queue + " is still used.");

            }
            else
            {
                int purged = queue.delete(body.ifUnused, body.ifEmpty);

                if (queue.isDurable())
                {
                    try
                    {
                        store.destroyQueue(queue);
                    } catch (Exception e)
                    {
                      throw new AMQException(null, "problem when destroying queue " + queue, e);
                    }
                }
                
                // AMQP version change: Hardwire the version to 0-8 (major=8, minor=0)
                // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
                // Be aware of possible changes to parameter order as versions change.
                session.writeFrame(QueueDeleteOkBody.createAMQFrame(evt.getChannelId(),
                                                                    (byte) 8, (byte) 0,    // AMQP version (major, minor)
                                                                    purged));    // messageCount
            }
        }
    }
}
