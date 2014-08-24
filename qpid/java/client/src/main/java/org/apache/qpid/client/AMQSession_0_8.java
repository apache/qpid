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
package org.apache.qpid.client;


import static org.apache.qpid.configuration.ClientProperties.DEFAULT_FLOW_CONTROL_WAIT_FAILURE;
import static org.apache.qpid.configuration.ClientProperties.DEFAULT_FLOW_CONTROL_WAIT_NOTIFY_PERIOD;
import static org.apache.qpid.configuration.ClientProperties.QPID_FLOW_CONTROL_WAIT_FAILURE;
import static org.apache.qpid.configuration.ClientProperties.QPID_FLOW_CONTROL_WAIT_NOTIFY_PERIOD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQUndeliveredException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverNoopSupport;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.ReturnMessage;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.messaging.address.AddressHelper;
import org.apache.qpid.client.messaging.address.Link;
import org.apache.qpid.client.messaging.address.Node;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.state.AMQState;
import org.apache.qpid.client.state.AMQStateManager;
import org.apache.qpid.client.state.listener.SpecificMethodFrameListener;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.amqp_0_9.MethodRegistry_0_9;
import org.apache.qpid.framing.amqp_0_91.MethodRegistry_0_91;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.util.Strings;

public class AMQSession_0_8 extends AMQSession<BasicMessageConsumer_0_8, BasicMessageProducer_0_8>
{
    /** Used for debugging. */
    private static final Logger _logger = LoggerFactory.getLogger(AMQSession.class);

    public static final String QPID_SYNC_AFTER_CLIENT_ACK = "qpid.sync_after_client.ack";

    private final boolean _syncAfterClientAck =
            Boolean.parseBoolean(System.getProperty(QPID_SYNC_AFTER_CLIENT_ACK, "true"));

    /**
     * The period to wait while flow controlled before sending a log message confirming that the session is still
     * waiting on flow control being revoked
     */
    private final long _flowControlWaitPeriod = Long.getLong(QPID_FLOW_CONTROL_WAIT_NOTIFY_PERIOD,
                                                                 DEFAULT_FLOW_CONTROL_WAIT_NOTIFY_PERIOD);

    /**
     * The period to wait while flow controlled before declaring a failure
     */
    private final long _flowControlWaitFailure = Long.getLong(QPID_FLOW_CONTROL_WAIT_FAILURE,
                                                                  DEFAULT_FLOW_CONTROL_WAIT_FAILURE);

    /** Flow control */
    private FlowControlIndicator _flowControl = new FlowControlIndicator();

