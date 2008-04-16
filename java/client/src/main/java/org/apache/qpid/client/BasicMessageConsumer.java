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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.jms.MessageConsumer;
import org.apache.qpid.jms.Session;
import org.apache.qpid.AMQException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BasicMessageConsumer<H, B> extends Closeable implements MessageConsumer
{
    private static final Logger _logger = LoggerFactory.getLogger(BasicMessageConsumer.class);

    /**
     * The connection being used by this consumer
     */
    protected AMQConnection _connection;

    protected String _messageSelector;

    private boolean _noLocal;

    private AMQDestination _destination;

    /**
     * When true indicates that a blocking receive call is in progress
     */
    private final AtomicBoolean _receiving = new AtomicBoolean(false);
    /**
     * Holds an atomic reference to the listener installed.
     */
    private final AtomicReference<MessageListener> _messageListener = new AtomicReference<MessageListener>();

    /**
     * The consumer tag allows us to close the consumer by sending a jmsCancel method to the broker
     */
    protected AMQShortString _consumerTag;

    /**
     * We need to know the channel id when constructing frames
     */
    protected int _channelId;

    /**
     * Used in the blocking receive methods to receive a message from the Session thread. <p/> Or to notify of errors
     * <p/> Argument true indicates we want strict FIFO semantics
     */
    protected final ArrayBlockingQueue _synchronousQueue;

    protected MessageFactoryRegistry _messageFactory;

    protected final AMQSession _session;

    protected AMQProtocolHandler _protocolHandler;

    /**
     * We need to store the "raw" field table so that we can resubscribe in the event of failover being required
     */
    private FieldTable _rawSelectorFieldTable;

    /**
     * We store the high water prefetch field in order to be able to reuse it when resubscribing in the event of
     * failover
     */
    private int _prefetchHigh;

    /**
     * We store the low water prefetch field in order to be able to reuse it when resubscribing in the event of
     * failover
     */
    private int _prefetchLow;

    /**
     * We store the exclusive field in order to be able to reuse it when resubscribing in the event of failover
     */
    private boolean _exclusive;

    /**
     * The acknowledge mode in force for this consumer. Note that the AMQP protocol allows different ack modes per
     * consumer whereas JMS defines this at the session level, hence why we associate it with the consumer in our
     * implementation.
     */
    private int _acknowledgeMode;

    /**
     * Number of messages unacknowledged in DUPS_OK_ACKNOWLEDGE mode
     */
    private int _outstanding;

    /**
     * Switch to enable sending of acknowledgements when using DUPS_OK_ACKNOWLEDGE mode. Enabled when _outstannding
     * number of msgs >= _prefetchHigh and disabled at < _prefetchLow
     */
    private boolean _dups_ok_acknowledge_send;

    /**
     * The thread that was used to call receive(). This is important for being able to interrupt that thread if a
     * receive() is in progress.
     */
    private Thread _receivingThread;


    /**
     * Used to store this consumer queue name
     * Usefull when more than binding key should be used
     */
    private AMQShortString _queuename;

    /**
     * autoClose denotes that the consumer will automatically cancel itself when there are no more messages to receive
     * on the queue.  This is used for queue browsing.
     */
    private boolean _autoClose;
    private boolean _closeWhenNoMessages;

    private boolean _noConsume;
    private List<StackTraceElement> _closedStack = null;

    protected BasicMessageConsumer(int channelId, AMQConnection connection, AMQDestination destination,
                                   String messageSelector, boolean noLocal, MessageFactoryRegistry messageFactory,
                                   AMQSession session, AMQProtocolHandler protocolHandler,
                                   FieldTable rawSelectorFieldTable, int prefetchHigh, int prefetchLow,
                                   boolean exclusive, int acknowledgeMode, boolean noConsume, boolean autoClose)
    {
        _channelId = channelId;
        _connection = connection;
        _messageSelector = messageSelector;
        _noLocal = noLocal;
        _destination = destination;
        _messageFactory = messageFactory;
        _session = session;
        _protocolHandler = protocolHandler;
        _rawSelectorFieldTable = rawSelectorFieldTable;
        _prefetchHigh = prefetchHigh;
        _prefetchLow = prefetchLow;
        _exclusive = exclusive;
        _acknowledgeMode = acknowledgeMode;
        _synchronousQueue = new ArrayBlockingQueue(prefetchHigh, true);
        _autoClose = autoClose;
        _noConsume = noConsume;

        // Force queue browsers not to use acknowledge modes.
        if (_noConsume)
        {
            _acknowledgeMode = Session.NO_ACKNOWLEDGE;
        }
    }

    public AMQDestination getDestination()
    {
        return _destination;
    }

    public String getMessageSelector() throws JMSException
    {
        checkPreConditions();

        return _messageSelector;
    }

    public MessageListener getMessageListener() throws JMSException
    {
        checkPreConditions();

        return _messageListener.get();
    }

    public int getAcknowledgeMode()
    {
        return _acknowledgeMode;
    }

    protected boolean isMessageListenerSet()
    {
        return _messageListener.get() != null;
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        checkPreConditions();

        // if the current listener is non-null and the session is not stopped, then
        // it is an error to call this method.

        // i.e. it is only valid to call this method if
        //
        // (a) the connection is stopped, in which case the dispatcher is not running
        // OR
        // (b) the listener is null AND we are not receiving synchronously at present
        //

        if (!_session.getAMQConnection().started())
        {
            _messageListener.set(messageListener);
            _session.setHasMessageListeners();

            if (_logger.isDebugEnabled())
            {
                _logger.debug(
                        "Session stopped : Message listener(" + messageListener + ") set for destination " + _destination);
            }
        }
        else
        {
            if (_receiving.get())
            {
                throw new javax.jms.IllegalStateException("Another thread is already receiving synchronously.");
            }

            if (!_messageListener.compareAndSet(null, messageListener))
            {
                throw new javax.jms.IllegalStateException("Attempt to alter listener while session is started.");
            }

            _logger.debug("Message listener set for destination " + _destination);

            if (messageListener != null)
            {
                // handle case where connection has already been started, and the dispatcher has alreaded started
                // putting values on the _synchronousQueue

                synchronized (_session)
                {
                    _messageListener.set(messageListener);
                    _session.setHasMessageListeners();
                    _session.startDistpatcherIfNecessary();
                }
            }
        }
    }

    protected void preApplicationProcessing(AbstractJMSMessage jmsMsg) throws JMSException
    {
        if (_session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            _session.addUnacknowledgedMessage(jmsMsg.getDeliveryTag());
        }

        _session.setInRecovery(false);
    }

    private void acquireReceiving() throws JMSException
    {
        if (!_receiving.compareAndSet(false, true))
        {
            throw new javax.jms.IllegalStateException("Another thread is already receiving.");
        }

        if (isMessageListenerSet())
        {
            throw new javax.jms.IllegalStateException("A listener has already been set.");
        }

        _receivingThread = Thread.currentThread();
    }

    private void releaseReceiving()
    {
        _receiving.set(false);
        _receivingThread = null;
    }

    public FieldTable getRawSelectorFieldTable()
    {
        return _rawSelectorFieldTable;
    }

    public int getPrefetch()
    {
        return _prefetchHigh;
    }

    public int getPrefetchHigh()
    {
        return _prefetchHigh;
    }

    public int getPrefetchLow()
    {
        return _prefetchLow;
    }

    public boolean isNoLocal()
    {
        return _noLocal;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }

    public boolean isReceiving()
    {
        return _receiving.get();
    }

    public Message receive() throws JMSException
    {
        return receive(0);
    }

    public Message receive(long l) throws JMSException
    {

        checkPreConditions();

        acquireReceiving();

        _session.startDistpatcherIfNecessary();

        try
        {
            if (closeOnAutoClose())
            {
                return null;
            }

            Object o = getMessageFromQueue(l);

            final AbstractJMSMessage m = returnMessageOrThrow(o);
            if (m != null)
            {
                preApplicationProcessing(m);
                postDeliver(m);
            }

            return m;
        }
        catch (InterruptedException e)
        {
            _logger.warn("Interrupted: " + e);

            return null;
        }
        finally
        {
            releaseReceiving();
        }
    }

    public  Object getMessageFromQueue(long l) throws InterruptedException
    {
         Object o;
         if (l > 0)
         {
             o = _synchronousQueue.poll(l, TimeUnit.MILLISECONDS);
         }
         else if (l < 0)
         {
             o = _synchronousQueue.poll();
         }
         else
         {
             o = _synchronousQueue.take();
         }
         return o;
     }

    private boolean closeOnAutoClose() throws JMSException
    {
        if (isAutoClose() && _closeWhenNoMessages && _synchronousQueue.isEmpty())
        {
            close(false);

            return true;
        }
        else
        {
            return false;
        }
    }

    public Message receiveNoWait() throws JMSException
    {
        checkPreConditions();

        acquireReceiving();

        _session.startDistpatcherIfNecessary();

        try
        {
            if (closeOnAutoClose())
            {
                return null;
            }

            Object o = getMessageFromQueue(-1);
            final AbstractJMSMessage m = returnMessageOrThrow(o);
            if (m != null)
            {
                preApplicationProcessing(m);
                postDeliver(m);
            }

            return m;
        }
        catch (InterruptedException e)
        {
            _logger.warn("Interrupted: " + e);

            return null;
        }
        finally
        {
            releaseReceiving();
        }
    }

    /**
     * We can get back either a Message or an exception from the queue. This method examines the argument and deals with
     * it by throwing it (if an exception) or returning it (in any other case).
     *
     * @param o
     * @return a message only if o is a Message
     * @throws JMSException if the argument is a throwable. If it is a JMSException it is rethrown as is, but if not a
     *                      JMSException is created with the linked exception set appropriately
     */
    private AbstractJMSMessage returnMessageOrThrow(Object o) throws JMSException
    {
        // errors are passed via the queue too since there is no way of interrupting the poll() via the API.
        if (o instanceof Throwable)
        {
            JMSException e = new JMSException("Message consumer forcibly closed due to error: " + o);
            if (o instanceof Exception)
            {
                e.setLinkedException((Exception) o);
            }

            throw e;
        }
        else
        {
            return (AbstractJMSMessage) o;
        }
    }

    public void close() throws JMSException
    {
        close(true);
    }

    public void close(boolean sendClose) throws JMSException
    {
        // synchronized (_closed)

        if (_logger.isInfoEnabled())
        {
            _logger.info("Closing consumer:" + debugIdentity());
        }

        synchronized (_connection.getFailoverMutex())
        {
            if (!_closed.getAndSet(true))
            {
                if (_logger.isTraceEnabled())
                {
                    if (_closedStack != null)
                    {
                        _logger.trace(_consumerTag + " close():" + Arrays.asList(Thread.currentThread().getStackTrace())
                                .subList(3, 6));
                        _logger.trace(_consumerTag + " previously:" + _closedStack.toString());
                    }
                    else
                    {
                        _closedStack = Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 6);
                    }
                }

                if (sendClose)
                {
                    // TODO: Be aware of possible changes to parameter order as versions change.
                    sendCancel();
                }
                else
                {
                    // //fixme this probably is not right
                    // if (!isNoConsume())
                    //{ // done in BasicCancelOK Handler but not sending one so just deregister.
                    //    deregisterConsumer();
                    //}
                }

                deregisterConsumer();

                if (_messageListener != null && _receiving.get() && _receivingThread != null)
                {
                    if (_logger.isInfoEnabled())
                    {
                        _logger.info("Interrupting thread: " + _receivingThread);
                    }

                    _receivingThread.interrupt();
                }
            }
        }
    }

    public abstract void sendCancel() throws JMSAMQException;

    /**
     * Called when you need to invalidate a consumer. Used for example when failover has occurred and the client has
     * vetoed automatic resubscription. The caller must hold the failover mutex.
     */
    void markClosed()
    {
        // synchronized (_closed)
        {
            _closed.set(true);

            if (_logger.isTraceEnabled())
            {
                if (_closedStack != null)
                {
                    _logger.trace(_consumerTag + " markClosed():" + Arrays
                            .asList(Thread.currentThread().getStackTrace()).subList(3, 8));
                    _logger.trace(_consumerTag + " previously:" + _closedStack.toString());
                }
                else
                {
                    _closedStack = Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 8);
                }
            }
        }

        deregisterConsumer();
    }

    /**
     * Called from the AMQSession when a message has arrived for this consumer. This methods handles both the case of a
     * message listener or a synchronous receive() caller.
     *
     * @param messageFrame the raw unprocessed mesage
     * @param channelId    channel on which this message was sent
     */
    void notifyMessage(UnprocessedMessage messageFrame, int channelId)
    {
        final boolean debug = _logger.isDebugEnabled();

        if (debug)
        {
            _logger.debug("notifyMessage called with message number " + messageFrame.getDeliveryTag());
        }

        try
        {
            AbstractJMSMessage jmsMessage = createJMSMessageFromUnprocessedMessage(messageFrame);
            if (debug)
            {
                _logger.debug("Message is of type: " + jmsMessage.getClass().getName());
            }
            // synchronized (_closed)

            {
                // if (!_closed.get())
                {

                    jmsMessage.setConsumer(this);

                    preDeliver(jmsMessage);

                    notifyMessage(jmsMessage, channelId);
                }
                // else
                // {
                // _logger.error("MESSAGE REJECTING!");
                // _session.rejectMessage(jmsMessage, true);
                // //_logger.error("MESSAGE JUST DROPPED!");
                // }
            }
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                _logger.info("SynchronousQueue.put interupted. Usually result of connection closing");
            }
            else
            {
                _logger.error("Caught exception (dump follows) - ignoring...", e);
            }
        }
    }

    public abstract AbstractJMSMessage createJMSMessageFromUnprocessedMessage(UnprocessedMessage<H, B> messageFrame)
            throws Exception;


    /**
     * @param jmsMessage this message has already been processed so can't redo preDeliver
     * @param channelId
     */
    public void notifyMessage(AbstractJMSMessage jmsMessage, int channelId)
    {
        try
        {
            if (isMessageListenerSet())
            {
                // we do not need a lock around the test above, and the dispatch below as it is invalid
                // for an application to alter an installed listener while the session is started
                // synchronized (_closed)
                {
                    // if (!_closed.get())
                    {

                        preApplicationProcessing(jmsMessage);
                        getMessageListener().onMessage(jmsMessage);
                        postDeliver(jmsMessage);
                    }
                }
            }
            else
            {
                // we should not be allowed to add a message is the
                // consumer is closed
                _synchronousQueue.put(jmsMessage);
            }
        }
        catch (Exception e)
        {
            if (e instanceof InterruptedException)
            {
                _logger.info("reNotification : SynchronousQueue.put interupted. Usually result of connection closing");
            }
            else
            {
                _logger.error("reNotification : Caught exception (dump follows) - ignoring...", e);
            }
        }
    }

    void preDeliver(AbstractJMSMessage msg)
    {
        switch (_acknowledgeMode)
        {

            case Session.PRE_ACKNOWLEDGE:
                _session.acknowledgeMessage(msg.getDeliveryTag(), false);
                break;

            case Session.CLIENT_ACKNOWLEDGE:
                // we set the session so that when the user calls acknowledge() it can call the method on session
                // to send out the appropriate frame
                msg.setAMQSession(_session);
                break;
        }
    }

    void postDeliver(AbstractJMSMessage msg) throws JMSException
    {
        msg.setJMSDestination(_destination);
        switch (_acknowledgeMode)
        {

            case Session.CLIENT_ACKNOWLEDGE:
                if (isNoConsume())
                {
                    _session.acknowledgeMessage(msg.getDeliveryTag(), false);
                }

                break;

            case Session.DUPS_OK_ACKNOWLEDGE:
                if (++_outstanding >= _prefetchHigh)
                {
                    _dups_ok_acknowledge_send = true;
                }

                if (_outstanding <= _prefetchLow)
                {
                    _dups_ok_acknowledge_send = false;
                }

                if (_dups_ok_acknowledge_send)
                {
                    if (!_session.isInRecovery())
                    {
                        _session.acknowledgeMessage(msg.getDeliveryTag(), true);
                    }
                }

                break;

            case Session.AUTO_ACKNOWLEDGE:
                // we do not auto ack a message if the application code called recover()
                if (!_session.isInRecovery())
                {
                    _session.acknowledgeMessage(msg.getDeliveryTag(), false);
                }

                break;

            case Session.SESSION_TRANSACTED:
                if (isNoConsume())
                {
                    _session.acknowledgeMessage(msg.getDeliveryTag(), false);
                }
                else
                {
                    _session.addDeliveredMessage(msg.getDeliveryTag());
                }

                break;
        }
    }

    void notifyError(Throwable cause)
    {
        // synchronized (_closed)
        {
            _closed.set(true);
            if (_logger.isTraceEnabled())
            {
                if (_closedStack != null)
                {
                    _logger.trace(_consumerTag + " notifyError():" + Arrays
                            .asList(Thread.currentThread().getStackTrace()).subList(3, 8));
                    _logger.trace(_consumerTag + " previously" + _closedStack.toString());
                }
                else
                {
                    _closedStack = Arrays.asList(Thread.currentThread().getStackTrace()).subList(3, 8);
                }
            }
        }
        // QPID-293 can "request redelivery of this error through dispatcher"

        // we have no way of propagating the exception to a message listener - a JMS limitation - so we
        // deal with the case where we have a synchronous receive() waiting for a message to arrive
        if (!isMessageListenerSet())
        {
            // offer only succeeds if there is a thread waiting for an item from the queue
            if (_synchronousQueue.offer(cause))
            {
                _logger.debug("Passed exception to synchronous queue for propagation to receive()");
            }
        }

        deregisterConsumer();
    }

    /**
     * Perform cleanup to deregister this consumer. This occurs when closing the consumer in both the clean case and in
     * the case of an error occurring.
     */
    private void deregisterConsumer()
    {
        _session.deregisterConsumer(this);
    }

    public AMQShortString getConsumerTag()
    {
        return _consumerTag;
    }

    public void setConsumerTag(AMQShortString consumerTag)
    {
        _consumerTag = consumerTag;
    }

    public AMQSession getSession()
    {
        return _session;
    }

    private void checkPreConditions() throws JMSException
    {

        this.checkNotClosed();

        if ((_session == null) || _session.isClosed())
        {
            throw new javax.jms.IllegalStateException("Invalid Session");
        }
    }

    public boolean isAutoClose()
    {
        return _autoClose;
    }

    public boolean isNoConsume()
    {
        return _noConsume;
    }

    public void closeWhenNoMessages(boolean b)
    {
        _closeWhenNoMessages = b;

        if (_closeWhenNoMessages && _synchronousQueue.isEmpty() && _receiving.get() && (_messageListener != null))
        {
            _receivingThread.interrupt();
        }

    }

    public void rollback()
    {
        // rollback pending messages
        if (_synchronousQueue.size() > 0)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rejecting the messages(" + _synchronousQueue
                        .size() + ") in _syncQueue (PRQ)" + "for consumer with tag:" + _consumerTag);
            }

            Iterator iterator = _synchronousQueue.iterator();

            while (iterator.hasNext())
            {

                Object o = iterator.next();
                if (o instanceof AbstractJMSMessage)
                {
                    _session.rejectMessage(((AbstractJMSMessage) o), true);

                    if (_logger.isTraceEnabled())
                    {
                        _logger.trace("Rejected message:" + ((AbstractJMSMessage) o).getDeliveryTag());
                    }

                    iterator.remove();

                }
                else
                {
                    _logger.error("Queue contained a :" + o
                            .getClass() + " unable to reject as it is not an AbstractJMSMessage. Will be cleared");
                    iterator.remove();
                }
            }

            if (_synchronousQueue.size() != 0)
            {
                _logger.warn("Queue was not empty after rejecting all messages Remaining:" + _synchronousQueue.size());
                rollback();
            }

            clearReceiveQueue();
        }
    }

    public String debugIdentity()
    {
        return String.valueOf(_consumerTag);
    }

    public void clearReceiveQueue()
    {
        _synchronousQueue.clear();
    }


    public void start()
    {
        // do nothing as this is a 0_10 feature
    }


    public void stop()
    {
        // do nothing as this is a 0_10 feature
    }

    public boolean isStrated()
    {
        // do nothing as this is a 0_10 feature
        return false;
    }

    public AMQShortString getQueuename()
    {
        return _queuename;
    }

    public void setQueuename(AMQShortString queuename)
    {
        this._queuename = queuename;
    }

    public void addBindingKey(AMQDestination amqd, String routingKey) throws AMQException
    {
        _session.addBindingKey(this,amqd,routingKey);
    }
}
