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
import java.util.EnumSet;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicGetBody;
import org.apache.qpid.framing.BasicGetEmptyBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.flow.MessageOnlyCreditManager;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.protocol.v0_8.AMQChannel;
import org.apache.qpid.server.protocol.v0_8.AMQMessage;
import org.apache.qpid.server.protocol.v0_8.AMQProtocolSession;
import org.apache.qpid.server.protocol.v0_8.ClientDeliveryMethod;
import org.apache.qpid.server.protocol.v0_8.ConsumerTarget_0_8;
import org.apache.qpid.server.protocol.v0_8.RecordDeliveryMethod;
import org.apache.qpid.server.protocol.v0_8.state.AMQStateManager;
import org.apache.qpid.server.protocol.v0_8.state.StateAwareMethodListener;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

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
        AMQProtocolSession protocolConnection = stateManager.getProtocolSession();


        VirtualHostImpl vHost = protocolConnection.getVirtualHost();

        AMQChannel channel = protocolConnection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId);
        }
        else
        {
            channel.sync();
            AMQQueue queue = body.getQueue() == null ? channel.getDefaultQueue() : vHost.getQueue(body.getQueue().toString());
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

                try
                {
                    if (!performGet(queue,protocolConnection, channel, !body.getNoAck()))
                    {
                        MethodRegistry methodRegistry = protocolConnection.getMethodRegistry();
                        // TODO - set clusterId
                        BasicGetEmptyBody responseBody = methodRegistry.createBasicGetEmptyBody(null);


                        protocolConnection.writeFrame(responseBody.generateFrame(channelId));
                    }
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      e.getMessage());
                }
                catch (MessageSource.ExistingExclusiveConsumer e)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue has an exclusive consumer");
                }
                catch (MessageSource.ExistingConsumerPreventsExclusive e)
                {
                    throw body.getConnectionException(AMQConstant.INTERNAL_ERROR,
                                                      "The GET request has been evaluated as an exclusive consumer, " +
                                                      "this is likely due to a programming error in the Qpid broker");
                }
                catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue has an incompatible exclusivit policy");
                }
            }
        }
    }

    public static boolean performGet(final AMQQueue queue,
                                     final AMQProtocolSession session,
                                     final AMQChannel channel,
                                     final boolean acks)
            throws AMQException, MessageSource.ExistingConsumerPreventsExclusive,
                   MessageSource.ExistingExclusiveConsumer, MessageSource.ConsumerAccessRefused
    {

        final FlowCreditManager singleMessageCredit = new MessageOnlyCreditManager(1L);

        final GetDeliveryMethod getDeliveryMethod =
                new GetDeliveryMethod(singleMessageCredit, session, channel, queue);
        final RecordDeliveryMethod getRecordMethod = new RecordDeliveryMethod()
        {

            public void recordMessageDelivery(final ConsumerImpl sub, final MessageInstance entry, final long deliveryTag)
            {
                channel.addUnacknowledgedMessage(entry, deliveryTag, null);
            }
        };

        ConsumerTarget_0_8 target;
        EnumSet<ConsumerImpl.Option> options = EnumSet.of(ConsumerImpl.Option.TRANSIENT, ConsumerImpl.Option.ACQUIRES,
                                                          ConsumerImpl.Option.SEES_REQUEUES);
        if(acks)
        {

            target = ConsumerTarget_0_8.createAckTarget(channel,
                                                        AMQShortString.EMPTY_STRING, null,
                                                        singleMessageCredit, getDeliveryMethod, getRecordMethod);
        }
        else
        {
            target = ConsumerTarget_0_8.createGetNoAckTarget(channel,
                                                          AMQShortString.EMPTY_STRING, null,
                                                          singleMessageCredit, getDeliveryMethod, getRecordMethod);
        }

        ConsumerImpl sub = queue.addConsumer(target, null, AMQMessage.class, "", options);
        sub.flush();
        sub.close();
        return(getDeliveryMethod.hasDeliveredMessage());


    }


    private static class GetDeliveryMethod implements ClientDeliveryMethod
    {

        private final FlowCreditManager _singleMessageCredit;
        private final AMQProtocolSession _session;
        private final AMQChannel _channel;
        private final AMQQueue _queue;
        private boolean _deliveredMessage;

        public GetDeliveryMethod(final FlowCreditManager singleMessageCredit,
                                 final AMQProtocolSession session,
                                 final AMQChannel channel, final AMQQueue queue)
        {
            _singleMessageCredit = singleMessageCredit;
            _session = session;
            _channel = channel;
            _queue = queue;
        }

        @Override
        public long deliverToClient(final ConsumerImpl sub, final ServerMessage message,
                                    final InstanceProperties props, final long deliveryTag)
        {
            _singleMessageCredit.useCreditForMessage(message.getSize());
            long size =_session.getProtocolOutputConverter().writeGetOk(message,
                                                            props,
                                                            _channel.getChannelId(),
                                                            deliveryTag,
                                                            _queue.getQueueDepthMessages());

            _deliveredMessage = true;
            return size;
        }

        public boolean hasDeliveredMessage()
        {
            return _deliveredMessage;
        }
    }
}
