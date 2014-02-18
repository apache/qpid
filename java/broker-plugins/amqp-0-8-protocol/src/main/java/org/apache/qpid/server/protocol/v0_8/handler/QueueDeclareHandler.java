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
package org.apache.qpid.server.protocol.v0_8.handler;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.security.AccessControlException;
import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public class QueueDeclareHandler implements StateAwareMethodListener<QueueDeclareBody>
{
    private static final Logger _logger = Logger.getLogger(QueueDeclareHandler.class);

    private static final QueueDeclareHandler _instance = new QueueDeclareHandler();

    public static QueueDeclareHandler getInstance()
    {
        return _instance;
    }

    public void methodReceived(AMQStateManager stateManager, QueueDeclareBody body, int channelId) throws AMQException
    {
        final AMQProtocolSession protocolConnection = stateManager.getProtocolSession();
        final AMQSessionModel session = protocolConnection.getChannel(channelId);
        VirtualHost virtualHost = protocolConnection.getVirtualHost();

        final AMQShortString queueName;

        // if we aren't given a queue name, we create one which we return to the client
        if ((body.getQueue() == null) || (body.getQueue().length() == 0))
        {
            queueName = createName();
        }
        else
        {
            queueName = body.getQueue().intern();
        }

        AMQQueue queue;

        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        AMQChannel channel = protocolConnection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }

        if(body.getPassive())
        {
            queue = virtualHost.getQueue(queueName.toString());
            if (queue == null)
            {
                String msg = "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").";
                throw body.getChannelException(AMQConstant.NOT_FOUND, msg);
            }
            else
            {
                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue " + queue.getName() + " is exclusive, but not created on this Connection.");
                }

                //set this as the default queue on the channel:
                channel.setDefaultQueue(queue);
            }
        }
        else
        {

            try
            {

                queue = createQueue(channel, queueName, body, virtualHost, protocolConnection);

            }
            catch(QueueExistsException qe)
            {

                queue = qe.getExistingQueue();

                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue " + queue.getName() + " is exclusive, but not created on this Connection.");
                }
                else if(queue.isExclusive() != body.getExclusive())
                {

                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                            "Cannot re-declare queue '" + queue.getName() + "' with different exclusivity (was: "
                            + queue.isExclusive() + " requested " + body.getExclusive() + ")");
                }
                else if((body.getAutoDelete() && queue.getLifetimePolicy() != LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS)
                    || (!body.getAutoDelete() && queue.getLifetimePolicy() != ((body.getExclusive() && !body.getDurable()) ? LifetimePolicy.DELETE_ON_CONNECTION_CLOSE : LifetimePolicy.PERMANENT)))
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                      "Cannot re-declare queue '" + queue.getName() + "' with different lifetime policy (was: "
                                                        + queue.getLifetimePolicy() + " requested autodelete: " + body.getAutoDelete() + ")");
                }
                else if(queue.isDurable() != body.getDurable())
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                      "Cannot re-declare queue '" + queue.getName() + "' with different durability (was: "
                                                        + queue.isDurable() + " requested " + body.getDurable() + ")");
                }

            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED, e.getMessage());
            }

            //set this as the default queue on the channel:
            channel.setDefaultQueue(queue);
        }

        if (!body.getNowait())
        {
            channel.sync();
            MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
            QueueDeclareOkBody responseBody =
                    methodRegistry.createQueueDeclareOkBody(queueName,
                                                            queue.getMessageCount(),
                                                            queue.getConsumerCount());
            protocolConnection.writeFrame(responseBody.generateFrame(channelId));

            _logger.info("Queue " + queueName + " declared successfully");
        }
    }

    protected AMQShortString createName()
    {
        return new AMQShortString("tmp_" + UUID.randomUUID());
    }

    protected AMQQueue createQueue(final AMQChannel channel, final AMQShortString queueName,
                                   QueueDeclareBody body,
                                   final VirtualHost virtualHost,
                                   final AMQProtocolSession session)
            throws AMQException, QueueExistsException
    {

        final boolean durable = body.getDurable();
        final boolean autoDelete = body.getAutoDelete();
        final boolean exclusive = body.getExclusive();


        Map<String, Object> attributes =
                QueueArgumentsConverter.convertWireArgsToModel(FieldTable.convertToMap(body.getArguments()));
        final String queueNameString = AMQShortString.toString(queueName);
        attributes.put(Queue.NAME, queueNameString);
        attributes.put(Queue.ID, UUIDGenerator.generateQueueUUID(queueNameString, virtualHost.getName()));
        attributes.put(Queue.DURABLE, durable);

        LifetimePolicy lifetimePolicy;
        ExclusivityPolicy exclusivityPolicy;

        if(exclusive)
        {
            lifetimePolicy = autoDelete
                    ? LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS
                    : durable ? LifetimePolicy.PERMANENT : LifetimePolicy.DELETE_ON_CONNECTION_CLOSE;
            exclusivityPolicy = durable ? ExclusivityPolicy.CONTAINER : ExclusivityPolicy.CONNECTION;
        }
        else
        {
            lifetimePolicy = autoDelete ? LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS : LifetimePolicy.PERMANENT;
            exclusivityPolicy = ExclusivityPolicy.NONE;
        }

        attributes.put(Queue.EXCLUSIVE, exclusivityPolicy);
        attributes.put(Queue.LIFETIME_POLICY, lifetimePolicy);


        final AMQQueue queue = virtualHost.createQueue(channel, attributes);

        return queue;
    }
}
