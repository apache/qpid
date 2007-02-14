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
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.QueuePurgeBody;
import org.apache.qpid.framing.QueuePurgeOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.virtualhost.VirtualHost;

//import org.apache.log4j.Logger;

public class QueuePurgeHandler implements StateAwareMethodListener<QueuePurgeBody>
{
    //private static final Logger _log = Logger.getLogger(QueuePurgeHandler.class);
    
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

    public void methodReceived(AMQStateManager stateManager, AMQMethodEvent<QueuePurgeBody> evt) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();
        VirtualHost virtualHost = session.getVirtualHost();
        QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

        QueuePurgeBody body = evt.getMethod();
        AMQQueue queue;
        if(body.queue == null)
        {
            queue = session.getChannel(evt.getChannelId()).getDefaultQueue();
            if(queue == null)
            {
                if(_failIfNotFound)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED.getCode(),"No queue specified.");
                }

            }
        }
        else
        {
            queue = queueRegistry.getQueue(body.queue);
        }

        if(queue == null)
        {
            if(_failIfNotFound)
            {
                throw body.getChannelException(404, "Queue " + body.queue + " does not exist.");
            }
        }
        else
        {
            AMQChannel channel = session.getChannel(evt.getChannelId());
            long purged = queue.clearQueue(channel.getStoreContext());
            
            if(!body.nowait)
            {
                AMQMethodBody response = QueuePurgeOkBody.createMethodBody(
                    session.getProtocolMajorVersion(), // AMQP major version
                    session.getProtocolMinorVersion(), // AMQP minor version
                    purged);
                session.writeResponse(evt, response);
            }
        }
    }
}
