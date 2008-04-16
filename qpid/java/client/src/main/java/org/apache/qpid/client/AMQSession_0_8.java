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


import javax.jms.*;
import javax.jms.IllegalStateException;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.failover.FailoverRetrySupport;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.framing.*;
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
            BasicAckBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), deliveryTag,
                                        multiple);

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on channel " + _channelId);
        }

        getProtocolHandler().writeFrame(ackFrame);
    }

    public void sendQueueBind(final AMQShortString queueName, final AMQShortString routingKey, final FieldTable arguments,
            final AMQShortString exchangeName, final AMQDestination dest) throws AMQException, FailoverException
    {
        AMQFrame queueBind = QueueBindBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), arguments, // arguments
                exchangeName, // exchange
                false, // nowait
                queueName, // queue
                routingKey, // routingKey
                getTicket()); // ticket

        getProtocolHandler().syncWrite(queueBind, QueueBindOkBody.class);
    }

    public void sendClose(long timeout) throws AMQException, FailoverException
    {
        getProtocolHandler().closeSession(this);

        final AMQFrame frame = ChannelCloseBody.createAMQFrame
            (getChannelId(), getProtocolMajorVersion(), getProtocolMinorVersion(),
             0, // classId
             0, // methodId
             AMQConstant.REPLY_SUCCESS.getCode(), // replyCode
             new AMQShortString("JMS client closing channel")); // replyText

        getProtocolHandler().syncWrite(frame, ChannelCloseOkBody.class, timeout);
        // When control resumes at this point, a reply will have been received that
        // indicates the broker has closed the channel successfully.
    }

    public void sendCommit() throws AMQException, FailoverException
    {
        final AMQProtocolHandler handler = getProtocolHandler();

        handler.syncWrite(TxCommitBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion()), TxCommitOkBody.class);
    }

    public void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable, final boolean exclusive) throws AMQException,
            FailoverException
    {
        AMQFrame queueDeclare = QueueDeclareBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), null, // arguments
                autoDelete, // autoDelete
                durable, // durable
                exclusive, // exclusive
                false, // nowait
                false, // passive
                name, // queue
                getTicket()); // ticket

        getProtocolHandler().syncWrite(queueDeclare, QueueDeclareOkBody.class);
    }

    public void sendRecover() throws AMQException, FailoverException
    {
        _unacknowledgedMessageTags.clear();

        if (isStrictAMQP())
        {
            // We can't use the BasicRecoverBody-OK method as it isn't part of the spec.
            _connection.getProtocolHandler().writeFrame(
                    BasicRecoverBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), false)); // requeue
            _logger.warn("Session Recover cannot be guaranteed with STRICT_AMQP. Messages may arrive out of order.");
        }
        else
        {

            _connection.getProtocolHandler().syncWrite(
                    BasicRecoverBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), false) // requeue
                    , BasicRecoverOkBody.class);
        }
    }

    public void releaseForRollback()
    {
        while (true)
        {
            Long tag = _deliveredMessageTags.poll();
            if (tag == null)
            {
                break;
            }

            rejectMessage(tag, true);
        }

        if (_dispatcher != null)
        {
            _dispatcher.rollback();
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

            AMQFrame basicRejectBody = BasicRejectBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), deliveryTag,
                    requeue);

            _connection.getProtocolHandler().writeFrame(basicRejectBody);
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
                            AMQFrame boundFrame = ExchangeBoundBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(),
                                    exchangeName, // exchange
                                    queueName, // queue
                                    routingKey); // routingKey

                            return getProtocolHandler().syncWrite(boundFrame, ExchangeBoundOkBody.class);

                        }
                    }, _connection).execute();

            // Extract and return the response code from the query.
            ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();

            return (responseBody.replyCode == 0);
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
        AMQFrame jmsConsume = BasicConsumeBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), arguments, // arguments
                tag, // consumerTag
                consumer.isExclusive(), // exclusive
                consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE, // noAck
                consumer.isNoLocal(), // noLocal
                nowait, // nowait
                queueName, // queue
                getTicket()); // ticket

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
        AMQFrame exchangeDeclare = ExchangeDeclareBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), null, // arguments
                false, // autoDelete
                false, // durable
                name, // exchange
                false, // internal
                nowait, // nowait
                false, // passive
                getTicket(), // ticket
                type); // type

        protocolHandler.syncWrite(exchangeDeclare, ExchangeDeclareOkBody.class);
    }

    public void sendQueueDeclare(final AMQDestination amqd, final AMQProtocolHandler protocolHandler) throws AMQException, FailoverException
    {
        AMQFrame queueDeclare = QueueDeclareBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), null, // arguments
                amqd.isAutoDelete(), // autoDelete
                amqd.isDurable(), // durable
                amqd.isExclusive(), // exclusive
                false, // nowait
                false, // passive
                amqd.getAMQQueueName(), // queue
                getTicket()); // ticket

        protocolHandler.syncWrite(queueDeclare, QueueDeclareOkBody.class);
    }

    public void sendQueueDelete(final AMQShortString queueName) throws AMQException, FailoverException
    {
        AMQFrame queueDeleteFrame = QueueDeleteBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), false, // ifEmpty
                false, // ifUnused
                true, // nowait
                queueName, // queue
                getTicket()); // ticket

        getProtocolHandler().syncWrite(queueDeleteFrame, QueueDeleteOkBody.class);
    }

    public void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException
    {
        AMQFrame channelFlowFrame = ChannelFlowBody.createAMQFrame(_channelId, getProtocolMajorVersion(), getProtocolMinorVersion(), !suspend);

        _connection.getProtocolHandler().syncWrite(channelFlowFrame, ChannelFlowOkBody.class);
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
        _connection.getProtocolHandler().syncWrite(TxRollbackBody.createAMQFrame(_channelId,
            getProtocolMajorVersion(), getProtocolMinorVersion()), TxRollbackOkBody.class);
    }

     public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkNotClosed();

        return new AMQTemporaryQueue(this);
    }

    public  TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
       {

           checkNotClosed();
           AMQTopic origTopic = checkValidTopic(topic);
           AMQTopic dest = AMQTopic.createDurableTopic(origTopic, name, _connection);
           TopicSubscriberAdaptor subscriber = _subscriptions.get(name);
           if (subscriber != null)
           {
               if (subscriber.getTopic().equals(topic))
               {
                   throw new IllegalStateException("Already subscribed to topic " + topic + " with subscription exchange "
                                                   + name);
               }
               else
               {
                   unsubscribe(name);
               }
           }
           else
           {
               AMQShortString topicName;
               if (topic instanceof AMQTopic)
               {
                   topicName = ((AMQTopic) topic).getRoutingKey();
               }
               else
               {
                   topicName = new AMQShortString(topic.getTopicName());
               }

               if (_strictAMQP)
               {
                   if (_strictAMQPFATAL)
                   {
                       throw new UnsupportedOperationException("JMS Durable not currently supported by AMQP.");
                   }
                   else
                   {
                       _logger.warn("Unable to determine if subscription already exists for '" + topicName + "' "
                                    + "for creation durableSubscriber. Requesting queue deletion regardless.");
                   }

                   deleteQueue(dest.getAMQQueueName());
               }
               else
               {
                   // if the queue is bound to the exchange but NOT for this topic, then the JMS spec
                   // says we must trash the subscription.
                   if (isQueueBound(dest.getExchangeName(), dest.getAMQQueueName())
                       && !isQueueBound(dest.getExchangeName(), dest.getAMQQueueName(), topicName))
                   {
                       deleteQueue(dest.getAMQQueueName());
                   }
               }
           }

           subscriber = new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest));

           _subscriptions.put(name, subscriber);
           _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);

           return subscriber;
       }

}
