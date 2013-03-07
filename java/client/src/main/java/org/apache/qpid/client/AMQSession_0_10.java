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

import static org.apache.qpid.transport.Option.BATCH;
import static org.apache.qpid.transport.Option.NONE;
import static org.apache.qpid.transport.Option.SYNC;
import static org.apache.qpid.transport.Option.UNRELIABLE;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.jms.Destination;
import javax.jms.JMSException;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination.AddressOption;
import org.apache.qpid.client.AMQDestination.Binding;
import org.apache.qpid.client.AMQDestination.DestSyntax;
import org.apache.qpid.client.failover.FailoverException;
import org.apache.qpid.client.failover.FailoverNoopSupport;
import org.apache.qpid.client.failover.FailoverProtectedOperation;
import org.apache.qpid.client.message.AMQMessageDelegateFactory;
import org.apache.qpid.client.message.FieldTableSupport;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage_0_10;
import org.apache.qpid.client.messaging.address.AddressHelper;
import org.apache.qpid.client.messaging.address.Link;
import org.apache.qpid.client.messaging.address.Link.SubscriptionQueue;
import org.apache.qpid.client.messaging.address.Node;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.transport.Connection;
import org.apache.qpid.transport.ExchangeBoundResult;
import org.apache.qpid.transport.ExchangeQueryResult;
import org.apache.qpid.transport.ExecutionErrorCode;
import org.apache.qpid.transport.ExecutionException;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Option;
import org.apache.qpid.transport.QueueQueryResult;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.RangeSet;
import org.apache.qpid.transport.RangeSetFactory;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;
import org.apache.qpid.transport.TransportException;
import org.apache.qpid.util.Serial;
import org.apache.qpid.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a 0.10 Session
 */
