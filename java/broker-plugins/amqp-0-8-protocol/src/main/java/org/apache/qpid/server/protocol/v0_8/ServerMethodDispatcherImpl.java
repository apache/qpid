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

import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.Subject;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

import org.apache.log4j.Logger;

import org.apache.qpid.AMQConnectionException;
import org.apache.qpid.AMQException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.*;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.consumer.ConsumerImpl;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.filter.AMQInvalidArgumentException;
import org.apache.qpid.server.flow.FlowCreditManager;
import org.apache.qpid.server.flow.MessageOnlyCreditManager;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageSource;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.NoFactoryForTypeException;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.UnknownConfiguredObjectException;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.security.auth.SubjectAuthenticationResult;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class ServerMethodDispatcherImpl implements MethodDispatcher
{
    private static final Logger _logger = Logger.getLogger(ServerMethodDispatcherImpl.class);

    private final AMQProtocolSession<?> _connection;


    private static interface ChannelAction
    {
        void onChannel(ChannelMethodProcessor channel);
    }

    public static MethodDispatcher createMethodDispatcher(AMQProtocolSession<?> connection)
    {
        return new ServerMethodDispatcherImpl(connection);
    }


    public ServerMethodDispatcherImpl(AMQProtocolSession<?> connection)
    {
        _connection = connection;
    }


    protected final AMQProtocolSession<?> getConnection()
    {
        return _connection;
    }

    private void processChannelMethod(int channelId, final ChannelAction action)
    {
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            // TODO throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        else
        {
            Subject.doAs(channel.getSubject(), new PrivilegedAction<Void>()
            {
                @Override
                public Void run()
                {
                    action.onChannel(channel.getMethodProcessor());
                    return null;
                }
            });
        }

    }

    public boolean dispatchAccessRequest(AccessRequestBody body, int channelId) throws AMQException
    {
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        if(ProtocolVersion.v0_91.equals(_connection.getProtocolVersion()) )
        {
            throw new AMQException(AMQConstant.COMMAND_INVALID, "AccessRequest not present in AMQP versions other than 0-8, 0-9");
        }

        // We don't implement access control class, but to keep clients happy that expect it
        // always use the "0" ticket.
        AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);
        channel.sync();
        _connection.writeFrame(response.generateFrame(channelId));
        return true;
    }

    public boolean dispatchBasicAck(BasicAckBody body, int channelId) throws AMQException
    {

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Ack(Tag:" + body.getDeliveryTag() + ":Mult:" + body.getMultiple() + ") received on channel " + channelId);
        }

        final AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        // this method throws an AMQException if the delivery tag is not known
        channel.acknowledgeMessage(body.getDeliveryTag(), body.getMultiple());
        return true;
    }

    public boolean dispatchBasicCancel(BasicCancelBody body, int channelId) throws AMQException
    {
        final AMQChannel channel = _connection.getChannel(channelId);


        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("BasicCancel: for:" + body.getConsumerTag() +
                       " nowait:" + body.getNowait());
        }

        channel.unsubscribeConsumer(body.getConsumerTag());
        if (!body.getNowait())
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            BasicCancelOkBody cancelOkBody = methodRegistry.createBasicCancelOkBody(body.getConsumerTag());
            channel.sync();
            _connection.writeFrame(cancelOkBody.generateFrame(channelId));
        }
        return true;
    }

    public boolean dispatchBasicConsume(BasicConsumeBody body, int channelId) throws AMQException
    {
        AMQChannel channel = _connection.getChannel(channelId);
        VirtualHostImpl<?,?,?> vHost = _connection.getVirtualHost();

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        else
        {
            channel.sync();
            String queueName = body.getQueue() == null ? null : body.getQueue().asString();
            if (_logger.isDebugEnabled())
            {
                _logger.debug("BasicConsume: from '" + queueName +
                              "' for:" + body.getConsumerTag() +
                              " nowait:" + body.getNowait() +
                              " args:" + body.getArguments());
            }

            MessageSource queue = queueName == null ? channel.getDefaultQueue() : vHost.getQueue(queueName);
            final Collection<MessageSource> sources = new HashSet<>();
            if(queue != null)
            {
                sources.add(queue);
            }
            else if(vHost.getContextValue(Boolean.class, "qpid.enableMultiQueueConsumers")
                    && body.getArguments() != null
                    && body.getArguments().get("x-multiqueue") instanceof Collection)
            {
                for(Object object : (Collection<Object>) body.getArguments().get("x-multiqueue"))
                {
                    String sourceName = String.valueOf(object);
                    sourceName = sourceName.trim();
                    if(sourceName.length() != 0)
                    {
                        MessageSource source = vHost.getMessageSource(sourceName);
                        if(source == null)
                        {
                            sources.clear();
                            break;
                        }
                        else
                        {
                            sources.add(source);
                        }
                    }
                }
                queueName = body.getArguments().get("x-multiqueue").toString();
            }

            if (sources.isEmpty())
            {
                if (_logger.isDebugEnabled())
                {
                    _logger.debug("No queue for '" + queueName + "'");
                }
                if (queueName != null)
                {
                    String msg = "No such queue, '" + queueName + "'";
                    throw body.getChannelException(AMQConstant.NOT_FOUND, msg, _connection.getMethodRegistry());
                }
                else
                {
                    String msg = "No queue name provided, no default queue defined.";
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED, msg, _connection.getMethodRegistry());
                }
            }
            else
            {
                final AMQShortString consumerTagName;

                if (body.getConsumerTag() != null)
                {
                    consumerTagName = body.getConsumerTag().intern(false);
                }
                else
                {
                    consumerTagName = null;
                }

                try
                {
                    if(consumerTagName == null || channel.getSubscription(consumerTagName) == null)
                    {

                        AMQShortString consumerTag = channel.consumeFromSource(consumerTagName,
                                                                               sources,
                                                                               !body.getNoAck(),
                                                                               body.getArguments(),
                                                                               body.getExclusive(),
                                                                               body.getNoLocal());
                        if (!body.getNowait())
                        {
                            MethodRegistry methodRegistry = _connection.getMethodRegistry();
                            AMQMethodBody responseBody = methodRegistry.createBasicConsumeOkBody(consumerTag);
                            _connection.writeFrame(responseBody.generateFrame(channelId));

                        }
                    }
                    else
                    {
                        AMQShortString msg = AMQShortString.validValueOf("Non-unique consumer tag, '" + body.getConsumerTag() + "'");

                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                 msg,               // replytext
                                                                 body.getClazz(),
                                                                 body.getMethod());
                        _connection.writeFrame(responseBody.generateFrame(0));
                    }

                }
                catch (AMQInvalidArgumentException ise)
                {
                    _logger.debug("Closing connection due to invalid selector");

                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createChannelCloseBody(AMQConstant.ARGUMENT_INVALID.getCode(),
                                                                                       AMQShortString.validValueOf(ise.getMessage()),
                                                                                       body.getClazz(),
                                                                                       body.getMethod());
                    _connection.writeFrame(responseBody.generateFrame(channelId));


                }
                catch (AMQQueue.ExistingExclusiveConsumer e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " as it already has an existing exclusive consumer",
                                                      _connection.getMethodRegistry());
                }
                catch (AMQQueue.ExistingConsumerPreventsExclusive e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " exclusively as it already has a consumer",
                                                      _connection.getMethodRegistry());
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " permission denied", _connection.getMethodRegistry());
                }
                catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      "Cannot subscribe to queue "
                                                      + queue.getName()
                                                      + " as it already has an incompatible exclusivity policy",
                                                      _connection.getMethodRegistry());
                }

            }
        }
        return true;
    }

    public boolean dispatchBasicGet(BasicGetBody body, int channelId) throws AMQException
    {

        VirtualHostImpl vHost = _connection.getVirtualHost();

        AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        else
        {
            channel.sync();
            AMQQueue queue = body.getQueue() == null ? channel.getDefaultQueue() : vHost.getQueue(body.getQueue().toString());
            if (queue == null)
            {
                _logger.info("No queue for '" + body.getQueue() + "'");
                if(body.getQueue()!=null)
                {
                    throw body.getConnectionException(AMQConstant.NOT_FOUND,
                                                      "No such queue, '" + body.getQueue() + "'",
                                                      _connection.getMethodRegistry());
                }
                else
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "No queue name provided, no default queue defined.",
                                                      _connection.getMethodRegistry());
                }
            }
            else
            {

                try
                {
                    if (!performGet(queue, _connection, channel, !body.getNoAck()))
                    {
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();

                        BasicGetEmptyBody responseBody = methodRegistry.createBasicGetEmptyBody(null);


                        _connection.writeFrame(responseBody.generateFrame(channelId));
                    }
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      e.getMessage(), _connection.getMethodRegistry());
                }
                catch (MessageSource.ExistingExclusiveConsumer e)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue has an exclusive consumer",
                                                      _connection.getMethodRegistry());
                }
                catch (MessageSource.ExistingConsumerPreventsExclusive e)
                {
                    throw body.getConnectionException(AMQConstant.INTERNAL_ERROR,
                                                      "The GET request has been evaluated as an exclusive consumer, " +
                                                      "this is likely due to a programming error in the Qpid broker",
                                                      _connection.getMethodRegistry());
                }
                catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue has an incompatible exclusivit policy",
                                                      _connection.getMethodRegistry());
                }
            }
        }
        return true;
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

    public boolean dispatchBasicPublish(BasicPublishBody body, int channelId) throws AMQException
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Publish received on channel " + channelId);
        }

        AMQShortString exchangeName = body.getExchange();
        VirtualHostImpl vHost = _connection.getVirtualHost();

        // TODO: check the delivery tag field details - is it unique across the broker or per subscriber?

        MessageDestination destination;

        if (exchangeName == null || AMQShortString.EMPTY_STRING.equals(exchangeName))
        {
            destination = vHost.getDefaultDestination();
        }
        else
        {
            destination = vHost.getMessageDestination(exchangeName.toString());
        }

        // if the exchange does not exist we raise a channel exception
        if (destination == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Unknown exchange name",
                                           _connection.getMethodRegistry());
        }
        else
        {
            // The partially populated BasicDeliver frame plus the received route body
            // is stored in the channel. Once the final body frame has been received
            // it is routed to the exchange.
            AMQChannel channel = _connection.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
            }

            MessagePublishInfo info = new MessagePublishInfo(body.getExchange(),
                                                             body.getImmediate(),
                                                             body.getMandatory(),
                                                             body.getRoutingKey());
            info.setExchange(exchangeName);
            try
            {
                channel.setPublishFrame(info, destination);
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                  e.getMessage(),
                                                  _connection.getMethodRegistry());
            }
        }
        return true;
    }

    public boolean dispatchBasicQos(BasicQosBody body, int channelId) throws AMQException
    {
        AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        channel.sync();
        channel.setCredit(body.getPrefetchSize(), body.getPrefetchCount());


        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createBasicQosOkBody();
        _connection.writeFrame(responseBody.generateFrame(channelId));

        return true;
    }

    public boolean dispatchBasicRecover(BasicRecoverBody body, int channelId) throws AMQException
    {
        _logger.debug("Recover received on protocol session " + _connection
                                                + " and channel " + channelId);
        AMQChannel channel = _connection.getChannel(channelId);


        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        channel.resend();

        // Qpid 0-8 hacks a synchronous -ok onto recover.
        // In Qpid 0-9 we create a separate sync-recover, sync-recover-ok pair to be "more" compliant
        if(_connection.getProtocolVersion().equals(ProtocolVersion.v8_0))
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody recoverOk = methodRegistry.createBasicRecoverSyncOkBody();
            channel.sync();
            _connection.writeFrame(recoverOk.generateFrame(channelId));

        }

        return true;
    }

    public boolean dispatchBasicReject(BasicRejectBody body, int channelId) throws AMQException
    {

        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Rejecting:" + body.getDeliveryTag() +
                          ": Requeue:" + body.getRequeue() +
                          " on channel:" + channel.debugIdentity());
        }

        long deliveryTag = body.getDeliveryTag();

        MessageInstance message = channel.getUnacknowledgedMessageMap().get(deliveryTag);

        if (message == null)
        {
            _logger.warn("Dropping reject request as message is null for tag:" + deliveryTag);
        }
        else
        {

            if (message.getMessage() == null)
            {
                _logger.warn("Message has already been purged, unable to Reject.");
            }
            else
            {

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Rejecting: DT:" + deliveryTag + "-" + message.getMessage() +
                                  ": Requeue:" + body.getRequeue() +
                                  " on channel:" + channel.debugIdentity());
                }

                if (body.getRequeue())
                {
                    //this requeue represents a message rejected from the pre-dispatch queue
                    //therefore we need to amend the delivery counter.
                    message.decrementDeliveryCount();

                    channel.requeue(deliveryTag);
                }
                else
                {
                    // Since the Java client abuses the reject flag for requeing after rollback, we won't set reject here
                    // as it would prevent redelivery
                    // message.reject();

                    final boolean maxDeliveryCountEnabled = channel.isMaxDeliveryCountEnabled(deliveryTag);
                    _logger.debug("maxDeliveryCountEnabled: "
                                  + maxDeliveryCountEnabled
                                  + " deliveryTag "
                                  + deliveryTag);
                    if (maxDeliveryCountEnabled)
                    {
                        final boolean deliveredTooManyTimes = channel.isDeliveredTooManyTimes(deliveryTag);
                        _logger.debug("deliveredTooManyTimes: "
                                      + deliveredTooManyTimes
                                      + " deliveryTag "
                                      + deliveryTag);
                        if (deliveredTooManyTimes)
                        {
                            channel.deadLetter(body.getDeliveryTag());
                        }
                        else
                        {
                            //this requeue represents a message rejected because of a recover/rollback that we
                            //are not ready to DLQ. We rely on the reject command to resend from the unacked map
                            //and therefore need to increment the delivery counter so we cancel out the effect
                            //of the AMQChannel#resend() decrement.
                            message.incrementDeliveryCount();
                        }
                    }
                    else
                    {
                        channel.requeue(deliveryTag);
                    }
                }
            }
        }
        return true;
    }

    public boolean dispatchChannelOpen(ChannelOpenBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        // Protect the broker against out of order frame request.
        if (virtualHost == null)
        {
            throw new AMQException(AMQConstant.COMMAND_INVALID, "Virtualhost has not yet been set. ConnectionOpen has not been called.", null);
        }
        _logger.info("Connecting to: " + virtualHost.getName());

        final AMQChannel channel = new AMQChannel(_connection, channelId, virtualHost.getMessageStore());

        _connection.addChannel(channel);

        ChannelOpenOkBody response;


        response = _connection.getMethodRegistry().createChannelOpenOkBody();


        _connection.writeFrame(response.generateFrame(channelId));
        return true;
    }


    public boolean dispatchAccessRequestOk(AccessRequestOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchBasicCancelOk(BasicCancelOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicConsumeOk(BasicConsumeOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicDeliver(BasicDeliverBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicGetEmpty(BasicGetEmptyBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicGetOk(BasicGetOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicQosOk(BasicQosOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchBasicReturn(BasicReturnBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchChannelClose(ChannelCloseBody body, int channelId) throws AMQException
    {

        if (_logger.isInfoEnabled())
        {
            _logger.info("Received channel close for id " + channelId
                                             + " citing class " + body.getClassId() +
                         " and method " + body.getMethodId());
        }


        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getConnectionException(AMQConstant.CHANNEL_ERROR,
                                              "Trying to close unknown channel",
                                              _connection.getMethodRegistry());
        }
        channel.sync();
        _connection.closeChannel(channelId);
        // Client requested closure so we don't wait for ok we send it
        _connection.closeChannelOk(channelId);

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        ChannelCloseOkBody responseBody = methodRegistry.createChannelCloseOkBody();
        _connection.writeFrame(responseBody.generateFrame(channelId));
        return true;
    }


    public boolean dispatchChannelCloseOk(ChannelCloseOkBody body, int channelId) throws AMQException
    {

        _logger.info("Received channel-close-ok for channel-id " + channelId);

        // Let the Protocol Session know the channel is now closed.
        _connection.closeChannelOk(channelId);
        return true;
    }


    public boolean dispatchChannelFlow(ChannelFlowBody body, int channelId) throws AMQException
    {
        final AMQProtocolSession<?> connection = getConnection();


        AMQChannel channel = connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, connection.getMethodRegistry());
        }
        channel.sync();
        channel.setSuspended(!body.getActive());
        _logger.debug("Channel.Flow for channel " + channelId + ", active=" + body.getActive());

        MethodRegistry methodRegistry = connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createChannelFlowOkBody(body.getActive());
        connection.writeFrame(responseBody.generateFrame(channelId));
        return true;
    }

    public boolean dispatchChannelFlowOk(ChannelFlowOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchChannelOpenOk(ChannelOpenOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchConnectionOpen(ConnectionOpenBody body, int channelId) throws AMQException
    {

        //ignore leading '/'
        String virtualHostName;
        if ((body.getVirtualHost() != null) && body.getVirtualHost().charAt(0) == '/')
        {
            virtualHostName = new StringBuilder(body.getVirtualHost().subSequence(1, body.getVirtualHost().length())).toString();
        }
        else
        {
            virtualHostName = body.getVirtualHost() == null ? null : String.valueOf(body.getVirtualHost());
        }

        VirtualHostImpl virtualHost = ((AmqpPort) _connection.getPort()).getVirtualHost(virtualHostName);

        if (virtualHost == null)
        {
            throw body.getConnectionException(AMQConstant.NOT_FOUND, "Unknown virtual host: '" + virtualHostName + "'",
                                              _connection.getMethodRegistry());
        }
        else
        {
            // Check virtualhost access
            if (virtualHost.getState() != State.ACTIVE)
            {
                throw body.getConnectionException(AMQConstant.CONNECTION_FORCED,
                                                  "Virtual host '" + virtualHost.getName() + "' is not active",
                                                  _connection.getMethodRegistry());
            }

            _connection.setVirtualHost(virtualHost);
            try
            {
                virtualHost.getSecurityManager().authoriseCreateConnection(_connection);
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                  e.getMessage(),
                                                  _connection.getMethodRegistry());
            }

            // See Spec (0.8.2). Section  3.1.2 Virtual Hosts
            if (_connection.getContextKey() == null)
            {
                _connection.setContextKey(new AMQShortString(Long.toString(System.currentTimeMillis())));
            }

            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody responseBody =  methodRegistry.createConnectionOpenOkBody(body.getVirtualHost());

            _connection.writeFrame(responseBody.generateFrame(channelId));
        }
        return true;
    }


    public boolean dispatchConnectionClose(ConnectionCloseBody body, int channelId) throws AMQException
    {
        if (_logger.isInfoEnabled())
        {
            _logger.info("ConnectionClose received with reply code/reply text " + body.getReplyCode() + "/" +
                         body.getReplyText() + " for " + _connection);
        }
        try
        {
            _connection.closeSession();
        }
        catch (Exception e)
        {
            _logger.error("Error closing protocol session: " + e, e);
        }

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        ConnectionCloseOkBody responseBody = methodRegistry.createConnectionCloseOkBody();
        _connection.writeFrame(responseBody.generateFrame(channelId));

        _connection.closeProtocolSession();

        return true;
    }


    public boolean dispatchConnectionCloseOk(ConnectionCloseOkBody body, int channelId) throws AMQException
    {
        _logger.info("Received Connection-close-ok");

        try
        {
            _connection.closeSession();
        }
        catch (Exception e)
        {
            _logger.error("Error closing protocol session: " + e, e);
        }
        return true;
    }

    public boolean dispatchConnectionOpenOk(ConnectionOpenOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionRedirect(ConnectionRedirectBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionSecure(ConnectionSecureBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionStart(ConnectionStartBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchConnectionTune(ConnectionTuneBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchExchangeBoundOk(ExchangeBoundOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchExchangeDeclareOk(ExchangeDeclareOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchExchangeDeleteOk(ExchangeDeleteOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueBindOk(QueueBindOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueDeclareOk(QueueDeclareOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueDeleteOk(QueueDeleteOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueuePurgeOk(QueuePurgeOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxCommitOk(TxCommitOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxRollbackOk(TxRollbackOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchTxSelectOk(TxSelectOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }


    public boolean dispatchConnectionSecureOk(ConnectionSecureOkBody body, int channelId) throws AMQException
    {
        Broker<?> broker = _connection.getBroker();

        SubjectCreator subjectCreator = _connection.getSubjectCreator();

        SaslServer ss = _connection.getSaslServer();
        if (ss == null)
        {
            throw new AMQException("No SASL context set up in session");
        }
        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
        switch (authResult.getStatus())
        {
            case ERROR:
                Exception cause = authResult.getCause();

                _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                ConnectionCloseBody connectionCloseBody =
                        methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),
                                                                 AMQConstant.NOT_ALLOWED.getName(),
                                                                 body.getClazz(),
                                                                 body.getMethod());

                _connection.writeFrame(connectionCloseBody.generateFrame(0));
                disposeSaslServer(_connection);
                break;
            case SUCCESS:
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Connected as: " + authResult.getSubject());
                }

                int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                if(frameMax <= 0)
                {
                    frameMax = Integer.MAX_VALUE;
                }

                ConnectionTuneBody tuneBody =
                        methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                frameMax,
                                                                broker.getConnection_heartBeatDelay());
                _connection.writeFrame(tuneBody.generateFrame(0));
                _connection.setAuthorizedSubject(authResult.getSubject());
                disposeSaslServer(_connection);
                break;
            case CONTINUE:

                ConnectionSecureBody
                        secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                _connection.writeFrame(secureBody.generateFrame(0));
        }
        return true;
    }

    private void disposeSaslServer(AMQProtocolSession ps)
    {
        SaslServer ss = ps.getSaslServer();
        if (ss != null)
        {
            ps.setSaslServer(null);
            try
            {
                ss.dispose();
            }
            catch (SaslException e)
            {
                _logger.error("Error disposing of Sasl server: " + e);
            }
        }
    }

    public boolean dispatchConnectionStartOk(ConnectionStartOkBody body, int channelId) throws AMQException
    {
        Broker<?> broker = _connection.getBroker();

        _logger.info("SASL Mechanism selected: " + body.getMechanism());
        _logger.info("Locale selected: " + body.getLocale());

        SubjectCreator subjectCreator = _connection.getSubjectCreator();
        SaslServer ss = null;
        try
        {
            ss = subjectCreator.createSaslServer(String.valueOf(body.getMechanism()),
                                                 _connection.getLocalFQDN(),
                                                 _connection.getPeerPrincipal());

            if (ss == null)
            {
                throw body.getConnectionException(AMQConstant.RESOURCE_ERROR,
                                                  "Unable to create SASL Server:" + body.getMechanism(),
                                                  _connection.getMethodRegistry());
            }

            _connection.setSaslServer(ss);

            final SubjectAuthenticationResult authResult = subjectCreator.authenticate(ss, body.getResponse());
            //save clientProperties
            _connection.setClientProperties(body.getClientProperties());

            MethodRegistry methodRegistry = _connection.getMethodRegistry();

            switch (authResult.getStatus())
            {
                case ERROR:
                    Exception cause = authResult.getCause();

                    _logger.info("Authentication failed:" + (cause == null ? "" : cause.getMessage()));

                    ConnectionCloseBody closeBody =
                            methodRegistry.createConnectionCloseBody(AMQConstant.NOT_ALLOWED.getCode(),    // replyCode
                                                                     AMQConstant.NOT_ALLOWED.getName(),
                                                                     body.getClazz(),
                                                                     body.getMethod());

                    _connection.writeFrame(closeBody.generateFrame(0));
                    disposeSaslServer(_connection);
                    break;

                case SUCCESS:
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Connected as: " + authResult.getSubject());
                    }
                    _connection.setAuthorizedSubject(authResult.getSubject());

                    int frameMax = broker.getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);

                    if(frameMax <= 0)
                    {
                        frameMax = Integer.MAX_VALUE;
                    }

                    ConnectionTuneBody
                            tuneBody = methodRegistry.createConnectionTuneBody(broker.getConnection_sessionCountLimit(),
                                                                               frameMax,
                                                                               broker.getConnection_heartBeatDelay());
                    _connection.writeFrame(tuneBody.generateFrame(0));
                    break;
                case CONTINUE:
                    ConnectionSecureBody
                            secureBody = methodRegistry.createConnectionSecureBody(authResult.getChallenge());
                    _connection.writeFrame(secureBody.generateFrame(0));
            }
        }
        catch (SaslException e)
        {
            disposeSaslServer(_connection);
            throw new AMQException("SASL error: " + e, e);
        }
        return true;
    }

    public boolean dispatchConnectionTuneOk(ConnectionTuneOkBody body, int channelId) throws AMQException
    {
        final AMQProtocolSession<?> connection = getConnection();

        if (_logger.isDebugEnabled())
        {
            _logger.debug(body);
        }

        connection.initHeartbeats(body.getHeartbeat());

        int brokerFrameMax = connection.getBroker().getContextValue(Integer.class, Broker.BROKER_FRAME_SIZE);
        if(brokerFrameMax <= 0)
        {
            brokerFrameMax = Integer.MAX_VALUE;
        }

        if(body.getFrameMax() > (long) brokerFrameMax)
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + " greater than the broker will allow: "
                                             + brokerFrameMax,
                                             body.getClazz(), body.getMethod(),
                                             connection.getMethodRegistry(),null);
        }
        else if(body.getFrameMax() > 0 && body.getFrameMax() < AMQConstant.FRAME_MIN_SIZE.getCode())
        {
            throw new AMQConnectionException(AMQConstant.SYNTAX_ERROR,
                                             "Attempt to set max frame size to " + body.getFrameMax()
                                             + " which is smaller than the specification definined minimum: "
                                             + AMQConstant.FRAME_MIN_SIZE.getCode(),
                                             body.getClazz(), body.getMethod(),
                                             connection.getMethodRegistry(),null);
        }
        int frameMax = body.getFrameMax() == 0 ? brokerFrameMax : (int) body.getFrameMax();
        connection.setMaxFrameSize(frameMax);

        long maxChannelNumber = body.getChannelMax();
        //0 means no implied limit, except that forced by protocol limitations (0xFFFF)
        connection.setMaximumNumberOfChannels(maxChannelNumber == 0 ? 0xFFFFL : maxChannelNumber);
        return true;
    }

    public static final int OK = 0;
    public static final int EXCHANGE_NOT_FOUND = 1;
    public static final int QUEUE_NOT_FOUND = 2;
    public static final int NO_BINDINGS = 3;
    public static final int QUEUE_NOT_BOUND = 4;
    public static final int NO_QUEUE_BOUND_WITH_RK = 5;
    public static final int SPECIFIC_QUEUE_NOT_BOUND_WITH_RK = 6;

    public boolean dispatchExchangeBound(ExchangeBoundBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        channel.sync();


        AMQShortString exchangeName = body.getExchange();
        AMQShortString queueName = body.getQueue();
        AMQShortString routingKey = body.getRoutingKey();
        ExchangeBoundOkBody response;

        if(isDefaultExchange(exchangeName))
        {
            if(routingKey == null)
            {
                if(queueName == null)
                {
                    response = methodRegistry.createExchangeBoundOkBody(virtualHost.getQueues().isEmpty() ? NO_BINDINGS : OK, null);
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                                                                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        response = methodRegistry.createExchangeBoundOkBody(OK, null);
                    }
                }
            }
            else
            {
                if(queueName == null)
                {
                    response = methodRegistry.createExchangeBoundOkBody(virtualHost.getQueue(routingKey.toString()) == null ? NO_QUEUE_BOUND_WITH_RK : OK, null);
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                                                                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        response = methodRegistry.createExchangeBoundOkBody(queueName.equals(routingKey) ? OK : SPECIFIC_QUEUE_NOT_BOUND_WITH_RK, null);
                    }
                }
            }
        }
        else
        {
            ExchangeImpl exchange = virtualHost.getExchange(exchangeName.toString());
            if (exchange == null)
            {


                response = methodRegistry.createExchangeBoundOkBody(EXCHANGE_NOT_FOUND,
                                                                    AMQShortString.validValueOf("Exchange '" + exchangeName + "' not found"));
            }
            else if (routingKey == null)
            {
                if (queueName == null)
                {
                    if (exchange.hasBindings())
                    {
                        response = methodRegistry.createExchangeBoundOkBody(OK, null);
                    }
                    else
                    {

                        response = methodRegistry.createExchangeBoundOkBody(NO_BINDINGS,	// replyCode
                            null);	// replyText
                    }
                }
                else
                {

                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                            AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                    }
                    else
                    {
                        if (exchange.isBound(queue))
                        {

                            response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                                null);	// replyText
                        }
                        else
                        {

                            response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_BOUND,	// replyCode
                                AMQShortString.validValueOf("Queue '" + queueName + "' not bound to exchange '" + exchangeName + "'"));	// replyText
                        }
                    }
                }
            }
            else if (queueName != null)
            {
                AMQQueue queue = virtualHost.getQueue(queueName.toString());
                if (queue == null)
                {

                    response = methodRegistry.createExchangeBoundOkBody(QUEUE_NOT_FOUND,	// replyCode
                        AMQShortString.validValueOf("Queue '" + queueName + "' not found"));	// replyText
                }
                else
                {
                    String bindingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().asString();
                    if (exchange.isBound(bindingKey, queue))
                    {

                        response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                            null);	// replyText
                    }
                    else
                    {

                        String message = "Queue '" + queueName + "' not bound with routing key '" +
                                            body.getRoutingKey() + "' to exchange '" + exchangeName + "'";

                        response = methodRegistry.createExchangeBoundOkBody(SPECIFIC_QUEUE_NOT_BOUND_WITH_RK,	// replyCode
                            AMQShortString.validValueOf(message));	// replyText
                    }
                }
            }
            else
            {
                if (exchange.isBound(body.getRoutingKey() == null ? "" : body.getRoutingKey().asString()))
                {

                    response = methodRegistry.createExchangeBoundOkBody(OK,	// replyCode
                        null);	// replyText
                }
                else
                {

                    response = methodRegistry.createExchangeBoundOkBody(NO_QUEUE_BOUND_WITH_RK,	// replyCode
                        AMQShortString.validValueOf("No queue bound with routing key '" + body.getRoutingKey() +
                        "' to exchange '" + exchangeName + "'"));	// replyText
                }
            }
        }
        _connection.writeFrame(response.generateFrame(channelId));
        return true;
    }

    public boolean dispatchExchangeDeclare(ExchangeDeclareBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        final AMQShortString exchangeName = body.getExchange();
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Request to declare exchange of type " + body.getType() + " with name " + exchangeName);
        }

        ExchangeImpl exchange;

        if(isDefaultExchange(exchangeName))
        {
            if(!new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_CLASS).equals(body.getType()))
            {
                throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare default exchange: "
                                                                          + " of type "
                                                                          + ExchangeDefaults.DIRECT_EXCHANGE_CLASS
                                                                          + " to " + body.getType() +".",
                                                 body.getClazz(), body.getMethod(),
                                                 _connection.getMethodRegistry(),null);
            }
        }
        else
        {
            if (body.getPassive())
            {
                exchange = virtualHost.getExchange(exchangeName.toString());
                if(exchange == null)
                {
                    throw body.getChannelException(AMQConstant.NOT_FOUND, "Unknown exchange: " + exchangeName,
                                                   _connection.getMethodRegistry());
                }
                else if (!(body.getType() == null || body.getType().length() ==0) && !exchange.getType().equals(body.getType().asString()))
                {

                    throw new AMQConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: " +
                                      exchangeName + " of type " + exchange.getType()
                                      + " to " + body.getType() +".",
                                                     body.getClazz(), body.getMethod(),
                                                     _connection.getMethodRegistry(),null);
                }

            }
            else
            {
                try
                {
                    String name = exchangeName == null ? null : exchangeName.intern().toString();
                    String type = body.getType() == null ? null : body.getType().intern().toString();

                    Map<String,Object> attributes = new HashMap<String, Object>();
                    if(body.getArguments() != null)
                    {
                        attributes.putAll(FieldTable.convertToMap(body.getArguments()));
                    }
                    attributes.put(org.apache.qpid.server.model.Exchange.ID, null);
                    attributes.put(org.apache.qpid.server.model.Exchange.NAME,name);
                    attributes.put(org.apache.qpid.server.model.Exchange.TYPE,type);
                    attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, body.getDurable());
                    attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                                   body.getAutoDelete() ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
                    if(!attributes.containsKey(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE))
                    {
                        attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
                    }
                    exchange = virtualHost.createExchange(attributes);

                }
                catch(ReservedExchangeNameException e)
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Attempt to declare exchange: " + exchangeName +
                                                      " which begins with reserved prefix.",
                                                      _connection.getMethodRegistry());

                }
                catch(ExchangeExistsException e)
                {
                    exchange = e.getExistingExchange();
                    if(!new AMQShortString(exchange.getType()).equals(body.getType()))
                    {
                        throw body.getConnectionException(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                                                                   + exchangeName + " of type "
                                                                                   + exchange.getType()
                                                                                   + " to " + body.getType() + ".",
                                                          _connection.getMethodRegistry());
                    }
                }
                catch(NoFactoryForTypeException e)
                {
                    throw body.getConnectionException(AMQConstant.COMMAND_INVALID,
                                                      "Unknown exchange type '"
                                                      + e.getType()
                                                      + "' for exchange '"
                                                      + exchangeName
                                                      + "'",
                                                      _connection.getMethodRegistry());
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      e.getMessage(),
                                                      _connection.getMethodRegistry());
                }
                catch (UnknownConfiguredObjectException e)
                {
                    // note - since 0-8/9/9-1 can't set the alt. exchange this exception should never occur
                    throw body.getConnectionException(AMQConstant.NOT_FOUND,
                                                      "Unknown alternate exchange "
                                                      + (e.getName() != null
                                                              ? "name: \"" + e.getName() + "\""
                                                              : "id: " + e.getId()),
                                                      _connection.getMethodRegistry());
                }
                catch (IllegalArgumentException e)
                {
                    throw body.getConnectionException(AMQConstant.COMMAND_INVALID,
                                                      "Error creating exchange '"
                                                      + exchangeName
                                                      + "': "
                                                      + e.getMessage(),
                                                      _connection.getMethodRegistry());
                }
            }
        }

        if(!body.getNowait())
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
            channel.sync();
            _connection.writeFrame(responseBody.generateFrame(channelId));
        }
        return true;
    }

    public boolean dispatchExchangeDelete(ExchangeDeleteBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        final AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        channel.sync();
        try
        {

            if(isDefaultExchange(body.getExchange()))
            {
                throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                  "Default Exchange cannot be deleted",
                                                  _connection.getMethodRegistry());
            }

            final String exchangeName = body.getExchange().toString();

            final ExchangeImpl exchange = virtualHost.getExchange(exchangeName);
            if(exchange == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND, "No such exchange: " + body.getExchange(),
                                               _connection.getMethodRegistry());
            }

            virtualHost.removeExchange(exchange, !body.getIfUnused());

            ExchangeDeleteOkBody responseBody = _connection.getMethodRegistry().createExchangeDeleteOkBody();

            _connection.writeFrame(responseBody.generateFrame(channelId));
        }

        catch (ExchangeIsAlternateException e)
        {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED, "Exchange in use as an alternate exchange",
                                           _connection.getMethodRegistry());

        }
        catch (RequiredExchangeException e)
        {
            throw body.getChannelException(AMQConstant.NOT_ALLOWED,
                                           "Exchange '" + body.getExchange() + "' cannot be deleted",
                                           _connection.getMethodRegistry());
        }
        catch (AccessControlException e)
        {
            throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                              e.getMessage(),
                                              _connection.getMethodRegistry());
        }
        return true;
    }

    private boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || exchangeName.equals(AMQShortString.EMPTY_STRING);
    }

    public boolean dispatchQueueBind(QueueBindBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        final AMQQueue queue;
        final AMQShortString routingKey;

        final AMQShortString queueName = body.getQueue();

        if (queueName == null)
        {

            queue = channel.getDefaultQueue();

            if (queue == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND,
                                               "No default queue defined on channel and queue was null",
                                               _connection.getMethodRegistry());
            }

            if (body.getRoutingKey() == null)
            {
                routingKey = AMQShortString.valueOf(queue.getName());
            }
            else
            {
                routingKey = body.getRoutingKey().intern();
            }
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
            routingKey = body.getRoutingKey() == null ? AMQShortString.EMPTY_STRING : body.getRoutingKey().intern();
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + queueName + " does not exist.",
                                           _connection.getMethodRegistry());
        }

        if(isDefaultExchange(body.getExchange()))
        {
            throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                              "Cannot bind the queue " + queueName + " to the default exchange",
                                              _connection.getMethodRegistry());
        }

        final String exchangeName = body.getExchange().toString();

        final ExchangeImpl exch = virtualHost.getExchange(exchangeName);
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Exchange " + exchangeName + " does not exist.",
                                           _connection.getMethodRegistry());
        }


        try
        {

            Map<String,Object> arguments = FieldTable.convertToMap(body.getArguments());
            String bindingKey = String.valueOf(routingKey);

            if (!exch.isBound(bindingKey, arguments, queue))
            {

                if(!exch.addBinding(bindingKey, queue, arguments) && ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(exch.getType()))
                {
                    exch.replaceBinding(bindingKey, queue, arguments);
                }
            }
        }
        catch (AccessControlException e)
        {
            throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                              e.getMessage(),
                                              _connection.getMethodRegistry());
        }

        if (_logger.isInfoEnabled())
        {
            _logger.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + routingKey);
        }
        if (!body.getNowait())
        {
            channel.sync();
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody responseBody = methodRegistry.createQueueBindOkBody();
            _connection.writeFrame(responseBody.generateFrame(channelId));

        }
        return true;
    }

    public boolean dispatchQueueDeclare(QueueDeclareBody body, int channelId) throws AMQException
    {
        final AMQSessionModel session = _connection.getChannel(channelId);
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        final AMQShortString queueName;

        // if we aren't given a queue name, we create one which we return to the client
        if ((body.getQueue() == null) || (body.getQueue().length() == 0))
        {
            queueName = new AMQShortString("tmp_" + UUID.randomUUID());
        }
        else
        {
            queueName = body.getQueue().intern();
        }

        AMQQueue queue;

        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?

        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        if(body.getPassive())
        {
            queue = virtualHost.getQueue(queueName.toString());
            if (queue == null)
            {
                String msg = "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").";
                throw body.getChannelException(AMQConstant.NOT_FOUND, msg, _connection.getMethodRegistry());
            }
            else
            {
                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue "
                                                      + queue.getName()
                                                      + " is exclusive, but not created on this Connection.",
                                                      _connection.getMethodRegistry());
                }

                //set this as the default queue on the channel:
                channel.setDefaultQueue(queue);
            }
        }
        else
        {

            try
            {

                queue = createQueue(channel, queueName, body, virtualHost, _connection);

            }
            catch(QueueExistsException qe)
            {

                queue = qe.getExistingQueue();

                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue "
                                                      + queue.getName()
                                                      + " is exclusive, but not created on this Connection.",
                                                      _connection.getMethodRegistry());
                }
                else if(queue.isExclusive() != body.getExclusive())
                {

                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                   "Cannot re-declare queue '"
                                                   + queue.getName()
                                                   + "' with different exclusivity (was: "
                                                   + queue.isExclusive()
                                                   + " requested "
                                                   + body.getExclusive()
                                                   + ")",
                                                   _connection.getMethodRegistry());
                }
                else if((body.getAutoDelete() && queue.getLifetimePolicy() != LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS)
                    || (!body.getAutoDelete() && queue.getLifetimePolicy() != ((body.getExclusive() && !body.getDurable()) ? LifetimePolicy.DELETE_ON_CONNECTION_CLOSE : LifetimePolicy.PERMANENT)))
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                   "Cannot re-declare queue '"
                                                   + queue.getName()
                                                   + "' with different lifetime policy (was: "
                                                   + queue.getLifetimePolicy()
                                                   + " requested autodelete: "
                                                   + body.getAutoDelete()
                                                   + ")",
                                                   _connection.getMethodRegistry());
                }
                else if(queue.isDurable() != body.getDurable())
                {
                    throw body.getChannelException(AMQConstant.ALREADY_EXISTS,
                                                   "Cannot re-declare queue '"
                                                   + queue.getName()
                                                   + "' with different durability (was: "
                                                   + queue.isDurable()
                                                   + " requested "
                                                   + body.getDurable()
                                                   + ")",
                                                   _connection.getMethodRegistry());
                }

            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                  e.getMessage(),
                                                  _connection.getMethodRegistry());
            }

            //set this as the default queue on the channel:
            channel.setDefaultQueue(queue);
        }

        if (!body.getNowait())
        {
            channel.sync();
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            QueueDeclareOkBody responseBody =
                    methodRegistry.createQueueDeclareOkBody(queueName,
                                                            queue.getQueueDepthMessages(),
                                                            queue.getConsumerCount());
            _connection.writeFrame(responseBody.generateFrame(channelId));

            _logger.info("Queue " + queueName + " declared successfully");
        }
        return true;
    }

    protected AMQQueue createQueue(final AMQChannel channel, final AMQShortString queueName,
                                   QueueDeclareBody body,
                                   final VirtualHostImpl virtualHost,
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
        attributes.put(Queue.ID, UUID.randomUUID());
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


        final AMQQueue queue = virtualHost.createQueue(attributes);

        return queue;
    }

    public boolean dispatchQueueDelete(QueueDeleteBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        channel.sync();
        AMQQueue queue;
        if (body.getQueue() == null)
        {

            //get the default queue on the channel:
            queue = channel.getDefaultQueue();
        }
        else
        {
            queue = virtualHost.getQueue(body.getQueue().toString());
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.",
                                           _connection.getMethodRegistry());

        }
        else
        {
            if (body.getIfEmpty() && !queue.isEmpty())
            {
                throw body.getChannelException(AMQConstant.IN_USE, "Queue: " + body.getQueue() + " is not empty.",
                                               _connection.getMethodRegistry());
            }
            else if (body.getIfUnused() && !queue.isUnused())
            {
                // TODO - Error code
                throw body.getChannelException(AMQConstant.IN_USE, "Queue: " + body.getQueue() + " is still used.",
                                               _connection.getMethodRegistry());
            }
            else
            {
                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue "
                                                      + queue.getName()
                                                      + " is exclusive, but not created on this Connection.",
                                                      _connection.getMethodRegistry());
                }

                int purged = 0;
                try
                {
                    purged = virtualHost.removeQueue(queue);
                }
                catch (AccessControlException e)
                {
                    throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                      e.getMessage(),
                                                      _connection.getMethodRegistry());
                }

                MethodRegistry methodRegistry = _connection.getMethodRegistry();
                QueueDeleteOkBody responseBody = methodRegistry.createQueueDeleteOkBody(purged);
                _connection.writeFrame(responseBody.generateFrame(channelId));
            }
        }
        return true;
    }

    public boolean dispatchQueuePurge(QueuePurgeBody body, int channelId) throws AMQException
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        AMQChannel channel = _connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }
        AMQQueue queue;
        if(body.getQueue() == null)
        {

           //get the default queue on the channel:
           queue = channel.getDefaultQueue();

            if(queue == null)
            {
                throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                  "No queue specified.",
                                                  _connection.getMethodRegistry());
            }
        }
        else
        {
            queue = virtualHost.getQueue(body.getQueue().toString());
        }

        if(queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.",
                                           _connection.getMethodRegistry());
        }
        else
        {
                if (!queue.verifySessionAccess(channel))
                {
                    throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                                      "Queue is exclusive, but not created on this Connection.",
                                                      _connection.getMethodRegistry());
                }

            long purged = 0;
            try
            {
                purged = queue.clearQueue();
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                  e.getMessage(),
                                                  _connection.getMethodRegistry());
            }


            if(!body.getNowait())
                {
                    channel.sync();
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createQueuePurgeOkBody(purged);
                    _connection.writeFrame(responseBody.generateFrame(channelId));

                }
        }
        return true;
    }


    public boolean dispatchTxCommit(TxCommitBody body, final int channelId) throws AMQException
    {
        try
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Commit received on channel " + channelId);
            }
            AMQChannel channel = _connection.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
            }
            channel.commit(new Runnable()
            {

                @Override
                public void run()
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createTxCommitOkBody();
                    _connection.writeFrame(responseBody.generateFrame(channelId));
                }
            }, true);



        }
        catch (AMQException e)
        {
            throw body.getChannelException(e.getErrorCode(), "Failed to commit: " + e.getMessage(),
                                           _connection.getMethodRegistry());
        }
        return true;
    }

    public boolean dispatchTxRollback(TxRollbackBody body, final int channelId) throws AMQException
    {
        try
        {
            AMQChannel channel = _connection.getChannel(channelId);

            if (channel == null)
            {
                throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
            }



            final MethodRegistry methodRegistry = _connection.getMethodRegistry();
            final AMQMethodBody responseBody = methodRegistry.createTxRollbackOkBody();

            Runnable task = new Runnable()
            {

                public void run()
                {
                    _connection.writeFrame(responseBody.generateFrame(channelId));
                }
            };

            channel.rollback(task);

            //Now resend all the unacknowledged messages back to the original subscribers.
            //(Must be done after the TxnRollback-ok response).
            // Why, are we not allowed to send messages back to client before the ok method?
            channel.resend();

        }
        catch (AMQException e)
        {
            throw body.getChannelException(e.getErrorCode(), "Failed to rollback: " + e.getMessage(),
                                           _connection.getMethodRegistry());
        }
        return true;
    }

    public boolean dispatchTxSelect(TxSelectBody body, int channelId) throws AMQException
    {
        AMQChannel channel = _connection.getChannel(channelId);

        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, _connection.getMethodRegistry());
        }

        channel.setLocalTransactional();

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        TxSelectOkBody responseBody = methodRegistry.createTxSelectOkBody();
        _connection.writeFrame(responseBody.generateFrame(channelId));
        return true;
    }

    public boolean dispatchBasicRecoverSync(BasicRecoverSyncBody body, int channelId) throws AMQException
    {
        final AMQProtocolSession<?> connection = getConnection();

        _logger.debug("Recover received on protocol session " + connection + " and channel " + channelId);
        AMQChannel channel = connection.getChannel(channelId);


        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, connection.getMethodRegistry());
        }
        channel.sync();
        channel.resend();

        MethodRegistry methodRegistry = connection.getMethodRegistry();
        AMQMethodBody recoverOk = methodRegistry.createBasicRecoverSyncOkBody();
        connection.writeFrame(recoverOk.generateFrame(channelId));

        return true;
    }

    public boolean dispatchBasicRecoverSyncOk(BasicRecoverSyncOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    @Override
    public boolean dispatchChannelAlert(final ChannelAlertBody body, final int channelId)
            throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueUnbindOk(QueueUnbindOkBody body, int channelId) throws AMQException
    {
        throw new UnexpectedMethodException(body);
    }

    public boolean dispatchQueueUnbind(QueueUnbindBody body, int channelId) throws AMQException
    {
        final AMQProtocolSession<?> connection = getConnection();

        if (ProtocolVersion.v8_0.equals(connection.getProtocolVersion()))
        {
            // 0-8 does not support QueueUnbind
            throw new AMQException(AMQConstant.COMMAND_INVALID, "QueueUnbind not present in AMQP version: " + connection.getProtocolVersion(), null);
        }

        VirtualHostImpl virtualHost = connection.getVirtualHost();

        final AMQQueue queue;
        final AMQShortString routingKey;


        AMQChannel channel = connection.getChannel(channelId);
        if (channel == null)
        {
            throw body.getChannelNotFoundException(channelId, connection.getMethodRegistry());
        }

        if (body.getQueue() == null)
        {

            queue = channel.getDefaultQueue();

            if (queue == null)
            {
                throw body.getChannelException(AMQConstant.NOT_FOUND,
                                               "No default queue defined on channel and queue was null",
                                               connection.getMethodRegistry());
            }

            routingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().intern(false);

        }
        else
        {
            queue = virtualHost.getQueue(body.getQueue().toString());
            routingKey = body.getRoutingKey() == null ? null : body.getRoutingKey().intern(false);
        }

        if (queue == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Queue " + body.getQueue() + " does not exist.",
                                           connection.getMethodRegistry());
        }

        if(isDefaultExchange(body.getExchange()))
        {
            throw body.getConnectionException(AMQConstant.NOT_ALLOWED,
                                              "Cannot unbind the queue "
                                              + queue.getName()
                                              + " from the default exchange", connection.getMethodRegistry());
        }

        final ExchangeImpl exch = virtualHost.getExchange(body.getExchange() == null ? null : body.getExchange().toString());
        if (exch == null)
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "Exchange " + body.getExchange() + " does not exist.",
                                           connection.getMethodRegistry());
        }

        if(!exch.hasBinding(String.valueOf(routingKey), queue))
        {
            throw body.getChannelException(AMQConstant.NOT_FOUND, "No such binding", connection.getMethodRegistry());
        }
        else
        {
            try
            {
                exch.deleteBinding(String.valueOf(routingKey), queue);
            }
            catch (AccessControlException e)
            {
                throw body.getConnectionException(AMQConstant.ACCESS_REFUSED,
                                                  e.getMessage(),
                                                  connection.getMethodRegistry());
            }
        }


        if (_logger.isInfoEnabled())
        {
            _logger.info("Binding queue " + queue + " to exchange " + exch + " with routing key " + routingKey);
        }


        final AMQMethodBody responseBody = connection.getMethodRegistry().createQueueUnbindOkBody();
        channel.sync();
        connection.writeFrame(responseBody.generateFrame(channelId));
        return true;
    }

}
