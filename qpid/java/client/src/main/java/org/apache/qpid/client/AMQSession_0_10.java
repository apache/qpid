/* Licensed to the Apache Software Foundation (ASF) under one
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
 */
package org.apache.qpid.client;

import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverNoopSupport;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.MessagePartListenerAdapter;
import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.transport.RangeSet;
import org.apache.qpidity.transport.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Destination;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is a 0.10 Session
 */
public class AMQSession_0_10 extends AMQSession
{

    /**
     * This class logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(AMQSession_0_10.class);

    /**
     * The maximum number of pre-fetched messages per destination
     */
    private static final long MAX_PREFETCH = 100;

    /**
     * The underlying QpidSession
     */
    private Session _qpidSession;

    /**
     * The latest qpid Exception that has been reaised.
     */
    private QpidException _currentException;

    /**
     * All the not yet acknoledged message tags
     */
    private ConcurrentLinkedQueue<Long> _unacknowledgedMessageTags = new ConcurrentLinkedQueue<Long>();

    //--- constructors

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
     * @param qpidConnection          The qpid connection
     */
    AMQSession_0_10(org.apache.qpidity.nclient.Connection qpidConnection, AMQConnection con, int channelId,
                    boolean transacted, int acknowledgeMode, MessageFactoryRegistry messageFactoryRegistry,
                    int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {

        super(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, defaultPrefetchHighMark,
              defaultPrefetchLowMark);
        // create the qpid session with an expiry  <= 0 so that the session does not expire
        _qpidSession = qpidConnection.createSession(0);
        // set the exception listnere for this session
        _qpidSession.setClosedListener(new QpidSessionExceptionListener());
        // set transacted if required
        if (_transacted)
        {
            _qpidSession.txSelect();
        }
    }

    /**
     * Creates a new session on a connection with the default 0-10 message factory.
     *
     * @param con                 The connection on which to create the session.
     * @param channelId           The unique identifier for the session.
     * @param transacted          Indicates whether or not the session is transactional.
     * @param acknowledgeMode     The acknoledgement mode for the session.
     * @param defaultPrefetchHigh The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLow  The number of prefetched messages at which to resume the session.
     * @param qpidConnection      The connection
     */
    AMQSession_0_10(org.apache.qpidity.nclient.Connection qpidConnection, AMQConnection con, int channelId,
                    boolean transacted, int acknowledgeMode, int defaultPrefetchHigh, int defaultPrefetchLow)
    {

        this(qpidConnection, con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(),
             defaultPrefetchHigh, defaultPrefetchLow);
    }

    //------- 0-10 specific methods

    /**
     * Add a message tag to be acknowledged
     * This is used for client ack mode
     *
     * @param tag The id of the message to be acknowledged
     */
    void addMessageTag(long tag)
    {
        _unacknowledgedMessageTags.add(tag);
    }

    //------- overwritten methods of class AMQSession

    /**
     * Acknowledge one or many messages.
     *
     * @param deliveryTag The tag of the last message to be acknowledged.
     * @param multiple    <tt>true</tt> to acknowledge all messages up to and including the one specified by the
     *                    delivery tag, <tt>false</tt> to just acknowledge that message.
     */
    public void acknowledgeMessage(long deliveryTag, boolean multiple)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on session " + _channelId);
        }
        // acknowledge this message
        RangeSet ranges = new RangeSet();
        if (multiple)
        {
            for (Long messageTag : _unacknowledgedMessageTags)
            {
                ranges.add(messageTag);
            }
            //empty the list of unack messages
            _unacknowledgedMessageTags.clear();
        }
        else
        {
            ranges.add(deliveryTag);
            _unacknowledgedMessageTags.remove(deliveryTag);
        }
        getQpidSession().messageAcknowledge(ranges);
    }

    /**
     * Bind a queue with an exchange.
     *
     * @param queueName    Specifies the name of the queue to bind. If the queue name is empty,
     *                     refers to the current
     *                     queue for the session, which is the last declared queue.
     * @param exchangeName The exchange name.
     * @param routingKey   Specifies the routing key for the binding.
     * @param arguments    0_8 specific
     */
    public void sendQueueBind(final AMQShortString queueName, final AMQShortString routingKey,
                              final FieldTable arguments, final AMQShortString exchangeName)
            throws AMQException, FailoverException
    {
        getQpidSession().queueBind(queueName.toString(), exchangeName.toString(), routingKey.toString(), null);
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }


    /**
     * Close this session.
     *
     * @param timeout no used / 0_8 specific
     * @throws AMQException
     * @throws FailoverException
     */
    public void sendClose(long timeout) throws AMQException, FailoverException
    {
        getQpidSession().sessionClose();
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Commit the receipt and the delivery of all messages exchanged by this session resources.
     */
    public void sendCommit() throws AMQException, FailoverException
    {
        getQpidSession().txCommit();
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Create a queue with a given name.
     *
     * @param name       The queue name
     * @param autoDelete If this field is set and the exclusive field is also set,
     *                   then the queue is deleted when the connection closes.
     * @param durable    If set when creating a new queue,
     *                   the queue will be marked as durable.
     * @param exclusive  Exclusive queues can only be used from one connection at a time.
     * @throws AMQException
     * @throws FailoverException
     */
    public void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable,
                                final boolean exclusive) throws AMQException, FailoverException
    {
        getQpidSession().queueDeclare(name.toString(), null, null, durable ? Option.DURABLE : Option.NO_OPTION,
                                      autoDelete ? Option.AUTO_DELETE : Option.NO_OPTION,
                                      exclusive ? Option.EXCLUSIVE : Option.NO_OPTION);
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * This method asks the broker to redeliver all unacknowledged messages
     *
     * @throws AMQException
     * @throws FailoverException
     */
    public void sendRecover() throws AMQException, FailoverException
    {
        // release all unack messages
        RangeSet ranges = new RangeSet();
        for (long messageTag : _unacknowledgedMessageTags)
        {
            // release this message
            ranges.add(messageTag);
        }
        getQpidSession().messageRelease(ranges);
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Release (0_8 notion of Reject) an acquired message
     *
     * @param deliveryTag the message ID
     * @param requeue     always true
     */
    public void rejectMessage(long deliveryTag, boolean requeue)
    {
        // The value of requeue is always true
        RangeSet ranges = new RangeSet();
        ranges.add(deliveryTag);
        getQpidSession().messageRelease(ranges);
        //I don't think we need to sync
    }

    /**
     * Create an 0_10 message consumer
     */
    public BasicMessageConsumer createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
                                                      final int prefetchLow, final boolean noLocal,
                                                      final boolean exclusive, String messageSelector,
                                                      final FieldTable ft, final boolean noConsume,
                                                      final boolean autoClose) throws JMSException
    {

        final AMQProtocolHandler protocolHandler = getProtocolHandler();
        return new BasicMessageConsumer_0_10(_channelId, _connection, destination, messageSelector, noLocal,
                                             _messageFactoryRegistry, this, protocolHandler, ft, prefetchHigh,
                                             prefetchLow, exclusive, _acknowledgeMode, noConsume, autoClose);
    }

    /**
     * Bind a queue with an exchange.
     */
    public boolean isQueueBound(final AMQShortString exchangeName, final AMQShortString queueName,
                                final AMQShortString routingKey) throws JMSException
    {
        getQpidSession().queueBind(queueName.toString(), exchangeName.toString(), routingKey.toString(), null);
        // we asume that a binding is always successful
        return true;
    }

    /**
     * This method is invoked when a consumer is creted
     * Registers the consumer with the broker
     */
    public void sendConsume(BasicMessageConsumer consumer, AMQShortString queueName, AMQProtocolHandler protocolHandler,
                            boolean nowait, String messageSelector, AMQShortString tag)
            throws AMQException, FailoverException
    {
        boolean preAcquire;
        try
        {
            preAcquire = consumer.getMessageSelector() == null || !(consumer.getDestination() instanceof AMQQueue);
        }
        catch (JMSException e)
        {
           throw new AMQException(AMQConstant.INTERNAL_ERROR, "problem when registering consumer", e);
        }
        getQpidSession().messageSubscribe(queueName.toString(), tag.toString(), Session.TRANSFER_CONFIRM_MODE_REQUIRED,
                                          preAcquire ? Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE :
                                                  Session.TRANSFER_ACQUIRE_MODE_NO_ACQUIRE,
                                          new MessagePartListenerAdapter((BasicMessageConsumer_0_10) consumer), null,
                                          consumer.isNoLocal() ? Option.NO_LOCAL : Option.NO_OPTION,
                                          consumer.isExclusive() ? Option.EXCLUSIVE : Option.NO_OPTION);
        getQpidSession().messageFlowMode(tag.toString(), Session.MESSAGE_FLOW_MODE_WINDOW);
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Create an 0_10 message producer
     */
    public BasicMessageProducer createMessageProducer(final Destination destination, final boolean mandatory,
                                                      final boolean immediate, final boolean waitUntilSent,
                                                      long producerId)
    {
        return new BasicMessageProducer_0_10(_connection, (AMQDestination) destination, _transacted, _channelId, this,
                                             getProtocolHandler(), producerId, immediate, mandatory, waitUntilSent);

    }

    /**
     * creates an exchange if it does not already exist
     */
    public void sendExchangeDeclare(final AMQShortString name, final AMQShortString type,
                                    final AMQProtocolHandler protocolHandler, final boolean nowait)
            throws AMQException, FailoverException
    {
        getQpidSession().exchangeDeclare(name.toString(), type.toString(), null, null);
        // autoDelete --> false
        // durable --> false
        // passive -- false
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Declare a queue with the given queueName
     */
    public void sendQueueDeclare(final AMQDestination amqd, final AMQProtocolHandler protocolHandler)
            throws AMQException, FailoverException
    {
        getQpidSession().queueDeclare(amqd.getAMQQueueName().toString(), null, null,
                                      amqd.isAutoDelete() ? Option.AUTO_DELETE : Option.NO_OPTION,
                                      amqd.isDurable() ? Option.DURABLE : Option.NO_OPTION,
                                      amqd.isExclusive() ? Option.EXCLUSIVE : Option.NO_OPTION);
        // passive --> false
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * deletes a queue
     */
    public void sendQueueDelete(final AMQShortString queueName) throws AMQException, FailoverException
    {
        getQpidSession().queueDelete(queueName.toString());
        // ifEmpty --> false
        // ifUnused --> false
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }

    /**
     * Activate/deactivate the message flow for all the consumers of this session.
     */
    public void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException
    {
        if (suspend)
        {
            for (BasicMessageConsumer consumer : _consumers.values())
            {
                getQpidSession().messageStop(consumer.getConsumerTag().toString());
            }
        }
        else
        {
            for (BasicMessageConsumer consumer : _consumers.values())
            {
                getQpidSession().messageFlow(consumer.getConsumerTag().toString(), Session.MESSAGE_FLOW_UNIT_MESSAGE,
                                             MAX_PREFETCH);
                // todo this 
                getQpidSession().messageFlow(consumer.getConsumerTag().toString(), Session.MESSAGE_FLOW_UNIT_BYTE,
                                             0xFFFFFFFF);
            }
        }
        // We need to sync so that we get notify of an error.
        getQpidSession().sync();
        getCurrentException();
    }


    //------ Private methods
    /**
     * Access to the underlying Qpid Session
     *
     * @return The associated Qpid Session.
     */
    protected org.apache.qpidity.nclient.Session getQpidSession()
    {
        return _qpidSession;
    }


    /**
     * Get the latest thrown exception.
     *
     * @throws org.apache.qpid.AMQException get the latest thrown error.
     */
    public synchronized void getCurrentException() throws AMQException
    {
        if (_currentException != null)
        {
            QpidException toBeTrhown = _currentException;
            _currentException = null;
            throw new AMQException(AMQConstant.getConstant(toBeTrhown.getErrorCode().getCode()),
                                   toBeTrhown.getMessage(), toBeTrhown);
        }
    }

    //------ Inner classes
    /**
     * Lstener for qpid protocol exceptions
     */
    private class QpidSessionExceptionListener implements org.apache.qpidity.nclient.ClosedListener
    {
        public void onClosed(ErrorCode errorCode, String reason)
        {
            synchronized (this)
            {
                //todo check the error code for finding out if we need to notify the
                // JMS connection exception listener
                _currentException = new QpidException(reason,errorCode,null);
            }
        }
    }

    protected AMQShortString declareQueue(final AMQDestination amqd, final AMQProtocolHandler protocolHandler)
              throws AMQException
      {
          /*return new FailoverRetrySupport<AMQShortString, AMQException>(*/
          return new FailoverNoopSupport<AMQShortString, AMQException>(
                  new FailoverProtectedOperation<AMQShortString, AMQException>()
                  {
                      public AMQShortString execute() throws AMQException, FailoverException
                      {
                          // Generate the queue name if the destination indicates that a client generated name is to be used.
                          if (amqd.isNameRequired())
                          {

                                   //TODO this is for 0_10 only to be changed
                                   amqd.setQueueName(new AMQShortString("tmp_" +System.currentTimeMillis()));

                          }

                          sendQueueDeclare(amqd,protocolHandler);

                          return amqd.getAMQQueueName();
                      }
                  }, _connection).execute();
      }


      void start() throws AMQException
    {

           super.suspendChannel(false);

        // If the event dispatcher is not running then start it too.
        if (hasMessageListeners())
        {
            startDistpatcherIfNecessary();
        }
    }
}