public class AMQSession_0_10 extends AMQSession<BasicMessageConsumer_0_10, BasicMessageProducer_0_10>
    implements SessionListener
{

    /**
     * This class logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(AMQSession_0_10.class);

    private static Timer timer = new Timer("ack-flusher", true);

    private static class Flusher extends TimerTask
    {

        private WeakReference<AMQSession_0_10> session;
        public Flusher(AMQSession_0_10 session)
        {
            this.session = new WeakReference<AMQSession_0_10>(session);
        }

        public void run() {
            AMQSession_0_10 ssn = session.get();
            if (ssn == null)
            {
                cancel();
            }
            else
            {
                try
                {
                    ssn.flushAcknowledgments(true);
                }
                catch (Throwable t)
                {
                    _logger.error("error flushing acks", t);
                }
            }
        }
    }


    /**
     * The underlying QpidSession
     */
    private Session _qpidSession;

    /**
     * The latest qpid Exception that has been raised.
     */
    private Object _currentExceptionLock = new Object();
    private AMQException _currentException;

    // a ref on the qpid connection
    private org.apache.qpid.transport.Connection _qpidConnection;

    private long maxAckDelay = Long.getLong("qpid.session.max_ack_delay", 1000);
    private TimerTask flushTask = null;
    private RangeSet unacked = RangeSetFactory.createRangeSet();
    private int unackedCount = 0;    

    /**
     * Used to store the range of in tx messages
     */
    private final RangeSet _txRangeSet = RangeSetFactory.createRangeSet();
    private int _txSize = 0;
    private boolean _isHardError = Boolean.getBoolean("qpid.session.legacy_exception_behaviour");
    //--- constructors

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
     * @param qpidConnection          The qpid connection
     */
    AMQSession_0_10(org.apache.qpid.transport.Connection qpidConnection, AMQConnection con, int channelId,
                    boolean transacted, int acknowledgeMode, MessageFactoryRegistry messageFactoryRegistry,
                    int defaultPrefetchHighMark, int defaultPrefetchLowMark,String name)
    {

        super(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, defaultPrefetchHighMark,
              defaultPrefetchLowMark);
        _qpidConnection = qpidConnection;
        if (name == null)
        {
            _qpidSession = _qpidConnection.createSession(1);
        }
        else
        {
            _qpidSession = _qpidConnection.createSession(name,1);
        }
        _qpidSession.setSessionListener(this);
        if (isTransacted())
        {
            _qpidSession.txSelect();
            _qpidSession.setTransacted(true);
        }

        if (maxAckDelay > 0)
        {
            flushTask = new Flusher(this);
            timer.schedule(flushTask, new Date(), maxAckDelay);
        }
    }

    /**
     * Creates a new session on a connection with the default 0-10 message factory.
     *
     * @param con                 The connection on which to create the session.
     * @param channelId           The unique identifier for the session.
     * @param transacted          Indicates whether or not the session is transactional.
     * @param acknowledgeMode     The acknowledgement mode for the session.
     * @param defaultPrefetchHigh The maximum number of messages to prefetched before suspending the session.
     * @param defaultPrefetchLow  The number of prefetched messages at which to resume the session.
     * @param qpidConnection      The connection
     */
    AMQSession_0_10(org.apache.qpid.transport.Connection qpidConnection, AMQConnection con, int channelId,
                    boolean transacted, int acknowledgeMode, int defaultPrefetchHigh, int defaultPrefetchLow,
                    String name)
    {

        this(qpidConnection, con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(),
             defaultPrefetchHigh, defaultPrefetchLow,name);
    }

    private void addUnacked(int id)
    {
        synchronized (unacked)
        {
            unacked.add(id);
            unackedCount++;
        }
    }

    private void clearUnacked()
    {
        synchronized (unacked)
        {
            unacked.clear();
            unackedCount = 0;
        }
    }

    protected Connection getQpidConnection()
    {
        return _qpidConnection;
    }

    //------- overwritten methods of class AMQSession

    void failoverPrep()
    {
        super.failoverPrep();
        clearUnacked();
    }

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
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on session " + getChannelId());
        }
        // acknowledge this message
        if (multiple)
        {
            for (Long messageTag : getUnacknowledgedMessageTags())
            {
                if( messageTag <= deliveryTag )
                {
                    addUnacked(messageTag.intValue());
                    getUnacknowledgedMessageTags().remove(messageTag);
                }
            }
            //empty the list of unack messages

        }
        else
        {
            addUnacked((int) deliveryTag);
            getUnacknowledgedMessageTags().remove(deliveryTag);
        }

        long prefetch = getAMQConnection().getMaxPrefetch();

        if (unackedCount >= prefetch/2 || maxAckDelay <= 0 || getAcknowledgeMode() == javax.jms.Session.AUTO_ACKNOWLEDGE)
        {
            flushAcknowledgments();
        }
    }

    protected void flushAcknowledgments()
    {
        flushAcknowledgments(false);
    }
    
    void flushAcknowledgments(boolean setSyncBit)
    {
        synchronized (unacked)
        {
            if (unackedCount > 0)
            {
                messageAcknowledge
                    (unacked, getAcknowledgeMode() != org.apache.qpid.jms.Session.NO_ACKNOWLEDGE,setSyncBit);
                clearUnacked();
            }
        }
    }

    void messageAcknowledge(final RangeSet ranges, final boolean accept)
    {
        messageAcknowledge(ranges,accept,false);
    }
    
    void messageAcknowledge(final RangeSet ranges, final boolean accept, final boolean setSyncBit)
    {
        final Session ssn = getQpidSession();
        flushProcessed(ranges,accept);
        if (accept)
        {
            ssn.messageAccept(ranges, UNRELIABLE, setSyncBit ? SYNC : NONE);
        }
    }

    /**
     * Flush any outstanding commands. This causes session complete to be sent.
     * @param ranges the range of command ids.
     * @param batch true if batched.
     */
    void flushProcessed(final RangeSet ranges, final boolean batch)
    {
        final Session ssn = getQpidSession();
        for (final Range range : ranges)
        {
            ssn.processed(range);
        }
        ssn.flushProcessed(batch ? BATCH : NONE);
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
                              final FieldTable arguments, final AMQShortString exchangeName,
                              final AMQDestination destination, final boolean nowait)
            throws AMQException
    {
        if (destination.getDestSyntax() == DestSyntax.BURL)
        {
            Map args = FieldTableSupport.convertToMap(arguments);
    
            for (AMQShortString rk: destination.getBindingKeys())
            {
                _logger.debug("Binding queue : " + queueName.toString() + 
                              " exchange: " + exchangeName.toString() + 
                              " using binding key " + rk.asString());
                getQpidSession().exchangeBind(queueName.toString(), 
                                              exchangeName.toString(), 
                                              rk.toString(), 
                                              args);
            }
        }
        else
        {
            // Leaving this here to ensure the public method bindQueue in AMQSession.java works as expected.
            List<Binding> bindings = new ArrayList<Binding>();
            bindings.addAll(destination.getNode().getBindings());
            
            String defaultExchange = destination.getAddressType() == AMQDestination.TOPIC_TYPE ?
                                     destination.getAddressName(): "amq.topic";
            
            for (Binding binding: bindings)
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
                getQpidSession().exchangeBind(queue, 
                                              exchange,
                                              binding.getBindingKey(),
                                              binding.getArgs()); 
            }
        }
        
        if (!nowait)
        {
            // We need to sync so that we get notify of an error.
            sync();
        }
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
        cancelTimerTask();
        flushAcknowledgments();
        try
        {
	        getQpidSession().sync();
	        getQpidSession().close();
        }
        catch (SessionException se)
        {
            setCurrentException(se);
        }

        AMQException amqe = getCurrentException();
        if (amqe != null)
        {
            throw amqe;
        }
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
     * @param arguments  Exclusive queues can only be used from one connection at a time.
     * @throws AMQException
     * @throws FailoverException
     */
    public void sendCreateQueue(AMQShortString name, final boolean autoDelete, final boolean durable,
                                final boolean exclusive, Map<String, Object> arguments) throws AMQException, FailoverException
    {
        getQpidSession().queueDeclare(name.toString(), null, arguments, durable ? Option.DURABLE : Option.NONE,
                                      autoDelete ? Option.AUTO_DELETE : Option.NONE,
                                      exclusive ? Option.EXCLUSIVE : Option.NONE);
        // We need to sync so that we get notify of an error.
        sync();
    }

    /**
     * This method asks the broker to redeliver all unacknowledged messages
     *
     * @throws AMQException
     * @throws FailoverException
     */
    public void sendRecover() throws AMQException, FailoverException
    {
        // release all unacked messages
        RangeSet all = RangeSetFactory.createRangeSet();
        RangeSet delivered = gatherRangeSet(getUnacknowledgedMessageTags());
        RangeSet prefetched = gatherRangeSet(getPrefetchedMessageTags());
        for (Iterator<Range> deliveredIter = delivered.iterator(); deliveredIter.hasNext();)
        {
            Range range = deliveredIter.next();
            all.add(range);
        }
        for (Iterator<Range> prefetchedIter = prefetched.iterator(); prefetchedIter.hasNext();)
        {
            Range range = prefetchedIter.next();
            all.add(range);
        }
        flushProcessed(all, false);
        getQpidSession().messageRelease(delivered, Option.SET_REDELIVERED);
        getQpidSession().messageRelease(prefetched);

        // We need to sync so that we get notify of an error.
        sync();
    }

    private RangeSet gatherRangeSet(ConcurrentLinkedQueue<Long> messageTags)
    {
        RangeSet ranges = RangeSetFactory.createRangeSet();
        while (true)
        {
            Long tag = messageTags.poll();
            if (tag == null)
            {
                break;
            }

            ranges.add(tag.intValue());
        }

        return ranges;
    }

    public void releaseForRollback()
    {
        if (_txSize > 0)
        {
            flushProcessed(_txRangeSet, false);
            getQpidSession().messageRelease(_txRangeSet, Option.SET_REDELIVERED);
            _txRangeSet.clear();
            _txSize = 0;
        }
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
        RangeSet ranges = RangeSetFactory.createRangeSet();
        ranges.add((int) deliveryTag);
        flushProcessed(ranges, false);
        if (requeue)
        {
            getQpidSession().messageRelease(ranges);
        }
        else
        {
            getQpidSession().messageRelease(ranges, Option.SET_REDELIVERED);
        }
        //I don't think we need to sync
    }

    /**
     * Create an 0_10 message consumer
     */
    public BasicMessageConsumer_0_10 createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
                                                      final int prefetchLow, final boolean noLocal,
                                                      final boolean exclusive, String messageSelector,
                                                      final FieldTable rawSelector, final boolean noConsume,
                                                      final boolean autoClose) throws JMSException
    {
        return new BasicMessageConsumer_0_10(getChannelId(), getAMQConnection(), destination, messageSelector, noLocal,
                getMessageFactoryRegistry(), this, rawSelector, prefetchHigh, prefetchLow,
                                             exclusive, getAcknowledgeMode(), noConsume, autoClose);
    }

    /**
     * Bind a queue with an exchange.
     */

    public boolean isQueueBound(final AMQShortString exchangeName, final AMQShortString queueName, final AMQShortString routingKey)
    throws JMSException
    {
        return isQueueBound(exchangeName,queueName,routingKey,null);
    }

    public boolean isQueueBound(final AMQDestination destination) throws JMSException
    {
        return isQueueBound(destination.getExchangeName(),destination.getAMQQueueName(),destination.getRoutingKey(),destination.getBindingKeys());
    }

    public boolean isQueueBound(final AMQShortString exchangeName, final AMQShortString queueName, final AMQShortString routingKey,AMQShortString[] bindingKeys)
    throws JMSException
    {
        String rk = null;        
        if (bindingKeys != null && bindingKeys.length>0)
        {
            rk = bindingKeys[0].toString();
        }
        else if (routingKey != null)
        {
            rk = routingKey.toString();
        }
                
        return isQueueBound(exchangeName == null ? null : exchangeName.toString(),queueName == null ? null : queueName.toString(),rk,null);
    }
    
    public boolean isQueueBound(final String exchangeName, final String queueName, final String bindingKey,Map<String,Object> args)
    {
        boolean res;
        ExchangeBoundResult bindingQueryResult =
            getQpidSession().exchangeBound(exchangeName,queueName, bindingKey, args).get();

        if (bindingKey == null)
        {
            res = !(bindingQueryResult.getExchangeNotFound() || bindingQueryResult.getQueueNotFound());
        }
        else
        {   
            if (args == null)
            {
                res = !(bindingQueryResult.getKeyNotMatched() || bindingQueryResult.getQueueNotFound() || bindingQueryResult
                        .getQueueNotMatched());
            }
            else
            {
                res = !(bindingQueryResult.getKeyNotMatched() || bindingQueryResult.getQueueNotFound() || bindingQueryResult
                        .getQueueNotMatched() || bindingQueryResult.getArgsNotMatched());
            }
        }
        return res;
    }

    /**
     * This method is invoked when a consumer is created
     * Registers the consumer with the broker
     */
    public void sendConsume(BasicMessageConsumer_0_10 consumer, AMQShortString queueName,
                            boolean nowait, int tag)
            throws AMQException, FailoverException
    {
        if (AMQDestination.DestSyntax.ADDR == consumer.getDestination().getDestSyntax())
        {
            if (AMQDestination.TOPIC_TYPE == consumer.getDestination().getAddressType())
            {
                String selector =  consumer.getMessageSelectorFilter() == null? null : consumer.getMessageSelectorFilter().getSelector();

                createSubscriptionQueue(consumer.getDestination(), consumer.isNoLocal(), selector);
                queueName = consumer.getDestination().getAMQQueueName();
                consumer.setQueuename(queueName);
            }
            handleLinkCreation(consumer.getDestination());
        }
        boolean preAcquire = consumer.isPreAcquire();

        AMQDestination destination = consumer.getDestination();
        long capacity = consumer.getCapacity();

        Map<String, Object> arguments = FieldTable.convertToMap(consumer.getArguments());

        Link link = destination.getLink();
        if (link != null && link.getSubscription() != null && link.getSubscription().getArgs() != null)
        {
            arguments.putAll((Map<? extends String, ? extends Object>) link.getSubscription().getArgs());
        }

        boolean acceptModeNone = getAcknowledgeMode() == NO_ACKNOWLEDGE;

        getQpidSession().messageSubscribe
            (queueName.toString(), String.valueOf(tag),
             acceptModeNone ? MessageAcceptMode.NONE : MessageAcceptMode.EXPLICIT,
             preAcquire ? MessageAcquireMode.PRE_ACQUIRED : MessageAcquireMode.NOT_ACQUIRED, null, 0, arguments,
             consumer.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

        String consumerTag = ((BasicMessageConsumer_0_10)consumer).getConsumerTagString();

        if (capacity == 0)
        {
            getQpidSession().messageSetFlowMode(consumerTag, MessageFlowMode.CREDIT);
        }
        else
        {
            getQpidSession().messageSetFlowMode(consumerTag, MessageFlowMode.WINDOW);
        }
        getQpidSession().messageFlow(consumerTag, MessageCreditUnit.BYTE, 0xFFFFFFFF,
                                     Option.UNRELIABLE);

        if(capacity > 0 && getDispatcher() != null && (isStarted() || isImmediatePrefetch()))
        {
            // set the flow
            getQpidSession().messageFlow(consumerTag,
                                         MessageCreditUnit.MESSAGE,
                                         capacity,
                                         Option.UNRELIABLE);
        }
        sync();
    }

    /**
     * Create an 0_10 message producer
     */
    public BasicMessageProducer_0_10 createMessageProducer(final Destination destination, final Boolean mandatory,
                                                           final Boolean immediate, final long producerId) throws JMSException
    {
        try
        {
            return new BasicMessageProducer_0_10(getAMQConnection(), (AMQDestination) destination, isTransacted(), getChannelId(), this,
                                             producerId, immediate, mandatory);
        }
        catch (AMQException e)
        {
            throw toJMSException("Error creating producer",e);
        }
        catch(TransportException e)
        {
            throw toJMSException("Exception while creating message producer:" + e.getMessage(), e);
        }

    }

    /**
     * creates an exchange if it does not already exist
     */
    public void sendExchangeDeclare(final AMQShortString name, final AMQShortString type, final boolean nowait,
                                    boolean durable, boolean autoDelete, boolean internal) throws AMQException, FailoverException
    {
        //The 'internal' parameter is ignored on the 0-10 path, the protocol does not support it
        sendExchangeDeclare(name.asString(), type.asString(), null, null, nowait, durable, autoDelete);
    }

    public void sendExchangeDeclare(final String name, final String type,
            final String alternateExchange, final Map<String, Object> args,
            final boolean nowait, boolean durable, boolean autoDelete) throws AMQException
    {
        getQpidSession().exchangeDeclare(
                name,
                type,
                alternateExchange,
                args,
                name.toString().startsWith("amq.") ? Option.PASSIVE : Option.NONE,
                durable ? Option.DURABLE : Option.NONE,
                autoDelete ? Option.AUTO_DELETE : Option.NONE);
        // We need to sync so that we get notify of an error.
        if (!nowait)
        {
            sync();
        }
    }

    /**
     * deletes an exchange 
     */
    public void sendExchangeDelete(final String name, final boolean nowait)
                throws AMQException, FailoverException
    {
        getQpidSession().exchangeDelete(name);
        // We need to sync so that we get notify of an error.
        if (!nowait)
        {
            sync();
        }
    }

    /**
     * Declare a queue with the given queueName
     */
    public AMQShortString send0_10QueueDeclare(final AMQDestination amqd, final boolean noLocal,
                                               final boolean nowait, boolean passive)
            throws AMQException
    {
        AMQShortString queueName;
        if (amqd.getAMQQueueName() == null)
        {
            // generate a name for this queue
            queueName = new AMQShortString("TempQueue" + UUID.randomUUID());
            amqd.setQueueName(queueName);
        }
        else
        {
            queueName = amqd.getAMQQueueName();
        }

        if (amqd.getDestSyntax() == DestSyntax.BURL)
        {        
            Map<String,Object> arguments = new HashMap<String,Object>();
            if (noLocal)
            {            
                arguments.put(AddressHelper.NO_LOCAL, true);
            } 

            getQpidSession().queueDeclare(queueName.toString(), "" , arguments,
                                          amqd.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                                          amqd.isDurable() ? Option.DURABLE : Option.NONE,
                                          amqd.isExclusive() ? Option.EXCLUSIVE : Option.NONE,
                                          passive ? Option.PASSIVE : Option.NONE);
        }
        else
        {
            // This code is here to ensure address based destination work with the declareQueue public method in AMQSession.java
            Node node = amqd.getNode();
            Map<String,Object> arguments = new HashMap<String,Object>();
            arguments.putAll((Map<? extends String, ? extends Object>) node.getDeclareArgs());
            if (arguments == null || arguments.get(AddressHelper.NO_LOCAL) == null)
            {
                arguments.put(AddressHelper.NO_LOCAL, noLocal);
            }
            getQpidSession().queueDeclare(queueName.toString(), node.getAlternateExchange() ,
                    arguments,
                    node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                    node.isDurable() ? Option.DURABLE : Option.NONE,
                    node.isExclusive() ? Option.EXCLUSIVE : Option.NONE);   
        }

        // passive --> false
        if (!nowait)
        {
            // We need to sync so that we get notify of an error.
            sync();
        }
        return queueName;
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
        sync();
    }

    /**
     * Activate/deactivate the message flow for all the consumers of this session.
     */
    public void sendSuspendChannel(boolean suspend) throws AMQException, FailoverException
    {
        if (suspend)
        {
            for (BasicMessageConsumer consumer : getConsumers().values())
            {
                getQpidSession().messageStop(String.valueOf(consumer.getConsumerTag()),
                                             Option.UNRELIABLE);
            }
            sync();
        }
        else
        {
            for (BasicMessageConsumer_0_10 consumer : getConsumers().values())
            {
                String consumerTag = String.valueOf(consumer.getConsumerTag());
                //only set if msg list is null
                try
                {
                    long capacity = consumer.getCapacity();
                    
                    if (capacity == 0)
                    {
                        if (consumer.getMessageListener() != null)
                        {
                            getQpidSession().messageFlow(consumerTag,
                                                         MessageCreditUnit.MESSAGE, 1,
                                                         Option.UNRELIABLE);
                        }
                    }
                    else
                    {
                        getQpidSession()
                            .messageFlow(consumerTag, MessageCreditUnit.MESSAGE,
                                         capacity,
                                         Option.UNRELIABLE);
                    }
                    getQpidSession()
                        .messageFlow(consumerTag, MessageCreditUnit.BYTE, 0xFFFFFFFF,
                                     Option.UNRELIABLE);
                }
                catch (Exception e)
                {
                    throw new AMQException(AMQConstant.INTERNAL_ERROR, "Error while trying to get the listener", e);
                }
            }
        }
        // We need to sync so that we get notify of an error.
        sync();
    }


    public void sendRollback() throws AMQException, FailoverException
    {
        getQpidSession().txRollback();
        // We need to sync so that we get notify of an error.
        sync();
    }

    //------ Private methods
    /**
     * Access to the underlying Qpid Session
     *
     * @return The associated Qpid Session.
     */
    protected Session getQpidSession()
    {
        return _qpidSession;
    }


    /**
     * Get the latest thrown exception.
     *
     * @throws SessionException get the latest thrown error.
     */
    public AMQException getCurrentException()
    {
        AMQException amqe = null;
        synchronized (_currentExceptionLock)
        {
            if (_currentException != null)
            {
                amqe = _currentException;
                _currentException = null;
            }
        }
        return amqe;
    }

    public void opened(Session ssn) {}

    public void resumed(Session ssn)
    {
        _qpidConnection = ssn.getConnection();
    }

    public void message(Session ssn, MessageTransfer xfr)
    {
        messageReceived(new UnprocessedMessage_0_10(xfr));
    }

    public void exception(Session ssn, SessionException exc)
    {
        setCurrentException(exc);
    }

    public void closed(Session ssn)
    {
        try
        {
            super.closed(null);
            if (flushTask != null)
            {
                flushTask.cancel();
                flushTask = null;
            }
        } catch (Exception e)
        {
            _logger.error("Error closing JMS session", e);
        }
    }

    public AMQException getLastException()
    {
        return getCurrentException();
    }

    @Override
    protected AMQShortString declareQueue(final AMQDestination amqd,
                                          final boolean noLocal, final boolean nowait, final boolean passive)
            throws AMQException
    {
        return new FailoverNoopSupport<AMQShortString, AMQException>(
                new FailoverProtectedOperation<AMQShortString, AMQException>()
                {
                    public AMQShortString execute() throws AMQException, FailoverException
                    {
                        // Generate the queue name if the destination indicates that a client generated name is to be used.
                        if (amqd.isNameRequired())
                        {
                            String binddingKey = "";
                            for(AMQShortString key : amqd.getBindingKeys())
                            {
                               binddingKey = binddingKey + "_" + key.toString();
                            }
                            amqd.setQueueName(new AMQShortString( binddingKey + "@"
                                    + amqd.getExchangeName().toString() + "_" + UUID.randomUUID()));
                        }
                        return send0_10QueueDeclare(amqd, noLocal, nowait, passive);
                    }
                }, getAMQConnection()).execute();
    }

    protected Long requestQueueDepth(AMQDestination amqd, boolean sync)
    {
        flushAcknowledgments();
        if (sync)
        {
            getQpidSession().sync();
        }
        return getQpidSession().queueQuery(amqd.getQueueName()).get().getMessageCount();
    }


    /**
     * Store non committed messages for this session
     * @param id
     */
    @Override protected void addDeliveredMessage(long id)
    {
        _txRangeSet.add((int) id);
        _txSize++;
    }

    /**
     * With 0.10 messages are consumed with window mode, we must send a completion
     * before the window size is reached so credits don't dry up.
     */
    protected void sendTxCompletionsIfNecessary()
    {
        // this is a heuristic, we may want to have that configurable
        if (_txSize > 0 && (getAMQConnection().getMaxPrefetch() == 1 ||
                getAMQConnection().getMaxPrefetch() != 0 && _txSize % (getAMQConnection().getMaxPrefetch() / 2) == 0))
        {
            // send completed so consumer credits don't dry up
            messageAcknowledge(_txRangeSet, false);
        }
    }

    public void commitImpl() throws AMQException, FailoverException, TransportException
    {
        if( _txSize > 0 )
        {
            messageAcknowledge(_txRangeSet, true);
            _txRangeSet.clear();
            _txSize = 0;
        }

        getQpidSession().setAutoSync(true);
        try
        {
            getQpidSession().txCommit();
        }
        finally
        {
            getQpidSession().setAutoSync(false);
        }
        // We need to sync so that we get notify of an error.
        sync();
    }

    protected final boolean tagLE(long tag1, long tag2)
    {
        return Serial.le((int) tag1, (int) tag2);
    }

    protected final boolean updateRollbackMark(long currentMark, long deliveryTag)
    {
        return Serial.lt((int) currentMark, (int) deliveryTag);
    }

    public void sync() throws AMQException
    {
        try
        {
            getQpidSession().sync();
        }
        catch (SessionException se)
        {
            setCurrentException(se);
        }

        AMQException amqe = getCurrentException();
        if (amqe != null)
        {
            throw amqe;
        }
    }

    public void setCurrentException(SessionException se)
    {
        synchronized (_currentExceptionLock)
        {
            ExecutionException ee = se.getException();
            int code = AMQConstant.INTERNAL_ERROR.getCode();
            if (ee != null)
            {
                code = ee.getErrorCode().getValue();
            }
            AMQException amqe = new AMQException(AMQConstant.getConstant(code), _isHardError, se.getMessage(), se.getCause());
            _currentException = amqe;
        }
        if (!_isHardError)
        {
            cancelTimerTask();
            stopDispatcherThread();
            try
            {
                closed(_currentException);
            }
            catch(Exception e)
            {
                _logger.warn("Error closing session", e);
            }
        }
        getAMQConnection().exceptionReceived(_currentException);
    }

    public AMQMessageDelegateFactory getMessageDelegateFactory()
    {
        return AMQMessageDelegateFactory.FACTORY_0_10;
    }
    
    public boolean isExchangeExist(AMQDestination dest,boolean assertNode) throws AMQException
    {
        boolean match = true;
        ExchangeQueryResult result = getQpidSession().exchangeQuery(dest.getAddressName(), Option.NONE).get();
        match = !result.getNotFound();        
        Node node = dest.getNode();
        
        if (match)
        {
            if (assertNode)
            {
                match =  (result.getDurable() == node.isDurable()) && 
                         (node.getExchangeType() != null && 
                          node.getExchangeType().equals(result.getType())) &&
                         (matchProps(result.getArguments(),node.getDeclareArgs()));
            }
            else
            {
                _logger.debug("Setting Exchange type " + result.getType());
                node.setExchangeType(result.getType());
                dest.setExchangeClass(new AMQShortString(result.getType()));
            }
        }

        if (assertNode)
        {
            if (!match)
            {
                throw new AMQException("Assert failed for address : " + dest  +", Result was : " + result);
            }
        }

        return match;
    }
    
    public boolean isQueueExist(AMQDestination dest, boolean assertNode) throws AMQException
    {
        boolean match = true;
        try
        {
            QueueQueryResult result = getQpidSession().queueQuery(dest.getAddressName(), Option.NONE).get();
            match = dest.getAddressName().equals(result.getQueue());
            Node node = dest.getNode();

            if (match && assertNode)
            {
                match = (result.getDurable() == node.isDurable()) && 
                         (result.getAutoDelete() == node.isAutoDelete()) &&
                         (result.getExclusive() == node.isExclusive()) &&
                         (matchProps(result.getArguments(),node.getDeclareArgs()));
            }

            if (assertNode)
            {
                if (!match)
                {
                    throw new AMQException("Assert failed for address : " + dest  +", Result was : " + result);
                }
            }
        }
        catch(SessionException e)
        {
            if (e.getException().getErrorCode() == ExecutionErrorCode.RESOURCE_DELETED)
            {
                match = false;
            }
            else
            {
                throw new AMQException(AMQConstant.getConstant(e.getException().getErrorCode().getValue()),
                        "Error querying queue",e);
            }
        }
        return match;
    }
    
    private boolean matchProps(Map<String,Object> target,Map<String,Object> source)
    {
        boolean match = true;
        for (String key: source.keySet())
        {
            match = target.containsKey(key) && 
                    target.get(key).equals(source.get(key));
            
            if (!match) 
            { 
                StringBuffer buf = new StringBuffer();
                buf.append("Property given in address did not match with the args sent by the broker.");
                buf.append(" Expected { ").append(key).append(" : ").append(source.get(key)).append(" }, ");
                buf.append(" Actual { ").append(key).append(" : ").append(target.get(key)).append(" }");
                _logger.debug(buf.toString());
                return match;
            }
        }
        
        return match;
    }

    /**
     * 1. Try to resolve the address type (queue or exchange)
     * 2. if type == queue, 
     *       2.1 verify queue exists or create if create == true
     *       2.2 If not throw exception
     *       
     * 3. if type == exchange,
     *       3.1 verify exchange exists or create if create == true
     *       3.2 if not throw exception
     *       3.3 if exchange exists (or created) create subscription queue.
     */
    
    @SuppressWarnings("deprecation")
    public void resolveAddress(AMQDestination dest,
                                              boolean isConsumer,
                                              boolean noLocal) throws AMQException
    {
        if (dest.isAddressResolved() && dest.isResolvedAfter(getAMQConnection().getLastFailoverTime()))
        {
            return;
        }
        else
        {
            boolean assertNode = (dest.getAssert() == AddressOption.ALWAYS) || 
                                 (isConsumer && dest.getAssert() == AddressOption.RECEIVER) ||
                                 (!isConsumer && dest.getAssert() == AddressOption.SENDER);
            
            boolean createNode = (dest.getCreate() == AddressOption.ALWAYS) ||
                                 (isConsumer && dest.getCreate() == AddressOption.RECEIVER) ||
                                 (!isConsumer && dest.getCreate() == AddressOption.SENDER);
                        
            
            
            int type = resolveAddressType(dest);
            
            switch (type)
            {
                case AMQDestination.QUEUE_TYPE: 
                {
                    if(createNode)
                    {
                        setLegacyFieldsForQueueType(dest);
                        handleQueueNodeCreation(dest,noLocal);
                        break;
                    }
                    else if (isQueueExist(dest,assertNode))
                    {
                        setLegacyFieldsForQueueType(dest);
                        break;
                    }
                }
                
                case AMQDestination.TOPIC_TYPE: 
                {
                    if(createNode)
                    {                    
                        setLegacyFiledsForTopicType(dest);
                        verifySubject(dest);
                        handleExchangeNodeCreation(dest);
                        break;
                    }
                    else if (isExchangeExist(dest,assertNode))
                    {                    
                        setLegacyFiledsForTopicType(dest);
                        verifySubject(dest);
                        break;
                    }
                }
                
                default:
                    throw new AMQException(
                            "The name '" + dest.getAddressName() +
                            "' supplied in the address doesn't resolve to an exchange or a queue");
            }
            dest.setAddressResolved(System.currentTimeMillis());
        }
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
            ExchangeBoundResult result = getQpidSession().exchangeBound(name,name,null,null).get();
            if (result.getQueueNotFound() && result.getExchangeNotFound()) {
                //neither a queue nor an exchange exists with that name; treat it as a queue
                type = AMQDestination.QUEUE_TYPE;
            } else if (result.getExchangeNotFound()) {
                //name refers to a queue
                type = AMQDestination.QUEUE_TYPE;
            } else if (result.getQueueNotFound()) {
                //name refers to an exchange
                type = AMQDestination.TOPIC_TYPE;
            } else {
                //both a queue and exchange exist for that name
                throw new AMQException("Ambiguous address, please specify queue or topic as node type");
            }
            dest.setAddressType(type);
            return type;
        }        
    }
    
    private void verifySubject(AMQDestination dest) throws AMQException
    {
        if (dest.getSubject() == null || dest.getSubject().trim().equals(""))
        {
            
            if ("topic".equals(dest.getExchangeClass().toString()))
            {
                dest.setRoutingKey(new AMQShortString("#"));
                dest.setSubject(dest.getRoutingKey().toString());
            }
            else
            {
                dest.setRoutingKey(new AMQShortString(""));
                dest.setSubject("");
            }
        }
    }

    void createSubscriptionQueue(AMQDestination dest, boolean noLocal, String messageSelector) throws AMQException
    {
        Link link = dest.getLink();
        String queueName = dest.getQueueName();

        if (queueName == null)
        {
            queueName = link.getName() == null ? "TempQueue" + UUID.randomUUID() : link.getName();
            dest.setQueueName(new AMQShortString(queueName));
        }

        SubscriptionQueue queueProps = link.getSubscriptionQueue();
        Map<String,Object> arguments = queueProps.getDeclareArgs();
        if (!arguments.containsKey((AddressHelper.NO_LOCAL)))
        {
            arguments.put(AddressHelper.NO_LOCAL, noLocal);
        }

        if (link.isDurable() && queueName.startsWith("TempQueue"))
        {
            throw new AMQException("You cannot mark a subscription queue as durable without providing a name for the link.");
        }

        getQpidSession().queueDeclare(queueName,
                queueProps.getAlternateExchange(), arguments,
                queueProps.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                link.isDurable() ? Option.DURABLE : Option.NONE,
                queueProps.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

        Map<String,Object> bindingArguments = new HashMap<String, Object>();
        bindingArguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue().toString(), messageSelector == null ? "" : messageSelector);
        getQpidSession().exchangeBind(queueName,
                              dest.getAddressName(),
                              dest.getSubject(),
                              bindingArguments);
    }

    public void setLegacyFieldsForQueueType(AMQDestination dest)
    {
        // legacy support
        dest.setQueueName(new AMQShortString(dest.getAddressName()));
        dest.setExchangeName(new AMQShortString(""));
        dest.setExchangeClass(new AMQShortString(""));
        dest.setRoutingKey(dest.getAMQQueueName());
    }

    public void setLegacyFiledsForTopicType(AMQDestination dest)
    {
        // legacy support
        dest.setExchangeName(new AMQShortString(dest.getAddressName()));
        Node node = dest.getNode();
        dest.setExchangeClass(node.getExchangeType() == null? 
                              ExchangeDefaults.TOPIC_EXCHANGE_CLASS:
                              new AMQShortString(node.getExchangeType()));  
        dest.setRoutingKey(new AMQShortString(dest.getSubject()));
    }
    
    protected void acknowledgeImpl()
    {
        RangeSet ranges = gatherRangeSet(getUnacknowledgedMessageTags());

        if(ranges.size() > 0 )
        {
            messageAcknowledge(ranges, true);
            getQpidSession().sync();
        }
    }

    @Override
    void resubscribe() throws AMQException
    {
        // Also reset the delivery tag tracker, to insure we dont
        // return the first <total number of msgs received on session>
        // messages sent by the brokers following the first rollback
        // after failover
        getHighestDeliveryTag().set(-1);
        // Clear txRangeSet/unacknowledgedMessageTags so we don't complete commands corresponding to
        //messages that came from the old broker.
        _txRangeSet.clear();
        _txSize = 0;
        getUnacknowledgedMessageTags().clear();
        getPrefetchedMessageTags().clear();
        super.resubscribe();
        getQpidSession().sync();
    }

    @Override
    void stop() throws AMQException
    {
        super.stop();
        setUsingDispatcherForCleanup(true);
        drainDispatchQueue();
        setUsingDispatcherForCleanup(false);

        for (BasicMessageConsumer consumer : getConsumers().values())
        {
            List<Long> tags = consumer.drainReceiverQueueAndRetrieveDeliveryTags();
            getPrefetchedMessageTags().addAll(tags);
        }
        
        RangeSet delivered = gatherRangeSet(getUnacknowledgedMessageTags());
		RangeSet prefetched = gatherRangeSet(getPrefetchedMessageTags());
		RangeSet all = RangeSetFactory.createRangeSet(delivered.size()
					+ prefetched.size());

		for (Iterator<Range> deliveredIter = delivered.iterator(); deliveredIter.hasNext();)
		{
			Range range = deliveredIter.next();
			all.add(range);
		}

		for (Iterator<Range> prefetchedIter = prefetched.iterator(); prefetchedIter.hasNext();)
		{
			Range range = prefetchedIter.next();
			all.add(range);
		}

		flushProcessed(all, false);
		getQpidSession().messageRelease(delivered,Option.SET_REDELIVERED);
		getQpidSession().messageRelease(prefetched);
		sync();
    }

    @Override
    public boolean isFlowBlocked()
    {
        return _qpidSession.isFlowBlocked();
    }

    @Override
    public void setFlowControl(boolean active)
    {
        // Supported by 0-8..0-9-1 only
        throw new UnsupportedOperationException("Operation not supported by this protocol");
    }

    private void cancelTimerTask()
    {
        if (flushTask != null)
        {
            flushTask.cancel();
            flushTask = null;
        }
    }

    private void handleQueueNodeCreation(AMQDestination dest, boolean noLocal) throws AMQException
    {
        Node node = dest.getNode();
        Map<String,Object> arguments = node.getDeclareArgs();
        if (!arguments.containsKey((AddressHelper.NO_LOCAL)))
        {
            arguments.put(AddressHelper.NO_LOCAL, noLocal);
        }
        getQpidSession().queueDeclare(dest.getAddressName(),
                node.getAlternateExchange(), arguments,
                node.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                node.isDurable() ? Option.DURABLE : Option.NONE,
                node.isExclusive() ? Option.EXCLUSIVE : Option.NONE);

        createBindings(dest, dest.getNode().getBindings());
        sync();
    }

    void handleExchangeNodeCreation(AMQDestination dest) throws AMQException
    {
        Node node = dest.getNode();
        sendExchangeDeclare(dest.getAddressName(),
                node.getExchangeType(),
                node.getAlternateExchange(),
                node.getDeclareArgs(),
                false,
                node.isDurable(),
                node.isAutoDelete());

        // If bindings are specified without a queue name and is called by the producer,
        // the broker will send an exception as expected.
        createBindings(dest, dest.getNode().getBindings());
        sync();
    }

    void handleLinkCreation(AMQDestination dest) throws AMQException
    {
        createBindings(dest, dest.getLink().getBindings());
    }

    void createBindings(AMQDestination dest, List<Binding> bindings)
    {
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

        for (Binding binding: bindings)
        {
            String queue = binding.getQueue() == null?
                    defaultQueueName: binding.getQueue();

            String exchange = binding.getExchange() == null ?
                        defaultExchangeForBinding :
                        binding.getExchange();

            if (_logger.isDebugEnabled())
            {
                _logger.debug("Binding queue : " + queue +
                         " exchange: " + exchange +
                         " using binding key " + binding.getBindingKey() +
                         " with args " + Strings.printMap(binding.getArgs()));
            }
            getQpidSession().exchangeBind(queue,
                                     exchange,
                                     binding.getBindingKey(),
                                     binding.getArgs());
       }
    }

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

        for (Binding binding: dest.getLink().getBindings())
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
            getQpidSession().exchangeUnbind(queue, exchange,
                                            binding.getBindingKey());
        }
    }

    void deleteSubscriptionQueue(AMQDestination dest) throws AMQException
    {
        // We need to delete the subscription queue.
        if (dest.getAddressType() == AMQDestination.TOPIC_TYPE &&
            dest.getLink().getSubscriptionQueue().isExclusive() &&
            isQueueExist(dest, false))
        {
            getQpidSession().queueDelete(dest.getQueueName());
        }
    }

    void handleNodeDelete(AMQDestination dest) throws AMQException
    {
        if (AMQDestination.TOPIC_TYPE == dest.getAddressType())
        {
            if (isExchangeExist(dest,false))
            {
                getQpidSession().exchangeDelete(dest.getAddressName());
                dest.setAddressResolved(0);
            }
        }
        else
        {
            if (isQueueExist(dest,false))
            {
                getQpidSession().queueDelete(dest.getAddressName());
                dest.setAddressResolved(0);
            }
        }
    }
}
