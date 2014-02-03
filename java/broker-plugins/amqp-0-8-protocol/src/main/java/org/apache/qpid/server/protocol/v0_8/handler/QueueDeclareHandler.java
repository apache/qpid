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
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;
import java.util.UUID;
import org.apache.qpid.server.virtualhost.plugins.QueueExistsException;

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
                AMQSessionModel owningSession = queue.getExclusiveOwningSession();
                if (queue.isExclusive() && !queue.isDurable()
                    && (owningSession == null || owningSession.getConnectionModel() != protocolConnection))
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

                queue = createQueue(queueName, body, virtualHost, protocolConnection);
                queue.setAuthorizationHolder(protocolConnection);

                if (body.getExclusive())
                {
                    queue.setExclusiveOwningSession(protocolConnection.getChannel(channelId));
                    queue.setAuthorizationHolder(protocolConnection);

                    if(!body.getDurable())
                    {
                        final AMQQueue q = queue;
                        final AMQProtocolSession.Task sessionCloseTask = new AMQProtocolSession.Task()
                        {
                            public void doTask(AMQProtocolSession session) throws AMQException
                            {
                                q.setExclusiveOwningSession(null);
                            }
                        };
                        protocolConnection.addSessionCloseTask(sessionCloseTask);
                        queue.addQueueDeleteTask(new Action<AMQQueue>() {
                            public void performAction(AMQQueue queue)
                            {
                                protocolConnection.removeSessionCloseTask(sessionCloseTask);
                            }
                        });
                    }
                }

            }
            catch(QueueExistsException qe)
            {

                queue = qe.getExistingQueue();
                AMQSessionModel owningSession = queue.getExclusiveOwningSession();

                if (queue.isExclusive() && !queue.isDurable() && (owningSession == null || owningSession.getConnectionModel() != protocolConnection))
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
                else if (body.getExclusive() && !(queue.isDurable() ? String.valueOf(queue.getOwner()).equals(session.getClientID()) : (owningSession == null || owningSession.getConnectionModel() == protocolConnection)))
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS, "Cannot declare queue('" + queueName + "'), "
                                                                               + "as exclusive queue with same name "
                                                                               + "declared on another client ID('"
                                                                               + queue.getOwner() + "') your clientID('" + session.getClientID() + "')");

                }
                else if(queue.isAutoDelete() != body.getAutoDelete())
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                      "Cannot re-declare queue '" + queue.getName() + "' with different auto-delete (was: "
                                                        + queue.isAutoDelete() + " requested " + body.getAutoDelete() + ")");
                }
                else if(queue.isDurable() != body.getDurable())
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                      "Cannot re-declare queue '" + queue.getName() + "' with different durability (was: "
                                                        + queue.isDurable() + " requested " + body.getDurable() + ")");
                }

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

    protected AMQQueue createQueue(final AMQShortString queueName,
                                   QueueDeclareBody body,
                                   final VirtualHost virtualHost,
                                   final AMQProtocolSession session)
            throws AMQException
    {

        final boolean durable = body.getDurable();
        final boolean autoDelete = body.getAutoDelete();
        final boolean exclusive = body.getExclusive();

        String owner = exclusive ? AMQShortString.toString(session.getContextKey()) : null;

        Map<String, Object> arguments =
                QueueArgumentsConverter.convertWireArgsToModel(FieldTable.convertToMap(body.getArguments()));
        String queueNameString = AMQShortString.toString(queueName);
        final UUID id = UUIDGenerator.generateQueueUUID(queueNameString, virtualHost.getName());

        final AMQQueue queue = virtualHost.createQueue(id, queueNameString, durable, owner, autoDelete,
                exclusive, autoDelete, arguments);

        if (exclusive && !durable)
        {
            final AMQProtocolSession.Task deleteQueueTask =
                    new AMQProtocolSession.Task()
                    {
                        public void doTask(AMQProtocolSession session) throws AMQException
                        {
                            if (virtualHost.getQueue(queueName.toString()) == queue)
                            {
                                virtualHost.removeQueue(queue);
                            }
                        }
                    };

            session.addSessionCloseTask(deleteQueueTask);

            queue.addQueueDeleteTask(new Action<AMQQueue>()
            {
                public void performAction(AMQQueue queue)
                {
                    session.removeSessionCloseTask(deleteQueueTask);
                }
            });
        }

        return queue;
    }
}
