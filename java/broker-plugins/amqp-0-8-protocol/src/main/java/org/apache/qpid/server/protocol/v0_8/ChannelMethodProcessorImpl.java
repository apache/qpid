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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.apache.log4j.Logger;

import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.AccessRequestOkBody;
import org.apache.qpid.framing.BasicCancelOkBody;
import org.apache.qpid.framing.BasicGetEmptyBody;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.framing.ExchangeDeleteOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.MessagePublishInfo;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.framing.QueueDeclareOkBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.TxSelectOkBody;
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
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.NoFactoryForTypeException;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UnknownConfiguredObjectException;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueArgumentsConverter;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ExchangeIsAlternateException;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.RequiredExchangeException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class ChannelMethodProcessorImpl implements ChannelMethodProcessor
{
    private static final Logger _logger = Logger.getLogger(ChannelMethodProcessorImpl.class);

    private final AMQChannel _channel;
    private final AMQProtocolEngine _connection;

    public ChannelMethodProcessorImpl(final AMQChannel channel)
    {
        _channel = channel;
        _connection = _channel.getConnection();
    }

    @Override
    public void receiveAccessRequest(final AMQShortString realm,
                                     final boolean exclusive,
                                     final boolean passive,
                                     final boolean active,
                                     final boolean write,
                                     final boolean read)
    {
        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        if (ProtocolVersion.v0_91.equals(_connection.getProtocolVersion()))
        {
            closeConnection(AMQConstant.COMMAND_INVALID,
                            "AccessRequest not present in AMQP versions other than 0-8, 0-9");
        }
        else
        {
            // We don't implement access control class, but to keep clients happy that expect it
            // always use the "0" ticket.
            AccessRequestOkBody response = methodRegistry.createAccessRequestOkBody(0);
            _channel.sync();
            _connection.writeFrame(response.generateFrame(getChannelId()));
        }
    }

    @Override
    public void receiveBasicAck(final long deliveryTag, final boolean multiple)
    {
        _channel.acknowledgeMessage(deliveryTag, multiple);
    }

    @Override
    public void receiveBasicCancel(final AMQShortString consumerTag, final boolean nowait)
    {
        _channel.unsubscribeConsumer(consumerTag);
        if (!nowait)
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            BasicCancelOkBody cancelOkBody = methodRegistry.createBasicCancelOkBody(consumerTag);
            _channel.sync();
            _connection.writeFrame(cancelOkBody.generateFrame(getChannelId()));
        }
    }

    @Override
    public void receiveBasicConsume(final AMQShortString queueNameStr,
                                    AMQShortString consumerTag,
                                    final boolean noLocal,
                                    final boolean noAck,
                                    final boolean exclusive,
                                    final boolean nowait,
                                    final FieldTable arguments)
    {
        VirtualHostImpl<?, ?, ?> vHost = _connection.getVirtualHost();
        _channel.sync();
        String queueName = queueNameStr == null ? null : queueNameStr.asString();

        MessageSource queue = queueName == null ? _channel.getDefaultQueue() : vHost.getQueue(queueName);
        final Collection<MessageSource> sources = new HashSet<>();
        if (queue != null)
        {
            sources.add(queue);
        }
        else if (vHost.getContextValue(Boolean.class, "qpid.enableMultiQueueConsumers")
                 && arguments != null
                 && arguments.get("x-multiqueue") instanceof Collection)
        {
            for (Object object : (Collection<Object>) arguments.get("x-multiqueue"))
            {
                String sourceName = String.valueOf(object);
                sourceName = sourceName.trim();
                if (sourceName.length() != 0)
                {
                    MessageSource source = vHost.getMessageSource(sourceName);
                    if (source == null)
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
            queueName = arguments.get("x-multiqueue").toString();
        }

        if (sources.isEmpty())
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("No queue for '" + queueName + "'");
            }
            if (queueName != null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "No such queue, '" + queueName + "'");
            }
            else
            {
                closeConnection(AMQConstant.NOT_ALLOWED, "No queue name provided, no default queue defined.");
            }
        }
        else
        {
            try
            {
                consumerTag = _channel.consumeFromSource(consumerTag,
                                                         sources,
                                                         !noAck,
                                                         arguments,
                                                         exclusive,
                                                         noLocal);
                if (!nowait)
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createBasicConsumeOkBody(consumerTag);
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                }
            }
            catch (ConsumerTagInUseException cte)
            {

                closeConnection(AMQConstant.NOT_ALLOWED, "Non-unique consumer tag, '" + consumerTag + "'");
            }
            catch (AMQInvalidArgumentException ise)
            {
                closeConnection(AMQConstant.ARGUMENT_INVALID, ise.getMessage());


            }
            catch (AMQQueue.ExistingExclusiveConsumer e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, "Cannot subscribe to queue "
                                                            + queue.getName()
                                                            + " as it already has an existing exclusive consumer");

            }
            catch (AMQQueue.ExistingConsumerPreventsExclusive e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, "Cannot subscribe to queue "
                                                            + queue.getName()
                                                            + " exclusively as it already has a consumer");

            }
            catch (AccessControlException e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, "Cannot subscribe to queue "
                                                            + queue.getName()
                                                            + " permission denied");

            }
            catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, "Cannot subscribe to queue "
                                                            + queue.getName()
                                                            + " as it already has an incompatible exclusivity policy");

            }

        }
    }

    @Override
    public void receiveBasicGet(final AMQShortString queueName, final boolean noAck)
    {
        VirtualHostImpl vHost = _connection.getVirtualHost();
        _channel.sync();
        AMQQueue queue =
                queueName == null ? _channel.getDefaultQueue() : vHost.getQueue(queueName.toString());
        if (queue == null)
        {
            _logger.info("No queue for '" + queueName + "'");
            if (queueName != null)
            {
                closeConnection(AMQConstant.NOT_FOUND, "No such queue, '" + queueName + "'");

            }
            else
            {
                closeConnection(AMQConstant.NOT_ALLOWED, "No queue name provided, no default queue defined.");

            }
        }
        else
        {

            try
            {
                if (!performGet(queue, _connection, _channel, !noAck))
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();

                    BasicGetEmptyBody responseBody = methodRegistry.createBasicGetEmptyBody(null);


                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }
            }
            catch (AccessControlException e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());
            }
            catch (MessageSource.ExistingExclusiveConsumer e)
            {
                closeConnection(AMQConstant.NOT_ALLOWED, "Queue has an exclusive consumer");
            }
            catch (MessageSource.ExistingConsumerPreventsExclusive e)
            {
                closeConnection(AMQConstant.INTERNAL_ERROR,
                                "The GET request has been evaluated as an exclusive consumer, " +
                                "this is likely due to a programming error in the Qpid broker");
            }
            catch (MessageSource.ConsumerAccessRefused consumerAccessRefused)
            {
                closeConnection(AMQConstant.NOT_ALLOWED, "Queue has an incompatible exclusivity policy");
            }
        }
    }

    @Override
    public void receiveBasicPublish(final AMQShortString exchangeName,
                                    final AMQShortString routingKey,
                                    final boolean mandatory,
                                    final boolean immediate)
    {
        VirtualHostImpl vHost = _connection.getVirtualHost();

        MessageDestination destination;

        if (isDefaultExchange(exchangeName))
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
            closeChannel(AMQConstant.NOT_FOUND, "Unknown exchange name: " + exchangeName);
        }
        else
        {

            MessagePublishInfo info = new MessagePublishInfo(exchangeName,
                                                             immediate,
                                                             mandatory,
                                                             routingKey);

            try
            {
                _channel.setPublishFrame(info, destination);
            }
            catch (AccessControlException e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());

            }
        }
    }

    @Override
    public void receiveBasicQos(final long prefetchSize, final int prefetchCount, final boolean global)
    {
        _channel.sync();
        _channel.setCredit(prefetchSize, prefetchCount);


        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createBasicQosOkBody();
        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveBasicRecover(final boolean requeue, final boolean sync)
    {
        _channel.resend();

        if (sync)
        {
            MethodRegistry methodRegistry = _connection.getMethodRegistry();
            AMQMethodBody recoverOk = methodRegistry.createBasicRecoverSyncOkBody();
            _channel.sync();
            _connection.writeFrame(recoverOk.generateFrame(getChannelId()));

        }

    }

    @Override
    public void receiveBasicReject(final long deliveryTag, final boolean requeue)
    {
        MessageInstance message = _channel.getUnacknowledgedMessageMap().get(deliveryTag);

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
                                  ": Requeue:" + requeue +
                                  " on channel:" + _channel.debugIdentity());
                }

                if (requeue)
                {
                    //this requeue represents a message rejected from the pre-dispatch queue
                    //therefore we need to amend the delivery counter.
                    message.decrementDeliveryCount();

                    _channel.requeue(deliveryTag);
                }
                else
                {
                    // Since the Java client abuses the reject flag for requeing after rollback, we won't set reject here
                    // as it would prevent redelivery
                    // message.reject();

                    final boolean maxDeliveryCountEnabled = _channel.isMaxDeliveryCountEnabled(deliveryTag);
                    _logger.debug("maxDeliveryCountEnabled: "
                                  + maxDeliveryCountEnabled
                                  + " deliveryTag "
                                  + deliveryTag);
                    if (maxDeliveryCountEnabled)
                    {
                        final boolean deliveredTooManyTimes = _channel.isDeliveredTooManyTimes(deliveryTag);
                        _logger.debug("deliveredTooManyTimes: "
                                      + deliveredTooManyTimes
                                      + " deliveryTag "
                                      + deliveryTag);
                        if (deliveredTooManyTimes)
                        {
                            _channel.deadLetter(deliveryTag);
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
                        _channel.requeue(deliveryTag);
                    }
                }
            }
        }
    }

    @Override
    public void receiveChannelClose()
    {
        _channel.sync();
        _connection.closeChannel(_channel);

        _connection.writeFrame(new AMQFrame(_channel.getChannelId(),
                                            _connection.getMethodRegistry().createChannelCloseOkBody()));
    }

    @Override
    public void receiveChannelCloseOk()
    {
        _connection.closeChannelOk(getChannelId());
    }

    @Override
    public void receiveChannelFlow(final boolean active)
    {
        _channel.sync();
        _channel.setSuspended(!active);

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        AMQMethodBody responseBody = methodRegistry.createChannelFlowOkBody(active);
        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveExchangeBound(final AMQShortString exchangeName,
                                     final AMQShortString queueName,
                                     final AMQShortString routingKey)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        MethodRegistry methodRegistry = _connection.getMethodRegistry();

        _channel.sync();

        int replyCode;
        String replyText;

        if (isDefaultExchange(exchangeName))
        {
            if (routingKey == null)
            {
                if (queueName == null)
                {
                    replyCode = virtualHost.getQueues().isEmpty()
                            ? ExchangeBoundOkBody.NO_BINDINGS
                            : ExchangeBoundOkBody.OK;
                    replyText = null;

                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {
                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                }
            }
            else
            {
                if (queueName == null)
                {
                    replyCode = virtualHost.getQueue(routingKey.toString()) == null
                            ? ExchangeBoundOkBody.NO_QUEUE_BOUND_WITH_RK
                            : ExchangeBoundOkBody.OK;
                    replyText = null;
                }
                else
                {
                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {

                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        replyCode = queueName.equals(routingKey)
                                ? ExchangeBoundOkBody.OK
                                : ExchangeBoundOkBody.SPECIFIC_QUEUE_NOT_BOUND_WITH_RK;
                        replyText = null;
                    }
                }
            }
        }
        else
        {
            ExchangeImpl exchange = virtualHost.getExchange(exchangeName.toString());
            if (exchange == null)
            {

                replyCode = ExchangeBoundOkBody.EXCHANGE_NOT_FOUND;
                replyText = "Exchange '" + exchangeName + "' not found";
            }
            else if (routingKey == null)
            {
                if (queueName == null)
                {
                    if (exchange.hasBindings())
                    {
                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.NO_BINDINGS;
                        replyText = null;
                    }
                }
                else
                {

                    AMQQueue queue = virtualHost.getQueue(queueName.toString());
                    if (queue == null)
                    {
                        replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                        replyText = "Queue '" + queueName + "' not found";
                    }
                    else
                    {
                        if (exchange.isBound(queue))
                        {
                            replyCode = ExchangeBoundOkBody.OK;
                            replyText = null;
                        }
                        else
                        {
                            replyCode = ExchangeBoundOkBody.QUEUE_NOT_BOUND;
                            replyText = "Queue '"
                                        + queueName
                                        + "' not bound to exchange '"
                                        + exchangeName
                                        + "'";
                        }
                    }
                }
            }
            else if (queueName != null)
            {
                AMQQueue queue = virtualHost.getQueue(queueName.toString());
                if (queue == null)
                {
                    replyCode = ExchangeBoundOkBody.QUEUE_NOT_FOUND;
                    replyText = "Queue '" + queueName + "' not found";
                }
                else
                {
                    String bindingKey = routingKey == null ? null : routingKey.asString();
                    if (exchange.isBound(bindingKey, queue))
                    {

                        replyCode = ExchangeBoundOkBody.OK;
                        replyText = null;
                    }
                    else
                    {
                        replyCode = ExchangeBoundOkBody.SPECIFIC_QUEUE_NOT_BOUND_WITH_RK;
                        replyText = "Queue '" + queueName + "' not bound with routing key '" +
                                    routingKey + "' to exchange '" + exchangeName + "'";

                    }
                }
            }
            else
            {
                if (exchange.isBound(routingKey == null ? "" : routingKey.asString()))
                {

                    replyCode = ExchangeBoundOkBody.OK;
                    replyText = null;
                }
                else
                {
                    replyCode = ExchangeBoundOkBody.NO_QUEUE_BOUND_WITH_RK;
                    replyText =
                            "No queue bound with routing key '" + routingKey + "' to exchange '" + exchangeName + "'";
                }
            }
        }

        ExchangeBoundOkBody exchangeBoundOkBody =
                methodRegistry.createExchangeBoundOkBody(replyCode, AMQShortString.validValueOf(replyText));

        _connection.writeFrame(exchangeBoundOkBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveExchangeDeclare(final AMQShortString exchangeName,
                                       final AMQShortString type,
                                       final boolean passive,
                                       final boolean durable,
                                       final boolean autoDelete,
                                       final boolean internal,
                                       final boolean nowait,
                                       final FieldTable arguments)
    {
        ExchangeImpl exchange;
        VirtualHostImpl<?, ?, ?> virtualHost = _connection.getVirtualHost();
        if (isDefaultExchange(exchangeName))
        {
            if (!new AMQShortString(ExchangeDefaults.DIRECT_EXCHANGE_CLASS).equals(type))
            {
                closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare default exchange: "
                                                         + " of type "
                                                         + ExchangeDefaults.DIRECT_EXCHANGE_CLASS
                                                         + " to " + type + ".");
            }
            else if (!nowait)
            {
                MethodRegistry methodRegistry = _connection.getMethodRegistry();
                AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                _channel.sync();
                _connection.writeFrame(responseBody.generateFrame(getChannelId()));
            }

        }
        else
        {
            if (passive)
            {
                exchange = virtualHost.getExchange(exchangeName.toString());
                if (exchange == null)
                {
                    closeChannel(AMQConstant.NOT_FOUND, "Unknown exchange: " + exchangeName);
                }
                else if (!(type == null || type.length() == 0) && !exchange.getType().equals(type.asString()))
                {

                    closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                                             +
                                                             exchangeName
                                                             + " of type "
                                                             + exchange.getType()
                                                             + " to "
                                                             + type
                                                             + ".");
                }
                else if (!nowait)
                {
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                    _channel.sync();
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }

            }
            else
            {
                try
                {
                    String name = exchangeName == null ? null : exchangeName.intern().toString();
                    String typeString = type == null ? null : type.intern().toString();

                    Map<String, Object> attributes = new HashMap<String, Object>();
                    if (arguments != null)
                    {
                        attributes.putAll(FieldTable.convertToMap(arguments));
                    }
                    attributes.put(org.apache.qpid.server.model.Exchange.ID, null);
                    attributes.put(org.apache.qpid.server.model.Exchange.NAME, name);
                    attributes.put(org.apache.qpid.server.model.Exchange.TYPE, typeString);
                    attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
                    attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                                   autoDelete ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
                    if (!attributes.containsKey(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE))
                    {
                        attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
                    }
                    exchange = virtualHost.createExchange(attributes);

                    if (!nowait)
                    {
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createExchangeDeclareOkBody();
                        _channel.sync();
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                    }

                }
                catch (ReservedExchangeNameException e)
                {
                    closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to declare exchange: " + exchangeName +
                                                             " which begins with reserved prefix.");


                }
                catch (ExchangeExistsException e)
                {
                    exchange = e.getExistingExchange();
                    if (!new AMQShortString(exchange.getType()).equals(type))
                    {
                        closeConnection(AMQConstant.NOT_ALLOWED, "Attempt to redeclare exchange: "
                                                                 + exchangeName + " of type "
                                                                 + exchange.getType()
                                                                 + " to " + type + ".");

                    }
                }
                catch (NoFactoryForTypeException e)
                {
                    closeConnection(AMQConstant.COMMAND_INVALID, "Unknown exchange type '"
                                                                 + e.getType()
                                                                 + "' for exchange '"
                                                                 + exchangeName
                                                                 + "'");

                }
                catch (AccessControlException e)
                {
                    closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());

                }
                catch (UnknownConfiguredObjectException e)
                {
                    // note - since 0-8/9/9-1 can't set the alt. exchange this exception should never occur
                    final String message = "Unknown alternate exchange "
                                           + (e.getName() != null
                            ? "name: \"" + e.getName() + "\""
                            : "id: " + e.getId());
                    closeConnection(AMQConstant.NOT_FOUND, message);

                }
                catch (IllegalArgumentException e)
                {
                    closeConnection(AMQConstant.COMMAND_INVALID, "Error creating exchange '"
                                                                 + exchangeName
                                                                 + "': "
                                                                 + e.getMessage());

                }
            }
        }

    }

    @Override
    public void receiveExchangeDelete(final AMQShortString exchangeStr, final boolean ifUnused, final boolean nowait)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        _channel.sync();
        try
        {

            if (isDefaultExchange(exchangeStr))
            {
                closeConnection(AMQConstant.NOT_ALLOWED,
                                "Default Exchange cannot be deleted");

            }

            else
            {
                final String exchangeName = exchangeStr.toString();

                final ExchangeImpl exchange = virtualHost.getExchange(exchangeName);
                if (exchange == null)
                {
                    closeChannel(AMQConstant.NOT_FOUND, "No such exchange: " + exchangeStr);
                }
                else
                {
                    virtualHost.removeExchange(exchange, !ifUnused);

                    ExchangeDeleteOkBody responseBody = _connection.getMethodRegistry().createExchangeDeleteOkBody();

                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }
            }
        }
        catch (ExchangeIsAlternateException e)
        {
            closeChannel(AMQConstant.NOT_ALLOWED, "Exchange in use as an alternate exchange");
        }
        catch (RequiredExchangeException e)
        {
            closeChannel(AMQConstant.NOT_ALLOWED, "Exchange '" + exchangeStr + "' cannot be deleted");
        }
        catch (AccessControlException e)
        {
            closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());
        }
    }

    @Override
    public void receiveQueueBind(final AMQShortString queueName,
                                 final AMQShortString exchange,
                                 AMQShortString routingKey,
                                 final boolean nowait,
                                 final FieldTable argumentsTable)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        AMQQueue<?> queue;
        if (queueName == null)
        {

            queue = _channel.getDefaultQueue();

            if (queue != null)
            {
                if (routingKey == null)
                {
                    routingKey = AMQShortString.valueOf(queue.getName());
                }
                else
                {
                    routingKey = routingKey.intern();
                }
            }
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
            routingKey = routingKey == null ? AMQShortString.EMPTY_STRING : routingKey.intern();
        }

        if (queue == null)
        {
            String message = queueName == null
                    ? "No default queue defined on channel and queue was null"
                    : "Queue " + queueName + " does not exist.";
                closeChannel(AMQConstant.NOT_FOUND, message);
        }
        else if (isDefaultExchange(exchange))
        {
            closeConnection(AMQConstant.NOT_ALLOWED,
                            "Cannot bind the queue " + queueName + " to the default exchange"
                           );

        }
        else
        {

            final String exchangeName = exchange.toString();

            final ExchangeImpl exch = virtualHost.getExchange(exchangeName);
            if (exch == null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "Exchange " + exchangeName + " does not exist.");
            }
            else
            {

                try
                {

                    Map<String, Object> arguments = FieldTable.convertToMap(argumentsTable);
                    String bindingKey = String.valueOf(routingKey);

                    if (!exch.isBound(bindingKey, arguments, queue))
                    {

                        if (!exch.addBinding(bindingKey, queue, arguments)
                            && ExchangeDefaults.TOPIC_EXCHANGE_CLASS.equals(
                                exch.getType()))
                        {
                            exch.replaceBinding(bindingKey, queue, arguments);
                        }
                    }

                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Binding queue "
                                     + queue
                                     + " to exchange "
                                     + exch
                                     + " with routing key "
                                     + routingKey);
                    }
                    if (!nowait)
                    {
                        _channel.sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        AMQMethodBody responseBody = methodRegistry.createQueueBindOkBody();
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                    }
                }
                catch (AccessControlException e)
                {
                    closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());
                }
            }
        }
    }

    @Override
    public void receiveQueueDeclare(final AMQShortString queueStr,
                                    final boolean passive,
                                    final boolean durable,
                                    final boolean exclusive,
                                    final boolean autoDelete,
                                    final boolean nowait,
                                    final FieldTable arguments)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();

        final AMQShortString queueName;

        // if we aren't given a queue name, we create one which we return to the client
        if ((queueStr == null) || (queueStr.length() == 0))
        {
            queueName = new AMQShortString("tmp_" + UUID.randomUUID());
        }
        else
        {
            queueName = queueStr.intern();
        }

        AMQQueue queue;

        //TODO: do we need to check that the queue already exists with exactly the same "configuration"?


        if (passive)
        {
            queue = virtualHost.getQueue(queueName.toString());
            if (queue == null)
            {
                closeChannel(AMQConstant.NOT_FOUND,
                             "Queue: " + queueName + " not found on VirtualHost(" + virtualHost + ").");
            }
            else
            {
                if (!queue.verifySessionAccess(_channel))
                {
                    closeConnection(AMQConstant.NOT_ALLOWED,
                                    "Queue "
                                    + queue.getName()
                                    + " is exclusive, but not created on this Connection.");
                }
                else
                {
                    //set this as the default queue on the channel:
                    _channel.setDefaultQueue(queue);
                    if (!nowait)
                    {
                        _channel.sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeclareOkBody responseBody =
                                methodRegistry.createQueueDeclareOkBody(queueName,
                                                                        queue.getQueueDepthMessages(),
                                                                        queue.getConsumerCount());
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                        _logger.info("Queue " + queueName + " declared successfully");
                    }
                }
            }
        }
        else
        {

            try
            {
                Map<String, Object> attributes =
                        QueueArgumentsConverter.convertWireArgsToModel(FieldTable.convertToMap(arguments));
                final String queueNameString = AMQShortString.toString(queueName);
                attributes.put(Queue.NAME, queueNameString);
                attributes.put(Queue.ID, UUID.randomUUID());
                attributes.put(Queue.DURABLE, durable);

                LifetimePolicy lifetimePolicy;
                ExclusivityPolicy exclusivityPolicy;

                if (exclusive)
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


                queue = virtualHost.createQueue(attributes);

                _channel.setDefaultQueue(queue);

                if (!nowait)
                {
                    _channel.sync();
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    QueueDeclareOkBody responseBody =
                            methodRegistry.createQueueDeclareOkBody(queueName,
                                                                    queue.getQueueDepthMessages(),
                                                                    queue.getConsumerCount());
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                    _logger.info("Queue " + queueName + " declared successfully");
                }
            }
            catch (QueueExistsException qe)
            {

                queue = qe.getExistingQueue();

                if (!queue.verifySessionAccess(_channel))
                {
                    closeConnection(AMQConstant.NOT_ALLOWED,
                                    "Queue "
                                    + queue.getName()
                                    + " is exclusive, but not created on this Connection.");

                }
                else if (queue.isExclusive() != exclusive)
                {

                    closeChannel(AMQConstant.ALREADY_EXISTS,
                                 "Cannot re-declare queue '"
                                 + queue.getName()
                                 + "' with different exclusivity (was: "
                                 + queue.isExclusive()
                                 + " requested "
                                 + exclusive
                                 + ")");
                }
                else if ((autoDelete
                          && queue.getLifetimePolicy() != LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS)
                         || (!autoDelete && queue.getLifetimePolicy() != ((exclusive
                                                                           && !durable)
                        ? LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                        : LifetimePolicy.PERMANENT)))
                {
                    closeChannel(AMQConstant.ALREADY_EXISTS,
                                 "Cannot re-declare queue '"
                                 + queue.getName()
                                 + "' with different lifetime policy (was: "
                                 + queue.getLifetimePolicy()
                                 + " requested autodelete: "
                                 + autoDelete
                                 + ")");
                }
                else if (queue.isDurable() != durable)
                {
                    closeChannel(AMQConstant.ALREADY_EXISTS,
                                 "Cannot re-declare queue '"
                                 + queue.getName()
                                 + "' with different durability (was: "
                                 + queue.isDurable()
                                 + " requested "
                                 + durable
                                 + ")");
                }
                else
                {
                    _channel.setDefaultQueue(queue);
                    if (!nowait)
                    {
                        _channel.sync();
                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeclareOkBody responseBody =
                                methodRegistry.createQueueDeclareOkBody(queueName,
                                                                        queue.getQueueDepthMessages(),
                                                                        queue.getConsumerCount());
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                        _logger.info("Queue " + queueName + " declared successfully");
                    }
                }
            }
            catch (AccessControlException e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());
            }

        }
    }

    @Override
    public void receiveQueueDelete(final AMQShortString queueName,
                                   final boolean ifUnused,
                                   final boolean ifEmpty,
                                   final boolean nowait)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        _channel.sync();
        AMQQueue queue;
        if (queueName == null)
        {

            //get the default queue on the channel:
            queue = _channel.getDefaultQueue();
        }
        else
        {
            queue = virtualHost.getQueue(queueName.toString());
        }

        if (queue == null)
        {
            closeChannel(AMQConstant.NOT_FOUND, "Queue " + queueName + " does not exist.");

        }
        else
        {
            if (ifEmpty && !queue.isEmpty())
            {
                closeChannel(AMQConstant.IN_USE, "Queue: " + queueName + " is not empty.");
            }
            else if (ifUnused && !queue.isUnused())
            {
                // TODO - Error code
                closeChannel(AMQConstant.IN_USE, "Queue: " + queueName + " is still used.");
            }
            else
            {
                if (!queue.verifySessionAccess(_channel))
                {
                    closeConnection(AMQConstant.NOT_ALLOWED,
                                    "Queue "
                                    + queue.getName()
                                    + " is exclusive, but not created on this Connection.");

                }
                else
                {
                    int purged = 0;
                    try
                    {
                        purged = virtualHost.removeQueue(queue);

                        MethodRegistry methodRegistry = _connection.getMethodRegistry();
                        QueueDeleteOkBody responseBody = methodRegistry.createQueueDeleteOkBody(purged);
                        _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                    }
                    catch (AccessControlException e)
                    {
                        closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());

                    }
                }
            }
        }
    }

    @Override
    public void receiveQueuePurge(final AMQShortString queueName, final boolean nowait)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();
        AMQQueue queue = null;
        if (queueName == null && (queue = _channel.getDefaultQueue()) == null)
        {

            closeConnection(AMQConstant.NOT_ALLOWED, "No queue specified.");
        }
        else if ((queueName != null) && (queue = virtualHost.getQueue(queueName.toString())) == null)
        {
            closeChannel(AMQConstant.NOT_FOUND, "Queue " + queueName + " does not exist.");
        }
        else if (!queue.verifySessionAccess(_channel))
        {
            closeConnection(AMQConstant.NOT_ALLOWED,
                            "Queue is exclusive, but not created on this Connection."
                           );
        }
        else
        {
            try
            {
                long purged = queue.clearQueue();
                if (!nowait)
                {
                    _channel.sync();
                    MethodRegistry methodRegistry = _connection.getMethodRegistry();
                    AMQMethodBody responseBody = methodRegistry.createQueuePurgeOkBody(purged);
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));

                }
            }
            catch (AccessControlException e)
            {
                closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());

            }

        }
    }

    @Override
    public void receiveQueueUnbind(final AMQShortString queueName,
                                   final AMQShortString exchange,
                                   AMQShortString routingKey,
                                   final FieldTable arguments)
    {
        VirtualHostImpl virtualHost = _connection.getVirtualHost();



        final boolean useDefaultQueue = queueName == null;
        final AMQQueue queue = useDefaultQueue
                ? _channel.getDefaultQueue()
                : virtualHost.getQueue(queueName.toString());


        if (queue == null)
        {
            String message = useDefaultQueue
                    ? "No default queue defined on channel and queue was null"
                    : "Queue " + queueName + " does not exist.";
            closeChannel(AMQConstant.NOT_FOUND, message);
        }
        else if (isDefaultExchange(exchange))
        {
            closeConnection(AMQConstant.NOT_ALLOWED, "Cannot unbind the queue "
                                                     + queue.getName()
                                                     + " from the default exchange");

        }
        else
        {

            final ExchangeImpl exch = virtualHost.getExchange(exchange.toString());

            if (exch == null)
            {
                closeChannel(AMQConstant.NOT_FOUND, "Exchange " + exchange + " does not exist.");
            }
            else if (!exch.hasBinding(String.valueOf(routingKey), queue))
            {
                closeChannel(AMQConstant.NOT_FOUND, "No such binding");
            }
            else
            {
                try
                {
                    exch.deleteBinding(String.valueOf(routingKey), queue);

                    final AMQMethodBody responseBody = _connection.getMethodRegistry().createQueueUnbindOkBody();
                    _channel.sync();
                    _connection.writeFrame(responseBody.generateFrame(getChannelId()));
                }
                catch (AccessControlException e)
                {
                    closeConnection(AMQConstant.ACCESS_REFUSED, e.getMessage());

                }
            }

        }
    }

    @Override
    public void receiveTxSelect()
    {
        _channel.setLocalTransactional();

        MethodRegistry methodRegistry = _connection.getMethodRegistry();
        TxSelectOkBody responseBody = methodRegistry.createTxSelectOkBody();
        _connection.writeFrame(responseBody.generateFrame(getChannelId()));

    }

    @Override
    public void receiveTxCommit()
    {
        if (!_channel.isTransactional())
        {
            closeChannel(AMQConstant.COMMAND_INVALID, "Fatal error: commit called on non-transactional channel");
        }
        _channel.commit(new Runnable()
        {

            @Override
            public void run()
            {
                MethodRegistry methodRegistry = _connection.getMethodRegistry();
                AMQMethodBody responseBody = methodRegistry.createTxCommitOkBody();
                _connection.writeFrame(responseBody.generateFrame(getChannelId()));
            }
        }, true);

    }

    @Override
    public void receiveTxRollback()
    {
        if (!_channel.isTransactional())
        {
            closeChannel(AMQConstant.COMMAND_INVALID, "Fatal error: rollback called on non-transactional channel");
        }

        final MethodRegistry methodRegistry = _connection.getMethodRegistry();
        final AMQMethodBody responseBody = methodRegistry.createTxRollbackOkBody();

        Runnable task = new Runnable()
        {

            public void run()
            {
                _connection.writeFrame(responseBody.generateFrame(getChannelId()));
            }
        };

        _channel.rollback(task);

        //Now resend all the unacknowledged messages back to the original subscribers.
        //(Must be done after the TxnRollback-ok response).
        // Why, are we not allowed to send messages back to client before the ok method?
        _channel.resend();
    }

    private void closeChannel(final AMQConstant cause, final String message)
    {
        _connection.closeChannelAndWriteFrame(_channel, cause, message);
    }

    private void closeConnection(final AMQConstant cause, final String message)
    {
        _connection.closeConnection(cause, message, getChannelId());
    }

    private int getChannelId()
    {
        return _channel.getChannelId();
    }

    private boolean isDefaultExchange(final AMQShortString exchangeName)
    {
        return exchangeName == null || AMQShortString.EMPTY_STRING.equals(exchangeName);
    }

    public static boolean performGet(final AMQQueue queue,
                                     final AMQProtocolEngine connection,
                                     final AMQChannel channel,
                                     final boolean acks)
            throws MessageSource.ExistingConsumerPreventsExclusive,
                   MessageSource.ExistingExclusiveConsumer, MessageSource.ConsumerAccessRefused
    {

        final FlowCreditManager singleMessageCredit = new MessageOnlyCreditManager(1L);

        final GetDeliveryMethod getDeliveryMethod =
                new GetDeliveryMethod(singleMessageCredit, connection, channel, queue);
        final RecordDeliveryMethod getRecordMethod = new RecordDeliveryMethod()
        {

            public void recordMessageDelivery(final ConsumerImpl sub,
                                              final MessageInstance entry,
                                              final long deliveryTag)
            {
                channel.addUnacknowledgedMessage(entry, deliveryTag, null);
            }
        };

        ConsumerTarget_0_8 target;
        EnumSet<ConsumerImpl.Option> options = EnumSet.of(ConsumerImpl.Option.TRANSIENT, ConsumerImpl.Option.ACQUIRES,
                                                          ConsumerImpl.Option.SEES_REQUEUES);
        if (acks)
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
        return getDeliveryMethod.hasDeliveredMessage();


    }


    private static class GetDeliveryMethod implements ClientDeliveryMethod
    {

        private final FlowCreditManager _singleMessageCredit;
        private final AMQProtocolEngine _connection;
        private final AMQChannel _channel;
        private final AMQQueue _queue;
        private boolean _deliveredMessage;

        public GetDeliveryMethod(final FlowCreditManager singleMessageCredit,
                                 final AMQProtocolEngine connection,
                                 final AMQChannel channel, final AMQQueue queue)
        {
            _singleMessageCredit = singleMessageCredit;
            _connection = connection;
            _channel = channel;
            _queue = queue;
        }

        @Override
        public long deliverToClient(final ConsumerImpl sub, final ServerMessage message,
                                    final InstanceProperties props, final long deliveryTag)
        {
            _singleMessageCredit.useCreditForMessage(message.getSize());
            long size = _connection.getProtocolOutputConverter().writeGetOk(message,
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
