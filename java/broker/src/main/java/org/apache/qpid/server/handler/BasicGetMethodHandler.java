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

package org.apache.qpid.server.handler;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.BasicGetBody;
import org.apache.qpid.framing.BasicGetEmptyBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.AMQChannel;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQQueueImpl;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.access.Permission;
import org.apache.qpid.server.state.AMQStateManager;
import org.apache.qpid.server.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class BasicGetMethodHandler implements StateAwareMethodListener<BasicGetBody>
{
    private static final Logger _log = Logger.getLogger(BasicGetMethodHandler.class);

    private static final BasicGetMethodHandler _instance = new BasicGetMethodHandler();

    public static BasicGetMethodHandler getInstance()
    {
        return _instance;
    }

    private BasicGetMethodHandler()
    {
    }

    public void methodReceived(AMQStateManager stateManager, BasicGetBody body, int channelId) throws AMQException
    {
        AMQProtocolSession session = stateManager.getProtocolSession();


        VirtualHost vHost = session.getVirtualHost();

        AMQChannel channel = session.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        else
        {
            AMQQueue queue = body.getQueue() == null ? channel.getDefaultQueue() : vHost.getQueueRegistry().getQueue(body.getQueue());
            if (queue == null)
            {
                _log.info("No queue for '" + body.getQueue() + "'");
                if(body.getQueue()!=null)
                {
                    throw body.getConnectionException(AMQConstant.NOT_FOUND,
                                                      "No such queue, '" + body.getQueue()+ "'");
                }
                else
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "No queue name provided, no default queue defined.");
                }
            }
            else
            {

                //Perform ACLs
                vHost.getAccessManager().authorise(session, Permission.CONSUME, body, queue);

                if (!queue.performGet(session, channel, !body.getNoAck()))
                {
                    MethodRegistry methodRegistry = session.getMethodRegistry();
                    // TODO - set clusterId
                    BasicGetEmptyBody responseBody = methodRegistry.createBasicGetEmptyBody(null);


                    session.writeFrame(responseBody.generateFrame(channelId));
                }
            }
        }
    }
}
