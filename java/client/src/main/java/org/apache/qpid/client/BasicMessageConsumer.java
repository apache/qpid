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

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.framing.*;
import org.apache.qpid.jms.MessageConsumer;
import org.apache.qpid.jms.Session;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.jms.Destination;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class BasicMessageConsumer extends Closeable implements MessageConsumer
{
    private static final Logger _logger = Logger.getLogger(BasicMessageConsumer.class);

    /**
     * The connection being used by this consumer
     */
    private AMQConnection _connection;

    private String _messageSelector;

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
     * The consumer tag allows us to close the consumer by sending a jmsCancel method to the
     * broker
     */
    private AMQShortString _consumerTag;

    /**
     * We need to know the channel id when constructing frames
     */
    private int _channelId;

    /**
     * Used in the blocking receive methods to receive a message from
     * the Session thread.
     * <p/>
     * Or to notify of errors
     * <p/>
     * Argument true indicates we want strict FIFO semantics
     */
    private final ArrayBlockingQueue _synchronousQueue;

    private MessageFactoryRegistry _messageFactory;

    private final AMQSession _session;

    private AMQProtocolHandler _protocolHandler;

    /**
     * We need to store the "raw" field table so that we can resubscribe in the event of failover being required
     */
    private FieldTable _rawSelectorFieldTable;

    /**
     * We store the high water prefetch field in order to be able to reuse it when resubscribing in the event of failover
     */
    private int _prefetchHigh;

    /**
     * We store the low water prefetch field in order to be able to reuse it when resubscribing in the event of failover
     */
    private int _prefetchLow;

    /**
     * We store the exclusive field in order to be able to reuse it when resubscribing in the event of failover
     */
    private boolean _exclusive;

    /**
     * The acknowledge mode in force for this consumer. Note that the AMQP protocol allows different ack modes
     * per consumer whereas JMS defines this at the session level, hence why we associate it with the consumer in our
     * implementation.
     */
    private int _acknowledgeMode;

    /**
     * Number of messages unacknowledged in DUPS_OK_ACKNOWLEDGE mode
     */
    private int _outstanding;

    /**
     * Tag of last message delievered, whoch should be acknowledged on commit in
     * transaction mode.
     */
    private long _lastDeliveryTag;

    /**
     * Switch to enable sending of acknowledgements when using DUPS_OK_ACKNOWLEDGE mode.
     * Enabled when _outstannding number of msgs >= _prefetchHigh and disabled at < _prefetchLow
     */
    private boolean _dups_ok_acknowledge_send;

    private ConcurrentLinkedQueue<Long> _unacknowledgedDeliveryTags = new ConcurrentLinkedQueue<Long>();

    /**
     * The thread that was used to call receive(). This is important for being able to interrupt that thread if
     * a receive() is in progress.
     */
    private Thread _receivingThread;

    /**
     * autoClose denotes that the consumer will automatically cancel itself when there are no more messages to receive
     * on the queue.  This is used for queue browsing.
     */
    private boolean _autoClose;
    private boolean _closeWhenNoMessages;

    private boolean _noConsume;

    protected BasicMessageConsumer(int channelId, AMQConnection connection, AMQDestination destination, String messageSelector,
                                   boolean noLocal, MessageFactoryRegistry messageFactory, AMQSession session,
                                   AMQProtocolHandler protocolHandler, FieldTable rawSelectorFieldTable,
                                   int prefetchHigh, int prefetchLow, boolean exclusive, int acknowledgeMode, boolean noConsume, boolean autoClose)
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

    private boolean isMessageListenerSet()
    {
        return _messageListener.get() != null;
    }

    public void setMessageListener(final MessageListener messageListener) throws JMSException
    {
        checkPreConditions();

        //if the current listener is non-null and the session is not stopped, then
        //it is an error to call this method.

        //i.e. it is only valid to call this method if
        //
        //    (a) the session is stopped, in which case the dispatcher is not running
        //    OR
        //    (b) the listener is null AND we are not receiving synchronously at present
        //

        if (_session.isStopped())
        {
            _messageListener.set(messageListener);
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Session stopped : Message listener(" + messageListener + ") set for destination " + _destination);
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
                //handle case where connection has already been started, and the dispatcher has alreaded started
                // putting values on the _synchronousQueue

                synchronized (_session)
                {
                    //Pause Dispatcher
                    _session.doDispatcherTask(new DispatcherCallback(this)
                    {
                        public void whilePaused(Queue<MessageConsumerPair> reprocessQueue)
                        {
                            // Prepend messages in _synchronousQueue to dispatcher queue
                            _logger.debug("ReprocessQueue current size:" + reprocessQueue.size());
                            for (Object item : _synchronousQueue)
                            {
                                reprocessQueue.offer(new MessageConsumerPair(_consumer, item));
                            }
                            _logger.debug("Added items to reprocessQueue:" + reprocessQueue.size());

                            // Set Message Listener
                            _logger.debug("Set Message Listener");
                            _messageListener.set(messageListener);
                        }
                    }
                    );
                }
            }
        }
    }

    private void preApplicationProcessing(AbstractJMSMessage jmsMsg) throws JMSException
    {
        byte[] url = jmsMsg.getBytesProperty(CustomJMSXProperty.JMSX_QPID_JMSDESTINATIONURL.getShortStringName());
        Destination dest = AMQDestination.createDestination(url);
        jmsMsg.setJMSDestination(dest);

        if (_session.getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            _unacknowledgedDeliveryTags.add(jmsMsg.getDeliveryTag());
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

        try
        {
            if (closeOnAutoClose())
            {
                return null;
            }
            Object o = null;
            if (l > 0)
            {
                o = _synchronousQueue.poll(l, TimeUnit.MILLISECONDS);
            }
            else
            {
                o = _synchronousQueue.take();
            }
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

        try
        {
            if (closeOnAutoClose())
            {
                return null;
            }
            Object o = _synchronousQueue.poll();
            final AbstractJMSMessage m = returnMessageOrThrow(o);
            if (m != null)
            {
                preApplicationProcessing(m);
                postDeliver(m);
            }

            return m;
        }
        finally
        {
            releaseReceiving();
        }
    }

    /**
     * We can get back either a Message or an exception from the queue. This method examines the argument and deals
     * with it by throwing it (if an exception) or returning it (in any other case).
     *
     * @param o
     * @return a message only if o is a Message
     * @throws JMSException if the argument is a throwable. If it is a JMSException it is rethrown as is, but if not
     *                      a JMSException is created with the linked exception set appropriately
     */
    private AbstractJMSMessage returnMessageOrThrow(Object o)
            throws JMSException
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
        synchronized (_connection.getFailoverMutex())
        {
            if (!_closed.getAndSet(true))
            {
                if (sendClose)
                {
                    // TODO: Be aware of possible changes to parameter order as versions change.
                    final AMQFrame cancelFrame = BasicCancelBody.createAMQFrame(_channelId,
                                                                                _protocolHandler.getProtocolMajorVersion(),
                                                                                _protocolHandler.getProtocolMinorVersion(),
                                                                                _consumerTag,    // consumerTag
                                                                                false);    // nowait

                    try
                    {
                        _protocolHandler.syncWrite(cancelFrame, BasicCancelOkBody.class);
                    }
                    catch (AMQException e)
                    {
                        _logger.error("Error closing consumer: " + e, e);
                        throw new JMSException("Error closing consumer: " + e);
                    }
                }

                deregisterConsumer();
                _unacknowledgedDeliveryTags.clear();
                if (_messageListener != null && _receiving.get())
                {
                    _logger.info("Interrupting thread: " + _receivingThread);
                    _receivingThread.interrupt();
                }
            }
        }
    }

    /**
     * Called when you need to invalidate a consumer. Used for example when failover has occurred and the
     * client has vetoed automatic resubscription.
     * The caller must hold the failover mutex.
     */
    void markClosed()
    {
        _closed.set(true);
        deregisterConsumer();
    }

    /**
     * Called from the AMQSession when a message has arrived for this consumer. This methods handles both the case
     * of a message listener or a synchronous receive() caller.
     *
     * @param messageFrame the raw unprocessed mesage
     * @param channelId    channel on which this message was sent
     */
    void notifyMessage(UnprocessedMessage messageFrame, int channelId)
    {
        final boolean debug = _logger.isDebugEnabled();

        if (debug)
        {
            _logger.debug("notifyMessage called with message number " + messageFrame.deliverBody.deliveryTag);
        }
        try
        {
            AbstractJMSMessage jmsMessage = _messageFactory.createMessage(messageFrame.deliverBody.deliveryTag,
                                                                          messageFrame.deliverBody.redelivered,
                                                                          messageFrame.contentHeader,
                                                                          messageFrame.bodies);

            if (debug)
            {
                _logger.debug("Message is of type: " + jmsMessage.getClass().getName());
            }
            jmsMessage.setConsumer(this);

            preDeliver(jmsMessage);

            notifyMessage(jmsMessage, channelId);
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
                //we do not need a lock around the test above, and the dispatch below as it is invalid
                //for an application to alter an installed listener while the session is started
                preApplicationProcessing(jmsMessage);
                getMessageListener().onMessage(jmsMessage);
                postDeliver(jmsMessage);
            }
            else
            {
                //This shouldn't be possible.
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

    private void preDeliver(AbstractJMSMessage msg)
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

    private void postDeliver(AbstractJMSMessage msg) throws JMSException
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
                    _lastDeliveryTag = msg.getDeliveryTag();
                }
                break;
        }
    }

    /**
     * Acknowledge up to last message delivered (if any). Used when commiting.
     */
    void acknowledgeLastDelivered()
    {
        if (_lastDeliveryTag > 0)
        {
            _session.acknowledgeMessage(_lastDeliveryTag, true);
            _lastDeliveryTag = -1;
        }
    }

    void notifyError(Throwable cause)
    {
        _closed.set(true);

        //QPID-293 can "request redelivery of this error through dispatcher"

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
     * Perform cleanup to deregister this consumer. This occurs when closing the consumer in both the clean
     * case and in the case of an error occurring.
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

        if (_session == null || _session.isClosed())
        {
            throw new javax.jms.IllegalStateException("Invalid Session");
        }
    }

    public void acknowledge() throws JMSException
    {
        if (!isClosed())
        {

            Iterator<Long> tags = _unacknowledgedDeliveryTags.iterator();
            while (tags.hasNext())
            {
                _session.acknowledgeMessage(tags.next(), false);
                tags.remove();
            }
        }
        else
        {
            throw new IllegalStateException("Consumer is closed");
        }
    }

    /**
     * Called on recovery to reset the list of delivery tags
     */
    public void clearUnackedMessages()
    {
        _unacknowledgedDeliveryTags.clear();
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

        if (_closeWhenNoMessages
            && _synchronousQueue.isEmpty()
            && _receiving.get()
            && _messageListener != null)
        {
            _receivingThread.interrupt();
        }

    }
}
