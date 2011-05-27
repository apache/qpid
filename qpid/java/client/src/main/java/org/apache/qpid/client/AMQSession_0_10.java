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
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

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
import org.apache.qpid.client.messaging.address.Link;
import org.apache.qpid.client.messaging.address.Link.Reliability;
import org.apache.qpid.client.messaging.address.Node.ExchangeNode;
import org.apache.qpid.client.messaging.address.Node.QueueNode;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.protocol.AMQConstant;
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
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionException;
import org.apache.qpid.transport.SessionListener;
import org.apache.qpid.util.Serial;
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
    protected org.apache.qpid.transport.Connection _qpidConnection;

    private long maxAckDelay = Long.getLong("qpid.session.max_ack_delay", 1000);
    private TimerTask flushTask = null;
    private RangeSet unacked = new RangeSet();
    private int unackedCount = 0;    

    /**
     * USed to store the range of in tx messages
     */
    private RangeSet _txRangeSet = new RangeSet();
    private int _txSize = 0;    
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
                    int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {

        super(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, defaultPrefetchHighMark,
              defaultPrefetchLowMark);
        _qpidConnection = qpidConnection;
        _qpidSession = _qpidConnection.createSession(1);
        _qpidSession.setSessionListener(this);
        if (_transacted)
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
                    boolean transacted, int acknowledgeMode, int defaultPrefetchHigh, int defaultPrefetchLow)
    {

        this(qpidConnection, con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(),
             defaultPrefetchHigh, defaultPrefetchLow);
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
            _logger.debug("Sending ack for delivery tag " + deliveryTag + " on session " + _channelId);
        }
        // acknowledge this message
        if (multiple)
        {
            for (Long messageTag : _unacknowledgedMessageTags)
            {
                if( messageTag <= deliveryTag )
                {
                    addUnacked(messageTag.intValue());
                    _unacknowledgedMessageTags.remove(messageTag);
                }
            }
            //empty the list of unack messages

        }
        else
        {
            addUnacked((int) deliveryTag);
            _unacknowledgedMessageTags.remove(deliveryTag);
        }

        long prefetch = getAMQConnection().getMaxPrefetch();

        if (unackedCount >= prefetch/2 || maxAckDelay <= 0)
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
                    (unacked, _acknowledgeMode != org.apache.qpid.jms.Session.NO_ACKNOWLEDGE,setSyncBit);
                clearUnacked();
            }
        }
    }

    void messageAcknowledge(RangeSet ranges, boolean accept)
    {
        messageAcknowledge(ranges,accept,false);
    }
    
    void messageAcknowledge(RangeSet ranges, boolean accept,boolean setSyncBit)
    {
        Session ssn = getQpidSession();
        for (Range range : ranges)
        {
            ssn.processed(range);
        }
        ssn.flushProcessed(accept ? BATCH : NONE);
        if (accept)
        {
            ssn.messageAccept(ranges, UNRELIABLE,setSyncBit? SYNC : NONE);
        }
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
            List<Binding> bindings = new ArrayList<Binding>();
            bindings.addAll(destination.getSourceNode().getBindings());
            bindings.addAll(destination.getTargetNode().getBindings());
            
            String defaultExchange = destination.getAddressType() == AMQDestination.TOPIC_TYPE ?
                                     destination.getAddressName(): "amq.topic";
            
            for (Binding binding: bindings)
            {
                String queue = binding.getQueue() == null?
                                   queueName.asString(): binding.getQueue();
                                   
               String exchange = binding.getExchange() == null ? 
                                 defaultExchange :
                                 binding.getExchange();
                        
                _logger.debug("Binding queue : " + queue + 
                              " exchange: " + exchange + 
                              " using binding key " + binding.getBindingKey() + 
                              " with args " + printMap(binding.getArgs()));
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
        if (flushTask != null)
        {
            flushTask.cancel();
            flushTask = null;
        }
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
     * Commit the receipt and the delivery of all messages exchanged by this session resources.
     */
    public void sendCommit() throws AMQException, FailoverException
    {
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
        RangeSet ranges = new RangeSet();
        while (true)
        {
            Long tag = _unacknowledgedMessageTags.poll();
            if (tag == null)
            {
                break;
            }
            ranges.add((int) (long) tag);
        }
        getQpidSession().messageRelease(ranges, Option.SET_REDELIVERED);
        // We need to sync so that we get notify of an error.
        sync();
    }


    public void releaseForRollback()
    {
        getQpidSession().messageRelease(_txRangeSet, Option.SET_REDELIVERED);
        _txRangeSet.clear();
        _txSize = 0;
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
        ranges.add((int) deliveryTag);
        getQpidSession().messageRelease(ranges, Option.SET_REDELIVERED);
        //I don't think we need to sync
    }

    /**
     * Create an 0_10 message consumer
     */
    public BasicMessageConsumer_0_10 createMessageConsumer(final AMQDestination destination, final int prefetchHigh,
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
                
        return isQueueBound(exchangeName.toString(),queueName.toString(),rk,null);
    }
    
    public boolean isQueueBound(final String exchangeName, final String queueName, final String bindingKey,Map<String,Object> args)
    throws JMSException
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
    public void sendConsume(BasicMessageConsumer_0_10 consumer, AMQShortString queueName, AMQProtocolHandler protocolHandler,
                            boolean nowait, String messageSelector, int tag)
            throws AMQException, FailoverException
    {        
        boolean preAcquire;
        
        long capacity = getCapacity(consumer.getDestination());
        
        try
        {
            boolean isTopic;
            Map<String, Object> arguments = FieldTable.convertToMap(consumer.getArguments());
            
            if (consumer.getDestination().getDestSyntax() == AMQDestination.DestSyntax.BURL)
            {
                isTopic = consumer.getDestination() instanceof AMQTopic ||
                          consumer.getDestination().getExchangeClass().equals(ExchangeDefaults.TOPIC_EXCHANGE_CLASS) ;
                
                preAcquire = isTopic || (!consumer.isNoConsume()  && 
                        (consumer.getMessageSelector() == null || consumer.getMessageSelector().equals("")));
            }
            else
            {
                isTopic = consumer.getDestination().getAddressType() == AMQDestination.TOPIC_TYPE;
                
                preAcquire = !consumer.isNoConsume() && 
                             (isTopic || consumer.getMessageSelector() == null || 
                              consumer.getMessageSelector().equals(""));
                
                arguments.putAll(
                        (Map<? extends String, ? extends Object>) consumer.getDestination().getLink().getSubscription().getArgs());
            }
            
            boolean acceptModeNone = getAcknowledgeMode() == NO_ACKNOWLEDGE;
            
            if (consumer.getDestination().getLink() != null)
            {
                acceptModeNone = consumer.getDestination().getLink().getReliability() == Link.Reliability.UNRELIABLE;
            }
            
            getQpidSession().messageSubscribe
                (queueName.toString(), String.valueOf(tag),
                 acceptModeNone ? MessageAcceptMode.NONE : MessageAcceptMode.EXPLICIT,
                 preAcquire ? MessageAcquireMode.PRE_ACQUIRED : MessageAcquireMode.NOT_ACQUIRED, null, 0, arguments,
                 consumer.isExclusive() ? Option.EXCLUSIVE : Option.NONE);
        }
        catch (JMSException e)
        {
            throw new AMQException(AMQConstant.INTERNAL_ERROR, "problem when registering consumer", e);
        }

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

        if(capacity > 0 && _dispatcher != null && (isStarted() || _immediatePrefetch))
        {
            // set the flow
            getQpidSession().messageFlow(consumerTag,
                                         MessageCreditUnit.MESSAGE,
                                         capacity,
                                         Option.UNRELIABLE);
        }

        if (!nowait)
        {
            sync();
        }
    }

    private long getCapacity(AMQDestination destination)
    {
        long capacity = 0;
        if (destination.getDestSyntax() == DestSyntax.ADDR && 
                destination.getLink().getConsumerCapacity() > 0)
        {
            capacity = destination.getLink().getConsumerCapacity();
        }
        else if (prefetch())
        {
            capacity = getAMQConnection().getMaxPrefetch();
        }
        return capacity;
    }

    /**
     * Create an 0_10 message producer
     */
    public BasicMessageProducer_0_10 createMessageProducer(final Destination destination, final boolean mandatory,
                                                      final boolean immediate, final boolean waitUntilSent,
                                                      long producerId) throws JMSException
    {
        try
        {
            return new BasicMessageProducer_0_10(_connection, (AMQDestination) destination, _transacted, _channelId, this,
                                             getProtocolHandler(), producerId, immediate, mandatory, waitUntilSent);
        }
        catch (AMQException e)
        {
            JMSException ex = new JMSException("Error creating producer");
            ex.initCause(e);
            ex.setLinkedException(e);
            
            throw ex;
        }

    }

    /**
     * creates an exchange if it does not already exist
     */
    public void sendExchangeDeclare(final AMQShortString name,
            final AMQShortString type,
            final AMQProtocolHandler protocolHandler, final boolean nowait)
            throws AMQException, FailoverException
    {
        sendExchangeDeclare(name.asString(), type.asString(), null, null,
                nowait);
    }

    public void sendExchangeDeclare(final String name, final String type,
            final String alternateExchange, final Map<String, Object> args,
            final boolean nowait) throws AMQException
    {
        getQpidSession().exchangeDeclare(
                name,
                type,
                alternateExchange,
                args,
                name.toString().startsWith("amq.") ? Option.PASSIVE
                        : Option.NONE);
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
    public void sendQueueDeclare(final AMQDestination amqd, final AMQProtocolHandler protocolHandler,
                                 final boolean nowait)
            throws AMQException, FailoverException
    {
        // do nothing this is only used by 0_8
    }

    /**
     * Declare a queue with the given queueName
     */
    public AMQShortString send0_10QueueDeclare(final AMQDestination amqd, final AMQProtocolHandler protocolHandler,
                                               final boolean noLocal, final boolean nowait)
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
                arguments.put("no-local", true);
            } 

            getQpidSession().queueDeclare(queueName.toString(), "" , arguments,
                                          amqd.isAutoDelete() ? Option.AUTO_DELETE : Option.NONE,
                                          amqd.isDurable() ? Option.DURABLE : Option.NONE,
                                          amqd.isExclusive() ? Option.EXCLUSIVE : Option.NONE);   
        }
        else
        {
            QueueNode node = (QueueNode)amqd.getSourceNode();
            getQpidSession().queueDeclare(queueName.toString(), node.getAlternateExchange() ,
                    node.getDeclareArgs(),
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
            for (BasicMessageConsumer consumer : _consumers.values())
            {
                getQpidSession().messageStop(String.valueOf(consumer.getConsumerTag()),
                                             Option.UNRELIABLE);
            }
        }
        else
        {
            for (BasicMessageConsumer_0_10 consumer : _consumers.values())
            {
                String consumerTag = String.valueOf(consumer.getConsumerTag());
                //only set if msg list is null
                try
                {
                    long capacity = getCapacity(consumer.getDestination());
                    
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

    protected AMQShortString declareQueue(final AMQDestination amqd, final AMQProtocolHandler protocolHandler,
                                          final boolean noLocal, final boolean nowait)
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
                            String binddingKey = "";
                            for(AMQShortString key : amqd.getBindingKeys())
                            {
                               binddingKey = binddingKey + "_" + key.toString();
                            }
                            amqd.setQueueName(new AMQShortString( binddingKey + "@"
                                    + amqd.getExchangeName().toString() + "_" + UUID.randomUUID()));
                        }
                        return send0_10QueueDeclare(amqd, protocolHandler, noLocal, nowait);
                    }
                }, _connection).execute();
    }

    protected Long requestQueueDepth(AMQDestination amqd)
    {
        flushAcknowledgments();
        return getQpidSession().queueQuery(amqd.getQueueName()).get().getMessageCount();
    }


    /**
     * Store non committed messages for this session
     * With 0.10 messages are consumed with window mode, we must send a completion
     * before the window size is reached so credits don't dry up.
     * @param id
     */
    @Override protected void addDeliveredMessage(long id)
    {
        _txRangeSet.add((int) id);
        _txSize++;
        // this is a heuristic, we may want to have that configurable
        if (_connection.getMaxPrefetch() == 1 ||
                _connection.getMaxPrefetch() != 0 && _txSize % (_connection.getMaxPrefetch() / 2) == 0)
        {
            // send completed so consumer credits don't dry up
            messageAcknowledge(_txRangeSet, false);
        }
    }

    @Override public void commit() throws JMSException
    {
        checkTransacted();
        try
        {
            if( _txSize > 0 )
            {
                messageAcknowledge(_txRangeSet, true);
                _txRangeSet.clear();
                _txSize = 0;
            }
            sendCommit();
        }
        catch (AMQException e)
        {
            throw new JMSAMQException("Failed to commit: " + e.getMessage(), e);
        }
        catch (FailoverException e)
        {
            throw new JMSAMQException("Fail-over interrupted commit. Status of the commit is uncertain.", e);
        }
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
            AMQException amqe = new AMQException(AMQConstant.getConstant(code), se.getMessage(), se.getCause());
            _currentException = amqe;
        }
        _connection.exceptionReceived(_currentException);
    }

    public AMQMessageDelegateFactory getMessageDelegateFactory()
    {
        return AMQMessageDelegateFactory.FACTORY_0_10;
    }
    
    public boolean isExchangeExist(AMQDestination dest,ExchangeNode node,boolean assertNode)
    {
        boolean match = true;
        ExchangeQueryResult result = getQpidSession().exchangeQuery(dest.getAddressName(), Option.NONE).get();
        match = !result.getNotFound();        
        
        if (match)
        {
            if (assertNode)
            {
                match =  (result.getDurable() == node.isDurable()) && 
                         (node.getExchangeType() != null && 
                          node.getExchangeType().equals(result.getType())) &&
                         (matchProps(result.getArguments(),node.getDeclareArgs()));
            }            
            else if (node.getExchangeType() != null)
            {
                // even if assert is false, better to verify this
                match = node.getExchangeType().equals(result.getType());
                if (!match)
                {
                    _logger.debug("Exchange type doesn't match. Expected : " +  node.getExchangeType() +
                             " actual " + result.getType());
                }
            }
            else
            {
                _logger.debug("Setting Exchange type " + result.getType());
                node.setExchangeType(result.getType());
                dest.setExchangeClass(new AMQShortString(result.getType()));
            }
        }
        
        return match;
    }
    
    public boolean isQueueExist(AMQDestination dest,QueueNode node,boolean assertNode) throws AMQException
    {
        boolean match = true;
        try
        {
            QueueQueryResult result = getQpidSession().queueQuery(dest.getAddressName(), Option.NONE).get();
            match = dest.getAddressName().equals(result.getQueue());
            
            if (match && assertNode)
            {
                match = (result.getDurable() == node.isDurable()) && 
                         (result.getAutoDelete() == node.isAutoDelete()) &&
                         (result.getExclusive() == node.isExclusive()) &&
                         (matchProps(result.getArguments(),node.getDeclareArgs()));
            }
            else if (match)
            {
                // should I use the queried details to update the local data structure.
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
    public void handleAddressBasedDestination(AMQDestination dest, 
                                              boolean isConsumer,
                                              boolean noWait) throws AMQException
    {
        if (dest.isAddressResolved())
        {           
            if (isConsumer && AMQDestination.TOPIC_TYPE == dest.getAddressType()) 
            {
                createSubscriptionQueue(dest);
            }
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
            
            if (type == AMQDestination.QUEUE_TYPE && 
                    dest.getLink().getReliability() == Reliability.UNSPECIFIED)
            {
                dest.getLink().setReliability(Reliability.AT_LEAST_ONCE);
            }
            else if (type == AMQDestination.TOPIC_TYPE && 
                    dest.getLink().getReliability() == Reliability.UNSPECIFIED)
            {
                dest.getLink().setReliability(Reliability.UNRELIABLE);
            }
            else if (type == AMQDestination.TOPIC_TYPE && 
                    dest.getLink().getReliability() == Reliability.AT_LEAST_ONCE)
            {
                throw new AMQException("AT-LEAST-ONCE is not yet supported for Topics");                      
            }
            
            switch (type)
            {
                case AMQDestination.QUEUE_TYPE: 
                {
                    if (isQueueExist(dest,(QueueNode)dest.getSourceNode(),assertNode))
                    {
                        setLegacyFiledsForQueueType(dest);
                        break;
                    }
                    else if(createNode)
                    {
                        setLegacyFiledsForQueueType(dest);
                        send0_10QueueDeclare(dest,null,false,noWait);
                        sendQueueBind(dest.getAMQQueueName(), dest.getRoutingKey(),
                                      null,dest.getExchangeName(),dest, false);
                        break;
                    }                
                }
                
                case AMQDestination.TOPIC_TYPE: 
                {
                    if (isExchangeExist(dest,(ExchangeNode)dest.getTargetNode(),assertNode))
                    {                    
                        setLegacyFiledsForTopicType(dest);
                        verifySubject(dest);
                        if (isConsumer && !isQueueExist(dest,(QueueNode)dest.getSourceNode(),true)) 
                        {  
                            createSubscriptionQueue(dest);
                        }
                        break;
                    }
                    else if(createNode)
                    {                    
                        setLegacyFiledsForTopicType(dest);
                        verifySubject(dest);
                        sendExchangeDeclare(dest.getAddressName(), 
                                dest.getExchangeClass().asString(),
                                dest.getTargetNode().getAlternateExchange(),
                                dest.getTargetNode().getDeclareArgs(),
                                false);        
                        if (isConsumer && !isQueueExist(dest,(QueueNode)dest.getSourceNode(),true)) 
                        {
                            createSubscriptionQueue(dest);
                        }
                        break;
                    }
                }
                
                default:
                    throw new AMQException(
                            "The name '" + dest.getAddressName() +
                            "' supplied in the address doesn't resolve to an exchange or a queue");
            }
            dest.setAddressResolved(true);
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
            dest.rebuildTargetAndSourceNodes(type);
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
    
    private void createSubscriptionQueue(AMQDestination dest) throws AMQException
    {
        QueueNode node = (QueueNode)dest.getSourceNode();  // source node is never null
        
        if (dest.getQueueName() == null)
        {
            if (dest.getLink() != null && dest.getLink().getName() != null) 
            {
                dest.setQueueName(new AMQShortString(dest.getLink().getName())); 
            }
        }
        node.setExclusive(true);
        node.setAutoDelete(!node.isDurable());
        send0_10QueueDeclare(dest,null,false,true);
        node.addBinding(new Binding(dest.getAddressName(),
                                    dest.getQueueName(),// should have one by now
                                    dest.getSubject(),
                                    Collections.<String,Object>emptyMap()));
        sendQueueBind(dest.getAMQQueueName(), dest.getRoutingKey(),
                null,dest.getExchangeName(),dest, false);
    }
    
    public void setLegacyFiledsForQueueType(AMQDestination dest)
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
        ExchangeNode node = (ExchangeNode)dest.getTargetNode();
        dest.setExchangeClass(node.getExchangeType() == null? 
                              ExchangeDefaults.TOPIC_EXCHANGE_CLASS:
                              new AMQShortString(node.getExchangeType()));  
        dest.setRoutingKey(new AMQShortString(dest.getSubject()));
    }
    
    /** This should be moved to a suitable utility class */
    private String printMap(Map<String,Object> map)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<");
        if (map != null)
        {
            for(String key : map.keySet())
            {
                sb.append(key).append(" = ").append(map.get(key)).append(" ");
            }
        }
        sb.append(">");
        return sb.toString();
    }
    
}
