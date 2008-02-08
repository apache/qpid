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


import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.TemporaryQueue;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.*;
import org.apache.qpid.framing.amqp_0_9.MethodRegistry_0_9;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQSession_0_8 extends AMQSession
{

    /** Used for debugging. */
    private static final Logger _logger = LoggerFactory.getLogger(AMQSession.class);

    /**
     * Creates a new session on a connection.
     *
     * @param con                     The connection on which to create the session.
     * @param channelId               The unique identifier for the session.
     * @param transacted              Indicates whether or not the session is transactional.
     * @param acknowledgeMode         The acknoledgement mode for the session.
     * @param messageFactoryRegistry  The message factory factory for the session.
     * @param defaultPrefetchHighMark The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLowMark  The number of prefetched messages at which to resume the session.
     */
    AMQSession_0_8(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
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
     * @param acknowledgeMode         The acknoledgement mode for the session.
     * @param defaultPrefetchHigh     The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLow      The number of prefetched messages at which to resume the session.
     */
    AMQSession_0_8(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetchHigh,
               int defaultPrefetchLow)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetchHigh,
             defaultPrefetchLow);
    }

    public void acknowledgeMessage(long deliveryTag, boolean multiple)
    {
        final AMQFrame ackFrame =
            getProtocolHandler().getMethodRegistry().createBasicAckBody(deliveryTag, multiple).generateFrame(_channelId);

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on channel " + _channelId);
        }

        getProtocolHandler().writeFrame(ackFrame);
    }

    public void sendQueueBind(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
            final AMQShortString exchangeName) throws AMQException, FailoverException
    {
        getProtocolHandler().syncWrite(getProtocolHandler().getMethodRegistry().createQueueBindBody
                                        (getTicket(),queueName,exchangeName,routingKey,false,arguments).
                                        generateFrame(_channelId), QueueBindOkBody.class);
    }

    public void sendClose(long timeout) throws AMQException, FailoverException
    {
        getProtocolHandler().closeSession(this);
        getProtocolHandler().syncWrite(getProtocolHandler().getMethodRegistry().createChannelCloseBody(AMQConstant.REPLY_SUCCESS.getCode(),
                new AMQShortString("JMS client closing channel"), 0, 0).generateFrame(_channelId), 
                                       ChannelCloseOkBody.class, timeout);
        // When control resumes at this point, a reply will have been received that
        // indicates the broker has closed the channel successfully.
    }

    public void sendCommit() throws AMQException, FailoverException
    {
        final AMQProtocolHandler handler = getProtocolHandler();

        handler.syncWrite(getProtocolHandler().getMethodRegistry().createTxCommitOkBody().generateFrame(_channelId), TxCommitOkBody.class);
    }

    public void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable, final boolean exclusive) throws AMQException,
            FailoverException
    {
        AMQFrame queueDeclare = getProtocolHandler().getMethodRegistry().createQueueDeclareBody(getTicket(),name,false,durable,exclusive,autoDelete,false,null).generateFrame(_channelId); 
        getProtocolHandler().syncWrite(queueDeclare, QueueDeclareOkBody.class);
    }

    public void sendRecover() throws AMQException, FailoverException
    {
        if (isStrictAMQP())
        {
            // We can't use the BasicRecoverBody-OK method as it isn't part of the spec.

            BasicRecoverBody body = getMethodRegistry().createBasicRecoverBody(false);
            _connection.getProtocolHandler().writeFrame(body.generateFrame(_channelId));
            _logger.warn("Session Recover cannot be guaranteed with STRICT_AMQP. Messages may arrive out of order.");
        }
        else
        {
            // in Qpid the 0-8 spec was hacked to have a recover-ok method... this is bad
            // in 0-9 we used the cleaner addition of a new sync recover method with its own ok
            if(getProtocolHandler().getProtocolVersion().equals(ProtocolVersion.v8_0))
            {
                BasicRecoverBody body = getMethodRegistry().createBasicRecoverBody(false);
                _connection.getProtocolHandler().syncWrite(body.generateFrame(_channelId), BasicRecoverOkBody.class);
            }
            else if(getProtocolHandler().getProtocolVersion().equals(ProtocolVersion.v0_9))
            {
                BasicRecoverSyncBody body = ((MethodRegistry_0_9)getMethodRegistry()).createBasicRecoverSyncBody(false);
                _connection.getProtocolHandler().syncWrite(body.generateFrame(_channelId), BasicRecoverSyncOkBody.class);
            }
            else
            {
                throw new RuntimeException("Unsupported version of the AMQP Protocol: " + getProtocolHandler().getProtocolVersion());
            }
        }
    }

    public void rejectMessage(long deliveryTag, boolean requeue)
    {
        if ((_acknowledgeMode == CLIENT_ACKNOWLEDGE) || (_acknowledgeMode == SESSION_TRANSACTED))
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rejecting delivery tag:" + deliveryTag);
            }

            AMQFrame basicRejectBody = getMethodRegistry().createBasicRejectBody(deliveryTag, requeue).generateFrame(_channelId);

            _connection.getProtocolHandler().writeFrame(basicRejectBody);
        }
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
                            AMQFrame boundFrame = getMethodRegistry().createExchangeBoundBody
                                                    (exchangeName, routingKey, queueName).generateFrame(_channelId);

                            return getProtocolHandler().syncWrite(boundFrame, ExchangeBoundOkBody.class);

                        }
                    }, _connection).execute();

            // Extract and return the response code from the query.
            ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();

            return (responseBody.getReplyCode() == 0);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Queue bound query failed: " + e.getMessage(), e);
        }
    }

    public void sendConsume(BasicMessageConsumer consumer, AMQShortString queueName, AMQProtocolHandler protocolHandler, boolean nowait,
            String messageSelector, AMQShortString tag) throws AMQException, FailoverException
    {

        FieldTable arguments = FieldTableFactory.newFieldTable();
        if ((messageSelector != null) && !messageSelector.equals(""))
        {
            arguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue(), messageSelector);
        }

        if (consumer.isAutoClose())
        {
            arguments.put(AMQPFilterTypes.AUTO_CLOSE.getValue(), Boolean.TRUE);
        }

        if (consumer.isNoConsume())
        {
            arguments.put(AMQPFilterTypes.NO_CONSUME.getValue(), Boolean.TRUE);
        }

        consumer.setConsumerTag(tag);
        // we must register the consumer in the map before we actually start listening
        _consumers.put(tag, consumer);
        // TODO: Be aware of possible changes to parameter order as versions change.
        AMQFrame jmsConsume = getMethodRegistry().createBasicConsumeBody(getTicket(),
                queueName,
                tag,
                consumer.isNoLocal(),
                consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE,
                consumer.isExclusive(),
                nowait,
                arguments).generateFrame(_channelId);

        if (nowait)
        {
            protocolHandler.writeFrame(jmsConsume);
        }
        else
        {
            protocolHandler.syncWrite(jmsConsume, BasicConsumeOkBody.class);
        }
    }

    public void sendExchangeDeclare(final AMQShortString name, final AMQShortString type, final AMQProtocolHandler protocolHandler,
            final boolean nowait) throws AMQException, FailoverException
    {
        AMQFrame exchangeDeclare = getMethodRegistry().createExchangeDeclareBody(getTicket(),name,type,false,false,false,false,nowait,null).
                                            generateFrame(_channelId);

        protocolHandler.syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
    }

    public void sendQueueDeclare(final AMQDestination amqd, final AMQProtocolHandler protocolHandler) throws AMQException, FailoverException
    {
        AMQFrame queueDeclare = getMethodRegistry().createQueueDeclareBody(getTicket(),amqd.getAMQQueueName(),false,amqd.isDurable(),amqd.isExclusive(),amqd.isAutoDelete(),false,null).generateFrame(_channelId);

        protocolHandler.syncWrite(queueDeclare, QueueDeclareOkBody.class);
    }

    public void sendQueueDelete(final AMQShortString queueName) throws AMQException, FailoverException
    {
        QueueDeleteBody body = getMethodRegistry().createQueueDeleteBody(getTicket(),
                queueName,
                false,
                false,
                true);
        AMQFrame queueDeleteFrame = body.generateFrame(_channelId);

        getProtocolHandler().syncWrite(queueDeleteFrame, QueueDeleteOkBody.class);
    }

    public void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException
    {
        _connection.getProtocolHandler().syncWrite(_connection.getProtocolHandler().getMethodRegistry().
                                                   createChannelFlowBody(!suspend).generateFrame(_channelId),
                                                   ChannelFlowOkBody.class);
    }

    public BasicMessageConsumer_0_8 createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
            final int prefetchLow, final boolean noLocal, final boolean exclusive, String messageSelector, final FieldTable ft,
            final boolean noConsume, final boolean autoClose)  throws JMSException
    {

        final AMQProtocolHandler protocolHandler = getProtocolHandler();
       return new BasicMessageConsumer_0_8(_channelId, _connection, destination, messageSelector, noLocal,
                                 _messageFactoryRegistry,this, protocolHandler, ft, prefetchHigh, prefetchLow,
                                 exclusive, _acknowledgeMode, noConsume, autoClose);
    }


    public BasicMessageProducer createMessageProducer(final Destination destination, final boolean mandatory,
            final boolean immediate, final boolean waitUntilSent, long producerId)
    {

       return new BasicMessageProducer_0_8(_connection, (AMQDestination) destination, _transacted, _channelId,
                                 this, getProtocolHandler(), producerId, immediate, mandatory, waitUntilSent);
    }

    public void sendRollback() throws AMQException, FailoverException
    {
        _connection.getProtocolHandler().syncWrite(getProtocolHandler().getMethodRegistry().createTxRollbackBody().generateFrame(_channelId), 
                                                    TxRollbackOkBody.class);
    }

     public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkNotClosed();

        return new AMQTemporaryQueue(this);
    }
}