    /**
     * Creates a new session on a connection.
     *
     * @param con                     The connection on which to create the session.
     * @param channelId               The unique identifier for the session.
     * @param transacted              Indicates whether or not the session is transactional.
     * @param acknowledgeMode         The acknowledgement mode for the session.
     * @param messageFactoryRegistry  The message factory factory for the session.
     * @param defaultPrefetchHighMark The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLowMark  The number of prefetched messages at which to resume the session.
     */
    protected AMQSession_0_8(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
                             MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {

         super(con,channelId,transacted,acknowledgeMode,messageFactoryRegistry,defaultPrefetchHighMark,defaultPrefetchLowMark);
    }

    /**
     * Creates a new session on a connection with the default message factory factory.
     *
     * @param con                     The connection on which to create the session.
     * @param channelId               The unique identifier for the session.
     * @param transacted              Indicates whether or not the session is transactional.
     * @param acknowledgeMode         The acknowledgement mode for the session.
     * @param defaultPrefetchHigh     The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLow      The number of prefetched messages at which to resume the session.
     */
    AMQSession_0_8(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetchHigh,
               int defaultPrefetchLow)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetchHigh,
             defaultPrefetchLow);
    }

    private ProtocolVersion getProtocolVersion()
    {
        return getProtocolHandler().getProtocolVersion();
    }

    protected void acknowledgeImpl() throws JMSException
    {
        boolean syncRequired = false;
        while (true)
        {
            Long tag = getUnacknowledgedMessageTags().poll();
            if (tag == null)
            {
                break;
            }

            acknowledgeMessage(tag, false);
            syncRequired = true;
        }

        try
        {
            if (syncRequired && _syncAfterClientAck)
            {
                sync();
            }
        }
        catch (AMQException a)
        {
            throw new JMSAMQException("Failed to sync after acknowledge", a);
        }
    }

    public void acknowledgeMessage(long deliveryTag, boolean multiple)
    {
        BasicAckBody body = getMethodRegistry().createBasicAckBody(deliveryTag, multiple);

        final AMQFrame ackFrame = body.generateFrame(getChannelId());

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on channel " + getChannelId());
        }

        getProtocolHandler().writeFrame(ackFrame, !isTransacted());
        getUnacknowledgedMessageTags().remove(deliveryTag);
    }

    public void sendQueueBind(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
                              final AMQShortString exchangeName, final AMQDestination destination,
                              final boolean nowait) throws AMQException, FailoverException
    {
        if (destination == null || destination.getDestSyntax() == AMQDestination.DestSyntax.BURL)
        {

            getProtocolHandler().syncWrite(getProtocolHandler().getMethodRegistry().createQueueBindBody
                    (getTicket(), queueName, exchangeName, routingKey, false, arguments).
                    generateFrame(getChannelId()), QueueBindOkBody.class);

        }
        else
        {
            // Leaving this here to ensure the public method bindQueue in AMQSession.java works as expected.
            List<AMQDestination.Binding> bindings = new ArrayList<AMQDestination.Binding>();
            bindings.addAll(destination.getNode().getBindings());

            String defaultExchange = destination.getAddressType() == AMQDestination.TOPIC_TYPE ?
                    destination.getAddressName(): "amq.topic";

            for (AMQDestination.Binding binding: bindings)
            {
                // Currently there is a bug (QPID-3317) with setting up and tearing down x-bindings for link.
                // The null check below is a way to side step that issue while fixing QPID-4146
                // Note this issue only affects producers.
                if (binding.getQueue() == null && queueName == null)
                {
                    continue;
                }
                String queue = binding.getQueue() == null?
                        queueName.asString(): binding.getQueue();

                String exchange = binding.getExchange() == null ?
                        defaultExchange :
                        binding.getExchange();

                _logger.debug("Binding queue : " + queue +
                              " exchange: " + exchange +
                              " using binding key " + binding.getBindingKey() +
                              " with args " + Strings.printMap(binding.getArgs()));
                doBind(destination, binding, queue, exchange);
            }
        }
    }

    public void sendClose(long timeout) throws AMQException, FailoverException
    {
        // we also need to check the state manager for 08/09 as the
        // _connection variable may not be updated in time by the error receiving
        // thread.
        // We can't close the session if we are already in the process of
        // closing/closed the connection.

        if (!(getProtocolHandler().getStateManager().getCurrentState().equals(AMQState.CONNECTION_CLOSED)
            || getProtocolHandler().getStateManager().getCurrentState().equals(AMQState.CONNECTION_CLOSING)))
        {

            getProtocolHandler().closeSession(this);
            getProtocolHandler().syncWrite(getProtocolHandler().getMethodRegistry().createChannelCloseBody(AMQConstant.REPLY_SUCCESS.getCode(),
                                                                                                           new AMQShortString("JMS client closing channel"), 0, 0).generateFrame(getChannelId()),
                                           ChannelCloseOkBody.class, timeout);
            // When control resumes at this point, a reply will have been received that
            // indicates the broker has closed the channel successfully.
        }
    }

    public void commitImpl() throws AMQException, FailoverException, TransportException
    {
        // Acknowledge all delivered messages
        while (true)
        {
            Long tag = getDeliveredMessageTags().poll();
            if (tag == null)
            {
                break;
            }

            acknowledgeMessage(tag, false);
        }

        final AMQProtocolHandler handler = getProtocolHandler();

        handler.syncWrite(getProtocolHandler().getMethodRegistry().createTxCommitBody().generateFrame(getChannelId()), TxCommitOkBody.class);
    }

    public void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable, final boolean exclusive, final Map<String, Object> arguments) throws AMQException,
            FailoverException
    {
        FieldTable table = null;
        if(arguments != null && !arguments.isEmpty())
        {
            table = new FieldTable();
            for(Map.Entry<String, Object> entry : arguments.entrySet())
            {
                table.setObject(entry.getKey(), entry.getValue());
            }
        }
        sendQueueDeclare(name, durable, exclusive, autoDelete, table, false);
    }

    public void sendRecover() throws AMQException, FailoverException
    {
        enforceRejectBehaviourDuringRecover();
        getPrefetchedMessageTags().clear();
        getUnacknowledgedMessageTags().clear();

        if (isStrictAMQP())
        {
            // We can't use the BasicRecoverBody-OK method as it isn't part of the spec.

            BasicRecoverBody body = getMethodRegistry().createBasicRecoverBody(false);
            getAMQConnection().getProtocolHandler().writeFrame(body.generateFrame(getChannelId()));
            _logger.warn("Session Recover cannot be guaranteed with STRICT_AMQP. Messages may arrive out of order.");
        }
        else
        {
            // in Qpid the 0-8 spec was hacked to have a recover-ok method... this is bad
            // in 0-9 we used the cleaner addition of a new sync recover method with its own ok
            if(getProtocolHandler().getProtocolVersion().equals(ProtocolVersion.v8_0))
            {
                BasicRecoverBody body = getMethodRegistry().createBasicRecoverBody(false);
                getAMQConnection().getProtocolHandler().syncWrite(body.generateFrame(getChannelId()), BasicRecoverOkBody.class);
            }
            else if(getProtocolVersion().equals(ProtocolVersion.v0_9))
            {
                BasicRecoverSyncBody body = ((MethodRegistry_0_9)getMethodRegistry()).createBasicRecoverSyncBody(false);
                getAMQConnection().getProtocolHandler().syncWrite(body.generateFrame(getChannelId()), BasicRecoverSyncOkBody.class);
            }
            else if(getProtocolVersion().equals(ProtocolVersion.v0_91))
            {
                BasicRecoverSyncBody body = ((MethodRegistry_0_91)getMethodRegistry()).createBasicRecoverSyncBody(false);
                getAMQConnection().getProtocolHandler().syncWrite(body.generateFrame(getChannelId()), BasicRecoverSyncOkBody.class);
            }
            else
            {
                throw new RuntimeException("Unsupported version of the AMQP Protocol: " + getProtocolVersion());
            }
        }
    }

    private void enforceRejectBehaviourDuringRecover()
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Prefetched message: _unacknowledgedMessageTags :" + getUnacknowledgedMessageTags());
        }
        ArrayList<BasicMessageConsumer_0_8> consumersToCheck = new ArrayList<BasicMessageConsumer_0_8>(getConsumers().values());
        boolean messageListenerFound = false;
        boolean serverRejectBehaviourFound = false;
        for(BasicMessageConsumer_0_8 consumer : consumersToCheck)
        {
            if (consumer.isMessageListenerSet())
            {
                messageListenerFound = true;
            }
            if (RejectBehaviour.SERVER.equals(consumer.getRejectBehaviour()))
            {
                serverRejectBehaviourFound = true;
            }
        }
        _logger.debug("about to pre-reject messages for " + consumersToCheck.size() + " consumer(s)");

        if (serverRejectBehaviourFound)
        {
            //reject(false) any messages we don't want returned again
            switch(getAcknowledgeMode())
            {
                case Session.DUPS_OK_ACKNOWLEDGE:
                case Session.AUTO_ACKNOWLEDGE:
                    if (!messageListenerFound)
                    {
                        break;
                    }
                case Session.CLIENT_ACKNOWLEDGE:
                    for(Long tag : getUnacknowledgedMessageTags())
                    {
                        rejectMessage(tag, false);
                    }
                    break;
            }
        }
    }

    public void releaseForRollback()
    {
        // Reject all the messages that have been received in this session and
        // have not yet been acknowledged. Should look to remove
        // _deliveredMessageTags and use _txRangeSet as used by 0-10.
        // Otherwise messages will be able to arrive out of order to a second
        // consumer on the queue. Whilst this is within the JMS spec it is not
        // user friendly and avoidable.
        boolean normalRejectBehaviour = true;
        for (BasicMessageConsumer_0_8 consumer : getConsumers().values())
        {
            if(RejectBehaviour.SERVER.equals(consumer.getRejectBehaviour()))
            {
                normalRejectBehaviour = false;
                //no need to consult other consumers now, found server behaviour.
                break;
            }
        }

        while (true)
        {
            Long tag = getDeliveredMessageTags().poll();
            if (tag == null)
            {
                break;
            }

            rejectMessage(tag, normalRejectBehaviour);
        }
    }

    public void rejectMessage(long deliveryTag, boolean requeue)
    {
        if ((getAcknowledgeMode() == CLIENT_ACKNOWLEDGE) || (getAcknowledgeMode() == SESSION_TRANSACTED)||
                ((getAcknowledgeMode() == AUTO_ACKNOWLEDGE || getAcknowledgeMode() == DUPS_OK_ACKNOWLEDGE ) && hasMessageListeners()))
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rejecting delivery tag:" + deliveryTag + ":SessionHC:" + this.hashCode());
            }

            BasicRejectBody body = getMethodRegistry().createBasicRejectBody(deliveryTag, requeue);
            AMQFrame frame = body.generateFrame(getChannelId());

            getAMQConnection().getProtocolHandler().writeFrame(frame);
        }
    }

    public boolean isQueueBound(final AMQDestination destination) throws JMSException
    {
        return isQueueBound(destination.getExchangeName(),destination.getAMQQueueName(),destination.getAMQQueueName());
    }


    public boolean isQueueBound(final AMQShortString exchangeName, final AMQShortString queueName, final AMQShortString routingKey)
            throws JMSException
    {
        try
        {
            AMQMethodEvent response = new FailoverRetrySupport<AMQMethodEvent, AMQException>(
                    new FailoverProtectedOperation<AMQMethodEvent, AMQException>()
                    {
                        public AMQMethodEvent execute() throws AMQException, FailoverException
                        {
                            return sendExchangeBound(exchangeName, routingKey, queueName);

                        }
                    }, getAMQConnection()).execute();

            // Extract and return the response code from the query.
            ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();

            return (responseBody.getReplyCode() == 0);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Queue bound query failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a particular queue is bound to an exchange with a given key.
     *
     * Returns false if not connected to a Qpid broker which supports the necessary AMQP extension.
     */
    @Override
    protected boolean isBound(final AMQShortString exchangeName, final AMQShortString queueName, final AMQShortString routingKey)
            throws AMQException
    {
        if(!getAMQConnection().getDelegate().supportsIsBound())
        {
            return false;
        }

        AMQMethodEvent response = new FailoverNoopSupport<AMQMethodEvent, AMQException>(
                new FailoverProtectedOperation<AMQMethodEvent, AMQException>()
                {
                    public AMQMethodEvent execute() throws AMQException, FailoverException
                    {
                        return sendExchangeBound(exchangeName, routingKey, queueName);

                    }
                }, getAMQConnection()).execute();

        // Extract and return the response code from the query.
        ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();

        return (responseBody.getReplyCode() == 0);
    }


    protected boolean exchangeExists(final AMQShortString exchangeName)
            throws AMQException
    {
        if(!getAMQConnection().getDelegate().supportsIsBound())
        {
            return false;
        }

        AMQMethodEvent response = new FailoverNoopSupport<AMQMethodEvent, AMQException>(
                new FailoverProtectedOperation<AMQMethodEvent, AMQException>()
                {
                    public AMQMethodEvent execute() throws AMQException, FailoverException
                    {
                        return sendExchangeBound(exchangeName, null, null);

                    }
                }, getAMQConnection()).execute();

        // Extract and return the response code from the query.
        ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();

        // valid if no issues, or just no bindings
        return (responseBody.getReplyCode() == 0 || responseBody.getReplyCode() == 3);
    }

    private AMQMethodEvent sendExchangeBound(AMQShortString exchangeName,
                                             AMQShortString routingKey,
                                             AMQShortString queueName) throws AMQException, FailoverException
    {
        AMQFrame boundFrame = getProtocolHandler().getMethodRegistry().createExchangeBoundBody
                                (exchangeName, routingKey, queueName).generateFrame(getChannelId());

        return getProtocolHandler().syncWrite(boundFrame, ExchangeBoundOkBody.class);
    }

    @Override
    public void sendConsume(BasicMessageConsumer_0_8 consumer,
                                      AMQShortString queueName,
                                      boolean nowait,
                                      int tag) throws AMQException, FailoverException
    {
        queueName = preprocessAddressTopic(consumer, queueName);

        BasicConsumeBody body = getMethodRegistry().createBasicConsumeBody(getTicket(),
                                                                           queueName,
                                                                           new AMQShortString(String.valueOf(tag)),
                                                                           consumer.isNoLocal(),
                                                                           consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE,
                                                                           consumer.isExclusive(),
                                                                           nowait,
                                                                           consumer.getArguments());


        AMQFrame jmsConsume = body.generateFrame(getChannelId());

        if (nowait)
        {
            getProtocolHandler().writeFrame(jmsConsume);
        }
        else
        {
            getProtocolHandler().syncWrite(jmsConsume, BasicConsumeOkBody.class);
        }
    }

    @Override
    void createSubscriptionQueue(AMQDestination dest, boolean noLocal, String messageSelector) throws AMQException
    {
        final Link link = dest.getLink();
        final String queueName ;

        if (dest.getQueueName() == null)
        {
            queueName = link.getName() == null ? "TempQueue" + UUID.randomUUID() : link.getName();
            dest.setQueueName(new AMQShortString(queueName));
        }
        else
        {
            queueName = dest.getQueueName();
        }

        final Link.SubscriptionQueue queueProps = link.getSubscriptionQueue();
        final Map<String,Object> arguments = queueProps.getDeclareArgs();
        if (!arguments.containsKey((AddressHelper.NO_LOCAL)))
        {
            arguments.put(AddressHelper.NO_LOCAL, noLocal);
        }

        if (link.isDurable() && queueName.startsWith("TempQueue"))
        {
            throw new AMQException("You cannot mark a subscription queue as durable without providing a name for the link.");
        }

        (new FailoverNoopSupport<Void, AMQException>(
                new FailoverProtectedOperation<Void, AMQException>()
                {
                    public Void execute() throws AMQException, FailoverException
                    {

                        // not setting alternate exchange
                        sendQueueDeclare(AMQShortString.valueOf(queueName),
                                         link.isDurable(),
                                         queueProps.isExclusive(),
                                         queueProps.isAutoDelete(),
                                         FieldTable.convertToFieldTable(arguments),
                                         false);

                        return null;
                    }
                }, getAMQConnection())).execute();


        Map<String,Object> bindingArguments = new HashMap<String, Object>();
        bindingArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue().toString(), messageSelector == null ? "" : messageSelector);

        final AMQDestination.Binding binding = new AMQDestination.Binding(dest.getAddressName(), queueName, dest.getSubject(), bindingArguments);
        doBind(dest, binding, queueName, dest.getAddressName());

    }

    @Override
    public void sendExchangeDeclare(final AMQShortString name, final AMQShortString type, final boolean nowait,
            boolean durable, boolean autoDelete, boolean internal) throws AMQException, FailoverException
    {
        //The 'noWait' parameter is only used on the 0-10 path, it is ignored on the 0-8/0-9/0-9-1 path

        ExchangeDeclareBody body = getMethodRegistry().createExchangeDeclareBody(getTicket(),name,type,
                                                                                 name.toString().startsWith("amq."),
                                                                                 durable, autoDelete, internal, false, null);
        AMQFrame exchangeDeclare = body.generateFrame(getChannelId());

        getProtocolHandler().syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
    }

    @Override
    public void sendExchangeDeclare(final AMQShortString name, final AMQShortString type, final boolean nowait,
                                    boolean durable, boolean autoDelete, FieldTable arguments, final boolean passive) throws AMQException, FailoverException
    {
        //The 'noWait' parameter is only used on the 0-10 path, it is ignored on the 0-8/0-9/0-9-1 path

        MethodRegistry methodRegistry = getMethodRegistry();
        ExchangeDeclareBody body = methodRegistry.createExchangeDeclareBody(getTicket(),
                                                                            name,
                                                                            type,
                                                                            passive || name.toString().startsWith("amq."),
                                                                            durable,
                                                                            autoDelete,
                                                                            false,
                                                                            false,
                                                                            arguments);
        AMQFrame exchangeDeclare = body.generateFrame(getChannelId());

        getProtocolHandler().syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
    }

    public void sendExchangeDelete(final String name) throws AMQException, FailoverException
    {
        ExchangeDeleteBody body =
                getMethodRegistry().createExchangeDeleteBody(getTicket(),AMQShortString.valueOf(name),false, false);
        AMQFrame exchangeDeclare = body.generateFrame(getChannelId());

        getProtocolHandler().syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
    }

    private void sendQueueDeclare(final AMQDestination amqd, boolean passive) throws AMQException, FailoverException
    {
        AMQShortString queueName = amqd.getAMQQueueName();
        boolean durable = amqd.isDurable();
        boolean exclusive = amqd.isExclusive();
        boolean autoDelete = amqd.isAutoDelete();
        FieldTable arguments = null;
        sendQueueDeclare(queueName, durable, exclusive, autoDelete, arguments, passive);
    }

    private void sendQueueDeclare(final AMQShortString queueName,
                                  final boolean durable,
                                  final boolean exclusive,
                                  final boolean autoDelete, final FieldTable arguments, final boolean passive)
            throws AMQException, FailoverException
    {
        QueueDeclareBody body =
                getMethodRegistry().createQueueDeclareBody(getTicket(),
                                                           queueName,
                                                           passive,
                                                           durable,
                                                           exclusive,
                                                           autoDelete,
                                                           false,
                                                           arguments);

        AMQFrame queueDeclare = body.generateFrame(getChannelId());

        getProtocolHandler().syncWrite(queueDeclare, QueueDeclareOkBody.class);
    }

    @Override
    protected AMQShortString declareQueue(final AMQDestination amqd, final boolean noLocal,
                                          final boolean nowait, final boolean passive) throws AMQException
    {
        //The 'noWait' parameter is only used on the 0-10 path, it is ignored on the 0-8/0-9/0-9-1 path

        final AMQProtocolHandler protocolHandler = getProtocolHandler();
        return new FailoverNoopSupport<AMQShortString, AMQException>(
                new FailoverProtectedOperation<AMQShortString, AMQException>()
                {
                    public AMQShortString execute() throws AMQException, FailoverException
                    {
                        // Generate the queue name if the destination indicates that a client generated name is to be used.
                        if (amqd.isNameRequired())
                        {
                            amqd.setQueueName(protocolHandler.generateQueueName());
                        }

                        sendQueueDeclare(amqd, passive);

                        return amqd.getAMQQueueName();
                    }
                }, getAMQConnection()).execute();
    }

    public void sendQueueDelete(final AMQShortString queueName) throws AMQException, FailoverException
    {
        QueueDeleteBody body = getMethodRegistry().createQueueDeleteBody(getTicket(),
                                                                         queueName,
                                                                         false,
                                                                         false,
                                                                         true);
        AMQFrame queueDeleteFrame = body.generateFrame(getChannelId());

        getProtocolHandler().syncWrite(queueDeleteFrame, QueueDeleteOkBody.class);
    }

    public void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException
    {
        ChannelFlowBody body = getMethodRegistry().createChannelFlowBody(!suspend);
        AMQFrame channelFlowFrame = body.generateFrame(getChannelId());
        getAMQConnection().getProtocolHandler().syncWrite(channelFlowFrame, ChannelFlowOkBody.class);
    }

    public BasicMessageConsumer_0_8 createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
            final int prefetchLow, final boolean noLocal, final boolean exclusive, String messageSelector, final FieldTable arguments,
            final boolean noConsume, final boolean autoClose)  throws JMSException
    {
       return new BasicMessageConsumer_0_8(getChannelId(), getAMQConnection(), destination, messageSelector, noLocal,
               getMessageFactoryRegistry(),this, arguments, prefetchHigh, prefetchLow,
                                 exclusive, getAcknowledgeMode(), noConsume, autoClose);
    }


    public BasicMessageProducer_0_8 createMessageProducer(final Destination destination, final Boolean mandatory,
            final Boolean immediate, long producerId) throws JMSException
    {
       try
       {
           return new BasicMessageProducer_0_8(getAMQConnection(), (AMQDestination) destination, isTransacted(), getChannelId(),
                                 this, getProtocolHandler(), producerId, immediate, mandatory);
       }
       catch (AMQException e)
       {
           JMSException ex = new JMSException("Error creating producer");
           ex.initCause(e);
           ex.setLinkedException(e);

           throw ex;
       }
    }


    @Override public void messageReceived(UnprocessedMessage message)
    {

        if (message instanceof ReturnMessage)
        {
            // Return of the bounced message.
            returnBouncedMessage((ReturnMessage) message);
        }
        else
        {
            super.messageReceived(message);
        }
    }

    private void returnBouncedMessage(final ReturnMessage msg)
    {
        getAMQConnection().performConnectionTask(new Runnable()
        {
            public void run()
            {
                try
                {
                    // Bounced message is processed here, away from the mina thread
                    AbstractJMSMessage bouncedMessage =
                            getMessageFactoryRegistry().createMessage(0, false, msg.getExchange(),
                                    msg.getRoutingKey(), msg.getContentHeader(), msg.getBodies(), _queueDestinationCache, _topicDestinationCache);
                    AMQConstant errorCode = AMQConstant.getConstant(msg.getReplyCode());
                    AMQShortString reason = msg.getReplyText();
                    _logger.debug("Message returned with error code " + errorCode + " (" + reason + ")");

                    // @TODO should this be moved to an exception handler of sorts. Somewhere errors are converted to correct execeptions.
                    if (errorCode == AMQConstant.NO_CONSUMERS)
                    {
                        getAMQConnection().exceptionReceived(new AMQNoConsumersException("Error: " + reason, bouncedMessage, null));
                    } else if (errorCode == AMQConstant.NO_ROUTE)
                    {
                        getAMQConnection().exceptionReceived(new AMQNoRouteException("Error: " + reason, bouncedMessage, null));
                    } else
                    {
                        getAMQConnection().exceptionReceived(
                                new AMQUndeliveredException(errorCode, "Error: " + reason, bouncedMessage, null));
                    }

                } catch (Exception e)
                {
                    _logger.error(
                            "Caught exception trying to raise undelivered message exception (dump follows) - ignoring...",
                            e);
                }
            }
        });
    }




    public void sendRollback() throws AMQException, FailoverException
    {
        TxRollbackBody body = getMethodRegistry().createTxRollbackBody();
        AMQFrame frame = body.generateFrame(getChannelId());
        getProtocolHandler().syncWrite(frame, TxRollbackOkBody.class);
    }

    public void setPrefetchLimits(final int messagePrefetch, final long sizePrefetch) throws AMQException
    {
        new FailoverRetrySupport<Object, AMQException>(
                new FailoverProtectedOperation<Object, AMQException>()
                {
                    public Object execute() throws AMQException, FailoverException
                    {

                        BasicQosBody basicQosBody = getProtocolHandler().getMethodRegistry().createBasicQosBody(sizePrefetch, messagePrefetch, false);

                        // todo send low water mark when protocol allows.
                        // todo Be aware of possible changes to parameter order as versions change.
                        getProtocolHandler().syncWrite(basicQosBody.generateFrame(getChannelId()), BasicQosOkBody.class);

                        return null;
                    }
                 }, getAMQConnection()).execute();
    }

    public DestinationCache<AMQQueue> getQueueDestinationCache()
    {
        return _queueDestinationCache;
    }

    public DestinationCache<AMQTopic> getTopicDestinationCache()
    {
        return _topicDestinationCache;
    }

    class QueueDeclareOkHandler extends SpecificMethodFrameListener
    {

        private long _messageCount;
        private long _consumerCount;

        public QueueDeclareOkHandler()
        {
            super(getChannelId(), QueueDeclareOkBody.class);
        }

        public boolean processMethod(int channelId, AMQMethodBody frame) //throws AMQException
        {
            boolean matches = super.processMethod(channelId, frame);
            if (matches)
            {
                QueueDeclareOkBody declareOk = (QueueDeclareOkBody) frame;
                _messageCount = declareOk.getMessageCount();
                _consumerCount = declareOk.getConsumerCount();
            }
            return matches;
        }

        public long getMessageCount()
        {
            return _messageCount;
        }

        public long getConsumerCount()
        {
            return _consumerCount;
        }
    }

    protected Long requestQueueDepth(AMQDestination amqd, boolean sync) throws AMQException, FailoverException
    {
        if(isBound(null, amqd.getAMQQueueName(), null))
        {
            AMQFrame queueDeclare =
                    getMethodRegistry().createQueueDeclareBody(getTicket(),
                                                               amqd.getAMQQueueName(),
                                                               true,
                                                               amqd.isDurable(),
                                                               amqd.isExclusive(),
                                                               amqd.isAutoDelete(),
                                                               false,
                                                               null).generateFrame(getChannelId());
            QueueDeclareOkHandler okHandler = new QueueDeclareOkHandler();
            getProtocolHandler().writeCommandFrameAndWaitForReply(queueDeclare, okHandler);
            return okHandler.getMessageCount();
        }
        else
        {
            return 0l;
        }
    }

    protected boolean tagLE(long tag1, long tag2)
    {
        return tag1 <= tag2;
    }

    protected boolean updateRollbackMark(long currentMark, long deliveryTag)
    {
        return false;
    }

    public AMQMessageDelegateFactory getMessageDelegateFactory()
    {
        return AMQMessageDelegateFactory.FACTORY_0_8;
    }

    public void sync() throws AMQException
    {
        declareExchange(new AMQShortString("amq.direct"), new AMQShortString("direct"), false);
    }

    @Override
    public void resolveAddress(final AMQDestination dest, final boolean isConsumer, final boolean noLocal)
            throws AMQException
    {
        if(!isAddrSyntaxSupported())
        {
            throw new UnsupportedAddressSyntaxException(dest);
        }
        super.resolveAddress(dest, isConsumer, noLocal);
    }

    private boolean isAddrSyntaxSupported()
    {
        return ((AMQConnectionDelegate_8_0)(getAMQConnection().getDelegate())).isAddrSyntaxSupported();
    }

    public int resolveAddressType(AMQDestination dest) throws AMQException
    {
        int type = dest.getAddressType();
        String name = dest.getAddressName();
        if (type != AMQDestination.UNKNOWN_TYPE)
        {
            return type;
        }
        else
        {
            boolean isExchange = exchangeExists(AMQShortString.valueOf(name));
            boolean isQueue = isBound(null,AMQShortString.valueOf(name), null);

            if (!isExchange && !isQueue)
            {
                type = dest instanceof AMQTopic ? AMQDestination.TOPIC_TYPE : AMQDestination.QUEUE_TYPE;
            }
            else if (!isExchange)
            {
                //name refers to a queue
                type = AMQDestination.QUEUE_TYPE;
            }
            else if (!isQueue)
            {
                //name refers to an exchange
                type = AMQDestination.TOPIC_TYPE;
            }
            else
            {
                //both a queue and exchange exist for that name
                throw new AMQException("Ambiguous address, please specify queue or topic as node type");
            }
            dest.setAddressType(type);
            return type;
        }
    }

    protected void handleQueueNodeCreation(final AMQDestination dest, boolean noLocal) throws AMQException
    {
        final Node node = dest.getNode();
        final Map<String,Object> arguments = node.getDeclareArgs();
        if (!arguments.containsKey((AddressHelper.NO_LOCAL)))
        {
            arguments.put(AddressHelper.NO_LOCAL, noLocal);
        }
        String altExchange = node.getAlternateExchange();
        if(altExchange != null && !"".equals(altExchange))
        {
            arguments.put("alternateExchange", altExchange);
        }

        (new FailoverNoopSupport<Void, AMQException>(
                new FailoverProtectedOperation<Void, AMQException>()
                {
                    public Void execute() throws AMQException, FailoverException
                    {

                        sendQueueDeclare(AMQShortString.valueOf(dest.getAddressName()),
                                         node.isDurable(),
                                         node.isExclusive(),
                                         node.isAutoDelete(),
                                         FieldTable.convertToFieldTable(arguments),
                                         false);

                        return null;
                    }
                }, getAMQConnection())).execute();


        createBindings(dest, dest.getNode().getBindings());
        sync();
    }

    void handleExchangeNodeCreation(AMQDestination dest) throws AMQException
    {
        Node node = dest.getNode();
        String altExchange = dest.getNode().getAlternateExchange();
        Map<String, Object> arguments = node.getDeclareArgs();

        if(altExchange != null && !"".equals(altExchange))
        {
            arguments.put("alternateExchange", altExchange);
        }

        // can't set alt. exchange
        declareExchange(AMQShortString.valueOf(dest.getAddressName()),
                        AMQShortString.valueOf(node.getExchangeType()),
                        false,
                        node.isDurable(),
                        node.isAutoDelete(),
                        FieldTable.convertToFieldTable(arguments), false);

        // If bindings are specified without a queue name and is called by the producer,
        // the broker will send an exception as expected.
        createBindings(dest, dest.getNode().getBindings());
        sync();
    }


    protected void doBind(final AMQDestination dest,
                        final AMQDestination.Binding binding,
                        final String queue,
                        final String exchange) throws AMQException
    {
        final String bindingKey = binding.getBindingKey() == null ? queue : binding.getBindingKey();

        new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
        {
            public Object execute() throws AMQException, FailoverException
            {


                MethodRegistry methodRegistry = getProtocolHandler().getMethodRegistry();
                QueueBindBody queueBindBody =
                        methodRegistry.createQueueBindBody(getTicket(),
                                                           AMQShortString.valueOf(queue),
                                                           AMQShortString.valueOf(exchange),
                                                           AMQShortString.valueOf(bindingKey),
                                                           false,
                                                           FieldTable.convertToFieldTable(binding.getArgs()));

                getProtocolHandler().syncWrite(queueBindBody.
                generateFrame(getChannelId()), QueueBindOkBody.class);
                return null;
            }
        }, getAMQConnection()).execute();

    }


    protected void doUnbind(final AMQDestination.Binding binding,
                            final String queue,
                            final String exchange) throws AMQException
    {
        new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
        {
            public Object execute() throws AMQException, FailoverException
            {

                if (isBound(null, AMQShortString.valueOf(queue), null))
                {
                    MethodRegistry methodRegistry = getProtocolHandler().getMethodRegistry();
                    AMQMethodBody body;
                    if (methodRegistry instanceof MethodRegistry_0_9)
                    {
                        String bindingKey = binding.getBindingKey() == null ? queue : binding.getBindingKey();

                        MethodRegistry_0_9 methodRegistry_0_9 = (MethodRegistry_0_9) methodRegistry;
                        body = methodRegistry_0_9.createQueueUnbindBody(getTicket(),
                                                                        AMQShortString.valueOf(queue),
                                                                        AMQShortString.valueOf(exchange),
                                                                        AMQShortString.valueOf(bindingKey),
                                                                        null);
                    }
                    else if (methodRegistry instanceof MethodRegistry_0_91)
                    {
                        MethodRegistry_0_91 methodRegistry_0_91 = (MethodRegistry_0_91) methodRegistry;
                        body = methodRegistry_0_91.createQueueUnbindBody(getTicket(),
                                                                         AMQShortString.valueOf(queue),
                                                                         AMQShortString.valueOf(exchange),
                                                                         AMQShortString.valueOf(binding.getBindingKey()),
                                                                         null);

                    }
                    else
                    {
                        throw new AMQException(AMQConstant.NOT_IMPLEMENTED, "Cannot unbind a queue in AMQP 0-8");
                    }
                    getProtocolHandler().syncWrite(body.generateFrame(getChannelId()), QueueUnbindOkBody.class);
                    return null;
                }
                else
                {
                    return null;
                }
            }
        }, getAMQConnection()).execute();
    }

    public boolean isQueueExist(AMQDestination dest, boolean assertNode) throws AMQException
    {
        Node node = dest.getNode();
        return isQueueExist(dest.getAddressName(), assertNode,
                            node.isDurable(), node.isAutoDelete(),
                            node.isExclusive(), node.getDeclareArgs());
    }

    public boolean isQueueExist(final String queueName, boolean assertNode,
                                final boolean durable, final boolean autoDelete,
                                final boolean exclusive, final Map<String, Object> args) throws AMQException
    {
        boolean match = isBound(null,AMQShortString.valueOf(queueName), null);

        if (assertNode)
        {
            if(!match)
            {
                throw new AMQException("Assert failed for queue : " + queueName  +". Queue does not exist." );

            }
            else
            {

                new FailoverNoopSupport<Void, AMQException>(
                        new FailoverProtectedOperation<Void, AMQException>()
                        {
                            public Void execute() throws AMQException, FailoverException
                            {

                                sendQueueDeclare(AMQShortString.valueOf(queueName),
                                                 durable,
                                                 exclusive,
                                                 autoDelete,
                                                 FieldTable.convertToFieldTable(args),
                                                 true);

                                return null;
                            }
                        }, getAMQConnection());

            }
        }


        return match;
    }

    public boolean isExchangeExist(AMQDestination dest,boolean assertNode) throws AMQException
    {
        boolean match = exchangeExists(AMQShortString.valueOf(dest.getAddressName()));

        Node node = dest.getNode();

        if (match)
        {
            if (assertNode)
            {

                declareExchange(AMQShortString.valueOf(dest.getAddressName()),
                                AMQShortString.valueOf(node.getExchangeType()),
                                false,
                                node.isDurable(),
                                node.isAutoDelete(),
                                FieldTable.convertToFieldTable(node.getDeclareArgs()), true);

            }
            else
            {
                // TODO - some way to determine the exchange type
            /*
                _logger.debug("Setting Exchange type " + result.getType());
                node.setExchangeType(result.getType());
                dest.setExchangeClass(new AMQShortString(result.getType()));
             */

            }
        }

        if (assertNode)
        {
            if (!match)
            {
                throw new AMQException("Assert failed for address : " + dest  +". Exchange not found.");
            }
        }

        return match;
    }

    @Override
    void handleNodeDelete(final AMQDestination dest) throws AMQException
    {
        if (AMQDestination.TOPIC_TYPE == dest.getAddressType())
        {
            if (isExchangeExist(dest,false))
            {

                new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
                {
                    public Object execute() throws AMQException, FailoverException
                    {
                        sendExchangeDelete(dest.getAddressName());
                        return null;
                    }
                }, getAMQConnection()).execute();
                dest.setAddressResolved(0);
            }
        }
        else
        {
            if (isQueueExist(dest,false))
            {

                new FailoverNoopSupport<Object, AMQException>(new FailoverProtectedOperation<Object, AMQException>()
                {
                    public Object execute() throws AMQException, FailoverException
                    {
                        sendQueueDelete(AMQShortString.valueOf(dest.getAddressName()));
                        return null;
                    }
                }, getAMQConnection()).execute();
                dest.setAddressResolved(0);
            }
        }
    }

    @Override
    void handleLinkDelete(AMQDestination dest) throws AMQException
    {
        // We need to destroy link bindings
        String defaultExchangeForBinding = dest.getAddressType() == AMQDestination.TOPIC_TYPE ? dest
                .getAddressName() : "amq.topic";

        String defaultQueueName = null;
        if (AMQDestination.QUEUE_TYPE == dest.getAddressType())
        {
            defaultQueueName = dest.getQueueName();
        }
        else
        {
            defaultQueueName = dest.getLink().getName() != null ? dest.getLink().getName() : dest.getQueueName();
        }

        for (AMQDestination.Binding binding: dest.getLink().getBindings())
        {
            String queue = binding.getQueue() == null?
                    defaultQueueName: binding.getQueue();

            String exchange = binding.getExchange() == null ?
                    defaultExchangeForBinding :
                    binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Unbinding queue : " + queue +
                              " exchange: " + exchange +
                              " using binding key " + binding.getBindingKey() +
                              " with args " + Strings.printMap(binding.getArgs()));
            }
            doUnbind(binding, queue, exchange);
        }
    }


    void deleteSubscriptionQueue(final AMQDestination dest) throws AMQException
    {
        // We need to delete the subscription queue.
        if (dest.getAddressType() == AMQDestination.TOPIC_TYPE &&
            dest.getLink().getSubscriptionQueue().isExclusive() &&
            isQueueExist(dest.getQueueName(), false, false, false, false, null))
        {
            (new FailoverNoopSupport<Void, AMQException>(
                    new FailoverProtectedOperation<Void, AMQException>()
                    {
                        public Void execute() throws AMQException, FailoverException
                        {

                            sendQueueDelete(AMQShortString.valueOf(dest.getQueueName()));
                            return null;
                        }
                    }, getAMQConnection())).execute();

        }
    }

    protected void flushAcknowledgments()
    {

    }

    @Override
    protected void deleteTemporaryDestination(final TemporaryDestination amqQueue)
            throws JMSException
    {
        // Currently TemporaryDestination is set to be auto-delete which, for 0-8..0-9-1, means that the queue will be deleted
        // by the server when there are no more subscriptions to that queue/topic (rather than when the client disconnects).
        // This is not quite right for JMSCompliance as the queue/topic should remain until the connection closes, or the
        // client explicitly deletes it.

        /* intentional no-op */
    }

    public boolean isQueueBound(String exchangeName, String queueName,
            String bindingKey, Map<String, Object> args) throws JMSException
    {
        return isQueueBound(exchangeName == null ? null : new AMQShortString(exchangeName),
                            queueName == null ? null : new AMQShortString(queueName),
                            bindingKey == null ? null : new AMQShortString(bindingKey));
    }

    private AMQProtocolHandler getProtocolHandler()
    {
        return getAMQConnection().getProtocolHandler();
    }

    public MethodRegistry getMethodRegistry()
    {
        MethodRegistry methodRegistry = getProtocolHandler().getMethodRegistry();
        return methodRegistry;
    }

    public AMQException getLastException()
    {
        // if the Connection has closed then we should throw any exception that
        // has occurred that we were not waiting for
        AMQStateManager manager = getProtocolHandler().getStateManager();

        Exception e = manager.getLastException();
        if (manager.getCurrentState().equals(AMQState.CONNECTION_CLOSED)
                && e != null)
        {
            if (e instanceof AMQException)
            {
                return (AMQException) e;
            }
            else
            {
                AMQException amqe = new AMQException(AMQConstant
                        .getConstant(AMQConstant.INTERNAL_ERROR.getCode()),
                        e.getMessage(), e.getCause());
                return amqe;
            }
        }
        else
        {
            return null;
        }
    }

    public boolean isFlowBlocked()
    {
        synchronized (_flowControl)
        {
            return !_flowControl.getFlowControl();
        }
    }

    public void setFlowControl(final boolean active)
    {
        _flowControl.setFlowControl(active);
        if (_logger.isInfoEnabled())
        {
            _logger.info("Broker enforced flow control " + (active ? "no longer in effect" : "has been enforced"));
        }
    }

    void checkFlowControl() throws InterruptedException, JMSException
    {
        long expiryTime = 0L;
        synchronized (_flowControl)
        {
            while (!_flowControl.getFlowControl() &&
                   (expiryTime == 0L ? (expiryTime = System.currentTimeMillis() + _flowControlWaitFailure)
                                     : expiryTime) >= System.currentTimeMillis() )
            {

                _flowControl.wait(_flowControlWaitPeriod);
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Message send delayed by " + (System.currentTimeMillis() + _flowControlWaitFailure - expiryTime)/1000 + "s due to broker enforced flow control");
                }
            }
            if(!_flowControl.getFlowControl())
            {
                _logger.error("Message send failed due to timeout waiting on broker enforced flow control");
                throw new JMSException("Unable to send message for " + _flowControlWaitFailure /1000 + " seconds due to broker enforced flow control");
            }
        }
    }



    public abstract static class DestinationCache<T extends AMQDestination>
    {
        private final Map<AMQShortString, Map<AMQShortString, T>> cache = new HashMap<AMQShortString, Map<AMQShortString, T>>();

        public T getDestination(AMQShortString exchangeName, AMQShortString routingKey)
        {
            Map<AMQShortString, T> routingMap = cache.get(exchangeName);
            if(routingMap == null)
            {
                routingMap = new LinkedHashMap<AMQShortString, T>()
                {

                    protected boolean removeEldestEntry(Map.Entry<AMQShortString, T> eldest)
                    {
                        return size() >= 200;
                    }
                };
                cache.put(exchangeName,routingMap);
            }
            T destination = routingMap.get(routingKey);
            if(destination == null)
            {
                destination = newDestination(exchangeName, routingKey);
                routingMap.put(routingKey,destination);
            }
            return destination;
        }

        protected abstract T newDestination(AMQShortString exchangeName, AMQShortString routingKey);
    }

    private static class TopicDestinationCache extends DestinationCache<AMQTopic>
    {
        protected AMQTopic newDestination(AMQShortString exchangeName, AMQShortString routingKey)
        {
            return new AMQTopic(exchangeName, routingKey, null);
        }
    }

    private static class QueueDestinationCache extends DestinationCache<AMQQueue>
    {
        protected AMQQueue newDestination(AMQShortString exchangeName, AMQShortString routingKey)
        {
            return new AMQQueue(exchangeName, routingKey, routingKey);
        }
    }

    private static final class FlowControlIndicator
    {
        private volatile boolean _flowControl = true;

        public synchronized void setFlowControl(boolean flowControl)
        {
            _flowControl = flowControl;
            notify();
        }

        public boolean getFlowControl()
        {
            return _flowControl;
        }
    }

    private final TopicDestinationCache _topicDestinationCache = new TopicDestinationCache();
    private final QueueDestinationCache _queueDestinationCache = new QueueDestinationCache();

}
