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
package org.apache.qpidity.njms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpidity.njms.message.*;
import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.transport.RangeSet;

import javax.jms.*;
import javax.jms.IllegalStateException;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of the JMS Session interface
 */
public class SessionImpl implements Session
{
    /**
     * this session's logger
     */
    private static final Logger _logger = LoggerFactory.getLogger(SessionImpl.class);

    /**
     * A queue for incoming asynch messages.
     */
    private final LinkedList<IncomingMessage> _incomingAsynchronousMessages = new LinkedList<IncomingMessage>();

    //--- MessageDispatcherThread and Session locking
    /**
     * indicates that the MessageDispatcherThread has stopped
     */
    private boolean _hasStopped = false;

    /**
     * lock for the MessageDispatcherThread to wait until the session is stopped
     */
    private final Object _stoppingLock = new Object();

    /**
     * lock for the stopper thread to wait on when the MessageDispatcherThread is stopping
     */
    private final Object _stoppingJoin = new Object();

    /**
     * thread to dispatch messages to async consumers
     */
    private MessageDispatcherThread _messageDispatcherThread = null;
    //----END

    /**
     * The messageActors of this session.
     */
    private final HashMap<String, MessageActor> _messageActors = new HashMap<String, MessageActor>();

    /**
     * All the not yet acknoledged messages
     */
    private final ArrayList<QpidMessage> _unacknowledgedMessages = new ArrayList<QpidMessage>();

    /**
     * Indicates whether this session is closed.
     */
    private boolean _isClosed = false;

    /**
     * Indicates whether this session is closing.
     */
    private boolean _isClosing = false;

    /**
     * Indicates whether this session is stopped.
     */
    private boolean _isStopped = false;

    /**
     * Used to indicate whether or not this is a transactional session.
     */
    private boolean _transacted;

    /**
     * Holds the sessions acknowledgement mode.
     */
    private int _acknowledgeMode;

    /**
     * The underlying QpidSession
     */
    private org.apache.qpidity.nclient.Session _qpidSession;

    /**
     * The latest qpid Exception that has been reaised.
     */
    private QpidException _currentException;

    /**
     * Indicates whether this session is recovering
     */
    private boolean _inRecovery = false;

    /**
     * This session connection
     */
    private ConnectionImpl _connection;

    /**
     * This will be used as the message actor id
     * This in turn will be set as the destination
     */
    protected AtomicInteger _consumerTag = new AtomicInteger();

    //--- Constructor
    /**
     * Create a JMS Session
     *
     * @param connection      The ConnectionImpl object from which the Session is created.
     * @param transacted      Indicates if the session transacted.
     * @param acknowledgeMode The session's acknowledgement mode. This value is ignored and set to
     *                        {@link Session#SESSION_TRANSACTED} if the <code>transacted</code> parameter is true.
     * @param isXA            Indicates whether this session is an XA session.
     * @throws QpidException In case of internal error.
     */
    protected SessionImpl(ConnectionImpl connection, boolean transacted, int acknowledgeMode, boolean isXA)
            throws QpidException
    {
        _connection = connection;
        _transacted = transacted;
        // for transacted sessions we ignore the acknowledgeMode and use GenericAckMode.SESSION_TRANSACTED
        if (_transacted)
        {
            acknowledgeMode = Session.SESSION_TRANSACTED;
        }
        _acknowledgeMode = acknowledgeMode;

        // create the qpid session with an expiry  <= 0 so that the session does not expire
        _qpidSession = _connection.getQpidConnection().createSession(0);
        // set the exception listnere for this session
        _qpidSession.setClosedListener(new QpidSessionExceptionListener());
        // set transacted if required
        if (_transacted && !isXA)
        {
            _qpidSession.txSelect();
        }
        testQpidException();
        // init the message dispatcher.
        initMessageDispatcherThread();
    }

    //--- javax.njms.Session API
    /**
     * Creates a <CODE>BytesMessage</CODE> object used to send a message
     * containing a stream of uninterpreted bytes.
     *
     * @return A BytesMessage.
     * @throws JMSException If Creating a BytesMessage object fails due to some internal error.
     */
    public BytesMessage createBytesMessage() throws JMSException
    {
        checkNotClosed();
        return new BytesMessageImpl();
    }

    /**
     * Creates a <CODE>MapMessage</CODE> object used to send a self-defining set
     * of name-value pairs, where names are Strings and values are primitive values.
     *
     * @return A MapMessage.
     * @throws JMSException If Creating a MapMessage object fails due to some internal error.
     */
    public MapMessage createMapMessage() throws JMSException
    {
        checkNotClosed();
        return new MapMessageImpl();
    }

    /**
     * Creates a <code>Message</code> object that holds all the standard message header information.
     * It can be sent when a message containing only header information is sufficient.
     * We simply return a ByteMessage
     *
     * @return A Message.
     * @throws JMSException If Creating a Message object fails due to some internal error.
     */
    public Message createMessage() throws JMSException
    {
        return new MessageImpl();
    }

    /**
     * Creates an <code>ObjectMessage</code> used to send a message
     * that contains a serializable Java object.
     *
     * @return An ObjectMessage.
     * @throws JMSException If Creating an ObjectMessage object fails due to some internal error.
     */
    public ObjectMessage createObjectMessage() throws JMSException
    {
        checkNotClosed();
        return new ObjectMessageImpl();
    }

    /**
     * Creates an initialized <code>ObjectMessage</code> used to send a message that contains
     * a serializable Java object.
     *
     * @param serializable The object to use to initialize this message.
     * @return An initialised ObjectMessage.
     * @throws JMSException If Creating an initialised ObjectMessage object fails due to some internal error.
     */
    public ObjectMessage createObjectMessage(Serializable serializable) throws JMSException
    {
        ObjectMessage msg = createObjectMessage();
        msg.setObject(serializable);
        return msg;
    }

    /**
     * Creates a <code>StreamMessage</code>  used to send a
     * self-defining stream of primitive values in the Java programming
     * language.
     *
     * @return A StreamMessage
     * @throws JMSException If Creating an StreamMessage object fails due to some internal error.
     */
    public StreamMessage createStreamMessage() throws JMSException
    {
        checkNotClosed();
        return new StreamMessageImpl();
    }

    /**
     * Creates a <code>TextMessage</code> object used to send a message containing a String.
     *
     * @return A TextMessage object
     * @throws JMSException If Creating an TextMessage object fails due to some internal error.
     */
    public TextMessage createTextMessage() throws JMSException
    {
        checkNotClosed();
        return new TextMessageImpl();
    }

    /**
     * Creates an initialized <code>TextMessage</code>  used to send
     * a message containing a String.
     *
     * @param text The string used to initialize this message.
     * @return An initialized TextMessage
     * @throws JMSException If Creating an initialised TextMessage object fails due to some internal error.
     */
    public TextMessage createTextMessage(String text) throws JMSException
    {
        TextMessage msg = createTextMessage();
        msg.setText(text);
        return msg;
    }

    /**
     * Indicates whether the session is in transacted mode.
     *
     * @return true if the session is in transacted mode
     * @throws JMSException If geting the transaction mode fails due to some internal error.
     */
    public boolean getTransacted() throws JMSException
    {
        checkNotClosed();
        return _transacted;
    }

    /**
     * Returns the acknowledgement mode of this session.
     * <p> The acknowledgement mode is set at the time that the session is created.
     * If the session is transacted, the acknowledgement mode is ignored.
     *
     * @return If the session is not transacted, returns the current acknowledgement mode for the session.
     *         else returns SESSION_TRANSACTED.
     * @throws JMSException if geting the acknowledgement mode fails due to some internal error.
     */
    public int getAcknowledgeMode() throws JMSException
    {
        checkNotClosed();
        return _acknowledgeMode;
    }

    /**
     * Commits all messages done in this transaction.
     *
     * @throws JMSException                   If committing the transaction fails due to some internal error.
     * @throws TransactionRolledBackException If the transaction is rolled back due to some internal error during commit.
     * @throws javax.jms.IllegalStateException
     *                                        If the method is not called by a transacted session.
     */
    public void commit() throws JMSException
    {
        checkNotClosed();
        //make sure the Session is a transacted one
        if (!_transacted)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Cannot commit non-transacted session, throwing IllegalStateException");
            }
            throw new IllegalStateException("Cannot commit non-transacted session", "Session is not transacted");
        }
        // commit the underlying Qpid Session
        _qpidSession.txCommit();
        try
        {
            testQpidException();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Rolls back any messages done in this transaction.
     *
     * @throws JMSException If rolling back the session fails due to some internal error.
     * @throws javax.jms.IllegalStateException
     *                      If the method is not called by a transacted session.
     */
    public void rollback() throws JMSException
    {
        checkNotClosed();
        //make sure the Session is a transacted one
        if (!_transacted)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Cannot rollback non-transacted session, throwing IllegalStateException");
            }
            throw new IllegalStateException("Cannot rollback non-transacted session", "Session is not transacted");
        }
        // rollback the underlying Qpid Session
        _qpidSession.txRollback();
        try
        {
            testQpidException();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Closes this session.
     * <p> The JMS specification says
     * <P> This call will block until a <code>receive</code> call or message
     * listener in progress has completed. A blocked message consumer
     * <code>receive</code> call returns <code>null</code> when this session is closed.
     * <P>Closing a transacted session must roll back the transaction in progress.
     * <P>This method is the only <code>Session</code> method that can be called concurrently.
     * <P>Invoking any other <code>Session</code> method on a closed session
     * must throw a <code>javax.njms.IllegalStateException</code>.
     * <p> Closing a closed session must <I>not</I> throw an exception.
     *
     * @throws JMSException If closing the session fails due to some internal error.
     */
    public synchronized void close() throws JMSException
    {
        if (!_isClosed)
        {
            _messageDispatcherThread.interrupt();
            if (!_isClosing)
            {
                _isClosing = true;
                // if the session is stopped then restart it before notifying on the lock
                // that will stop the sessionThread
                if (_isStopped)
                {
                    startDispatchThread();
                }
                //notify the sessionThread
                synchronized (_incomingAsynchronousMessages)
                {
                    _incomingAsynchronousMessages.notifyAll();
                }

                try
                {
                    _messageDispatcherThread.join();
                    _messageDispatcherThread = null;
                }
                catch (InterruptedException ie)
                {
                    /* ignore */
                }
            }
            // from now all the session methods will throw a IllegalStateException
            _isClosed = true;
            // close all the actors
            closeAllMessageActors();
            _messageActors.clear();
            // We may have a thread trying to add a message
            synchronized (_incomingAsynchronousMessages)
            {
                _incomingAsynchronousMessages.clear();
                _incomingAsynchronousMessages.notifyAll();
            }
            // close the underlaying QpidSession
            _qpidSession.sessionClose();
            try
            {
                testQpidException();
            }
            catch (QpidException e)
            {
                throw ExceptionHelper.convertQpidExceptionToJMSException(e);
            }
        }
    }

    /**
     * Stops message delivery in this session, and restarts message delivery with
     * the oldest unacknowledged message.
     * <p>Recovering a session causes it to take the following actions:
     * <ul>
     * <li>Stop message delivery.
     * <li>Mark all messages that might have been delivered but not acknowledged as "redelivered".
     * <li>Restart the delivery sequence including all unacknowledged messages that had been
     * previously delivered.
     * Redelivered messages do not have to be delivered in exactly their original delivery order.
     * </ul>
     *
     * @throws JMSException If the JMS provider fails to stop and restart message delivery due to some internal error.
     *                      Not that this does not necessarily mean that the recovery has failed, but simply that it is
     *                      not possible to tell if it has or not.
     */
    public void recover() throws JMSException
    {
        // Ensure that the session is open.
        checkNotClosed();
        // we are recovering
        _inRecovery = true;
        // Ensure that the session is not transacted.
        if (getTransacted())
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Trying to recover a transacted Session, throwing IllegalStateException");
            }
            throw new IllegalStateException("Session is transacted");
        }
        // release all unack messages
        RangeSet ranges = new RangeSet();
        for (QpidMessage message : _unacknowledgedMessages)
        {
            // release this message
            ranges.add(message.getMessageTransferId());
        }
        getQpidSession().messageRelease(ranges);
    }

    /**
     * Returns the session's distinguished message listener (optional).
     * <p>This is an expert facility used only by Application Servers.
     * <p> This is an optional operation that is not yet supported
     *
     * @return The message listener associated with this session.
     * @throws JMSException If getting the message listener fails due to an internal error.
     */
    public MessageListener getMessageListener() throws JMSException
    {
        checkNotClosed();
        if (_logger.isDebugEnabled())
        {
            _logger.debug(
                    "Getting session's distinguished message listener, not supported," + " throwing UnsupportedOperationException");
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the session's distinguished message listener.
     * <p>This is an expert facility used only by Application Servers.
     * <p> This is an optional operation that is not yet supported
     *
     * @param messageListener The message listener to associate with this session
     * @throws JMSException If setting the message listener fails due to an internal error.
     */
    public void setMessageListener(MessageListener messageListener) throws JMSException
    {
        checkNotClosed();
        if (_logger.isDebugEnabled())
        {
            _logger.debug(
                    "Setting the session's distinguished message listener, not supported," + " throwing UnsupportedOperationException");
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Optional operation, intended to be used only by Application Servers,
     * not by ordinary JMS clients.
     * <p> This is an optional operation that is not yet supported
     */
    public void run()
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Running this session, not supported," + " throwing UnsupportedOperationException");
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a MessageProducer to send messages to the specified destination.
     *
     * @param destination the Destination to send messages to, or null if this is a producer
     *                    which does not have a specified destination.
     * @return A new MessageProducer
     * @throws JMSException                If the session fails to create a MessageProducer
     *                                     due to some internal error.
     * @throws InvalidDestinationException If an invalid destination is specified.
     */
    public MessageProducer createProducer(Destination destination) throws JMSException
    {
        checkNotClosed();
        MessageProducerImpl producer = new MessageProducerImpl(this, (DestinationImpl) destination);
        // register this actor with the session
        _messageActors.put(producer.getMessageActorID(), producer);
        return producer;
    }

    /**
     * Creates a MessageConsumer for the specified destination.
     *
     * @param destination The <code>Destination</code> to access
     * @return A new MessageConsumer for the specified destination.
     * @throws JMSException                If the session fails to create a MessageConsumer due to some internal error.
     * @throws InvalidDestinationException If an invalid destination is specified.
     */
    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        return createConsumer(destination, null);
    }

    /**
     * Creates a MessageConsumer for the specified destination, using a message selector.
     *
     * @param destination     The <code>Destination</code> to access
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @return A new MessageConsumer for the specified destination.
     * @throws JMSException                If the session fails to create a MessageConsumer due to some internal error.
     * @throws InvalidDestinationException If an invalid destination is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException
    {
        return createConsumer(destination, messageSelector, false);
    }

    /**
     * Creates MessageConsumer for the specified destination, using a message selector.
     * <p> This method can specify whether messages published by its own connection should
     * be delivered to it, if the destination is a topic.
     * <p/>
     * <P>In some cases, a connection may both publish and subscribe to a topic. The consumer
     * NoLocal attribute allows a consumer to inhibit the delivery of messages published by its
     * own connection. The default value for this attribute is False.
     *
     * @param destination     The <code>Destination</code> to access
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @param noLocal         If true, and the destination is a topic, inhibits the delivery of messages published
     *                        by its own connection.
     * @return A new MessageConsumer for the specified destination.
     * @throws JMSException                If the session fails to create a MessageConsumer due to some internal error.
     * @throws InvalidDestinationException If an invalid destination is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkNotClosed();
        checkDestination(destination);
        MessageConsumerImpl consumer;
        try
        {
            consumer = new MessageConsumerImpl(this, (DestinationImpl) destination, messageSelector, noLocal, null,
                                               String.valueOf(_consumerTag.incrementAndGet()));
        }
        catch (Exception e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating consumer.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // register this actor with the session
        _messageActors.put(consumer.getMessageActorID(), consumer);
        return consumer;
    }

    /**
     * Creates a queue identity by a given name.
     * <P>This facility is provided for the rare cases where clients need to
     * dynamically manipulate queue identity. It allows the creation of a
     * queue identity with a provider-specific name. Clients that depend
     * on this ability are not portable.
     * <P>Note that this method is not for creating the physical queue.
     * The physical creation of queues is an administrative task and is not
     * to be initiated by the JMS API. The one exception is the
     * creation of temporary queues, which is accomplished with the
     * <code>createTemporaryQueue</code> method.
     *
     * @param queueName the name of this <code>Queue</code>
     * @return a <code>Queue</code> with the given name
     * @throws JMSException If the session fails to create a queue due to some internal error.
     */
    public Queue createQueue(String queueName) throws JMSException
    {
        checkNotClosed();
        Queue result;
        try
        {
            result = new QueueImpl(this, queueName);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Queue.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    /**
     * Creates a topic identity given a Topicname.
     * <P>This facility is provided for the rare cases where clients need to
     * dynamically manipulate queue identity. It allows the creation of a
     * queue identity with a provider-specific name. Clients that depend
     * on this ability are not portable.
     * <P>Note that this method is not for creating the physical queue.
     * The physical creation of queues is an administrative task and is not
     * to be initiated by the JMS API. The one exception is the
     * creation of temporary queues, which is accomplished with the
     * <code>createTemporaryTopic</code> method.
     *
     * @param topicName The name of this <code>Topic</code>
     * @return a <code>Topic</code> with the given name
     * @throws JMSException If the session fails to create a topic due to some internal error.
     */
    public Topic createTopic(String topicName) throws JMSException
    {
        checkNotClosed();
        Topic result;
        try
        {
            result = new TopicImpl(this, topicName);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Topic.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    /**
     * Creates a durable subscriber to the specified topic,
     *
     * @param topic The non-temporary <code>Topic</code> to subscribe to.
     * @param name  The name used to identify this subscription.
     * @return A durable subscriber to the specified topic,
     * @throws JMSException                If creating a subscriber fails due to some internal error.
     * @throws InvalidDestinationException If an invalid topic is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        // by default, use a null messageselector and set noLocal to falsen
        return createDurableSubscriber(topic, name, null, false);
    }

    /**
     * Creates a durable subscriber to the specified topic, using a message selector and specifying whether messages
     * published by its
     * own connection should be delivered to it.
     * <p> A client can change an existing durable subscription by creating a durable <code>TopicSubscriber</code> with
     * the same name and a new topic and/or message selector. Changing a durable subscriber is equivalent to
     * unsubscribing (deleting) the old one and creating a new one.
     *
     * @param topic           The non-temporary <code>Topic</code> to subscribe to.
     * @param name            The name used to identify this subscription.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @param noLocal         If set, inhibits the delivery of messages published by its own connection
     * @return A durable subscriber to the specified topic,
     * @throws JMSException                If creating a subscriber fails due to some internal error.
     * @throws InvalidDestinationException If an invalid topic is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkNotClosed();
        checkDestination(topic);
        TopicSubscriberImpl subscriber;
        try
        {
            subscriber = new TopicSubscriberImpl(this, topic, messageSelector, noLocal,
                                                 _connection.getClientID() + ":" + name,
                                                 String.valueOf(_consumerTag.incrementAndGet()));
        }
        catch (Exception e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Durable Subscriber.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        _messageActors.put(subscriber.getMessageActorID(), subscriber);
        return subscriber;
    }

    /**
     * Create a QueueBrowser to peek at the messages on the specified queue.
     *
     * @param queue The <code>Queue</code> to browse.
     * @return A QueueBrowser.
     * @throws JMSException                If creating a browser fails due to some internal error.
     * @throws InvalidDestinationException If an invalid queue is specified.
     */
    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        return createBrowser(queue, null);
    }

    /**
     * Create a QueueBrowser to peek at the messages on the specified queue using a message selector.
     *
     * @param queue           The <code>Queue</code> to browse.
     * @param messageSelector Only messages with properties matching the message selector expression are delivered.
     * @return A QueueBrowser.
     * @throws JMSException                If creating a browser fails due to some internal error.
     * @throws InvalidDestinationException If an invalid queue is specified.
     * @throws InvalidSelectorException    If the message selector is invalid.
     */
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        checkDestination(queue);
        QueueBrowserImpl browser;
        try
        {
            browser =
                    new QueueBrowserImpl(this, queue, messageSelector, String.valueOf(_consumerTag.incrementAndGet()));
        }
        catch (Exception e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Durable Browser.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        // register this actor with the session
        _messageActors.put(browser.getMessageActorID(), browser);
        return browser;
    }

    /**
     * Create a TemporaryQueue. Its lifetime will be the Connection unless it is deleted earlier.
     *
     * @return A temporary queue.
     * @throws JMSException If creating the temporary queue fails due to some internal error.
     */
    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        TemporaryQueue result;
        try
        {
            result = new TemporaryQueueImpl(this);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Durable Temporary Queue.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    /**
     * Create a TemporaryTopic. Its lifetime will be the Connection unless it is deleted earlier.
     *
     * @return A temporary topic.
     * @throws JMSException If creating the temporary topic fails due to some internal error.
     */
    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        TemporaryTopic result;
        try
        {
            result = new TemporaryTopicImpl(this);
        }
        catch (QpidException e)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Problem when creating Durable Temporary Topic.", e);
            }
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        return result;
    }

    /**
     * Unsubscribes a durable subscription that has been created by a client.
     * <p/>
     * <P>This method deletes the state being maintained on behalf of the
     * subscriber by its provider.
     * <p/>
     * <P>It is erroneous for a client to delete a durable subscription
     * while there is an active <code>TopicSubscriber</code> for the
     * subscription, or while a consumed message is part of a pending
     * transaction or has not been acknowledged in the session.
     *
     * @param name the name used to identify this subscription
     * @throws JMSException                if the session fails to unsubscribe to the durable subscription due to some internal error.
     * @throws InvalidDestinationException if an invalid subscription name
     *                                     is specified.
     */
    public void unsubscribe(String name) throws JMSException
    {
        checkNotClosed();
    }

    /**
     * Get the latest thrown exception.
     *
     * @return The latest thrown exception.
     */
    public synchronized QpidException getCurrentException()
    {
        QpidException result = _currentException;
        _currentException = null;
        return result;
    }
    //----- Protected methods

    /**
     * Remove a message actor form this session
     * <p> This method is called when an actor is independently closed.
     *
     * @param messageActor The closed actor.
     */
    protected void closeMessageActor(MessageActor messageActor)
    {
        _messageActors.remove(messageActor.getMessageActorID());
    }

    /**
     * Idincates whether this session is stopped.
     *
     * @return True is this session is stopped, false otherwise.
     */
    protected boolean isStopped()
    {
        return _isStopped;
    }

    /**
     * Start the flow of message to this session.
     *
     * @throws Exception If starting the session fails due to some communication error.
     */
    protected synchronized void start() throws Exception
    {
        if (_isStopped)
        {
            // start all the MessageActors
            for (MessageActor messageActor : _messageActors.values())
            {
                messageActor.start();
            }
            startDispatchThread();
        }
    }

    /**
     * Restart delivery of asynch messages
     */
    private void startDispatchThread()
    {
        synchronized (_stoppingLock)
        {
            _isStopped = false;
            _stoppingLock.notify();
        }
        synchronized (_stoppingJoin)
        {
            _hasStopped = false;
        }
    }

    /**
     * Stop the flow of message to this session.
     *
     * @throws Exception If stopping the session fails due to some communication error.
     */
    protected synchronized void stop() throws Exception
    {
        if (!_isClosing && !_isStopped)
        {
            // stop all the MessageActors
            for (MessageActor messageActor : _messageActors.values())
            {
                messageActor.stop();
            }
            synchronized (_incomingAsynchronousMessages)
            {
                _isStopped = true;
                // unlock the sessionThread that will then wait on _stoppingLock
                _incomingAsynchronousMessages.notifyAll();
            }
            // wait for the sessionThread to stop processing messages
            synchronized (_stoppingJoin)
            {
                while (!_hasStopped)
                {
                    try
                    {
                        _stoppingJoin.wait();
                    }
                    catch (InterruptedException e)
                    {
                        /* ignore */
                    }
                }
            }
        }
    }

    /**
     * Notify this session that a message is processed
     *
     * @param message The processed message.
     */
    protected void preProcessMessage(QpidMessage message)
    {
        _inRecovery = false;
    }

    /**
     * Dispatch this message to this session asynchronous consumers
     *
     * @param consumerID The consumer ID.
     * @param message    The message to be dispatched.
     */
    public void dispatchMessage(String consumerID, QpidMessage message)
    {
        synchronized (_incomingAsynchronousMessages)
        {
            _incomingAsynchronousMessages.addLast(new IncomingMessage(consumerID, message));
            _incomingAsynchronousMessages.notifyAll();
        }
    }

    /**
     * Indicate whether this session is recovering .
     *
     * @return true if this session is recovering.
     */
    protected boolean isInRecovery()
    {
        return _inRecovery;
    }

    /**
     * Validate that the Session is not closed.
     * <p/>
     * If the Session has been closed, throw a IllegalStateException. This behaviour is
     * required by the JMS specification.
     *
     * @throws IllegalStateException If the session is closed.
     */
    protected void checkNotClosed() throws IllegalStateException
    {
        if (_isClosed)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Session has been closed. Cannot invoke any further operations.");
            }
            throw new javax.jms.IllegalStateException("Session has been closed. Cannot invoke any further operations.");
        }
    }

    /**
     * Validate that the destination is valid i.e. it is not null
     *
     * @param dest The destination to be checked
     * @throws InvalidDestinationException If the destination not valid.
     */
    protected void checkDestination(Destination dest) throws InvalidDestinationException
    {
        if (dest == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid destination specified: " + dest,
                                                            "Invalid destination");
        }
    }

    /**
     * A session keeps the list of unack messages only when the ack mode is
     * set to client ack mode. Otherwise messages are always ack.
     * <p> We can use an ack heuristic for  dups ok mode where bunch of messages are ack.
     * This has to be done.
     *
     * @param message The message to be acknowledged.
     * @throws JMSException If the message cannot be acknowledged due to an internal error.
     */
    protected void acknowledgeMessage(QpidMessage message) throws JMSException
    {
        if (getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            // messages will be acknowldeged by the client application.
            // store this message for acknowledging it afterward
            synchronized (_unacknowledgedMessages)
            {
                _unacknowledgedMessages.add(message);
            }
        }
        else
        {
            // acknowledge this message
            RangeSet ranges = new RangeSet();
            ranges.add(message.getMessageTransferId());
            getQpidSession().messageAcknowledge(ranges);
        }
        //tobedone: Implement DUPS OK heuristic
    }

    /**
     * This method is called when a message is acked.
     * <p/>
     * <P>Acknowledgment of a message automatically acknowledges all
     * messages previously received by the session. Clients may
     * individually acknowledge messages or they may choose to acknowledge
     * messages in application defined groups (which is done by acknowledging
     * the last received message in the group).
     *
     * @throws JMSException If this method is called on a closed session.
     */
    public void acknowledge() throws JMSException
    {
        checkNotClosed();
        if (getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            synchronized (_unacknowledgedMessages)
            {
                for (QpidMessage message : _unacknowledgedMessages)
                {
                    // acknowledge this message
                    RangeSet ranges = new RangeSet();
                    ranges.add(message.getMessageTransferId());
                    getQpidSession().messageAcknowledge(ranges);
                }
                //empty the list of unack messages
                _unacknowledgedMessages.clear();
            }
        }
        //else there is no effect
    }

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
     * Get this session's conneciton
     *
     * @return This session's connection
     */
    protected ConnectionImpl getConnection()
    {
        return _connection;
    }

    /**
     * sync and return the potential exception
     *
     * @throws QpidException If an exception has been thrown by the broker.
     */
    protected void testQpidException() throws QpidException
    {
        //_qpidSession.sync();
        QpidException qe = getCurrentException();
        if (qe != null)
        {
            throw qe;
        }
    }

    //------ Private Methods
    /**
     * Close the producer and the consumers of this session
     *
     * @throws JMSException If one of the MessaeActor cannot be closed due to some internal error.
     */
    private void closeAllMessageActors() throws JMSException
    {
        for (MessageActor messageActor : _messageActors.values())
        {
            messageActor.closeMessageActor();
        }
    }

    /**
     * create and start the MessageDispatcherThread.
     */
    private synchronized void initMessageDispatcherThread()
    {
        // Create and start a MessageDispatcherThread
        // This thread is dispatching messages to the async consumers
        _messageDispatcherThread = new MessageDispatcherThread();
        _messageDispatcherThread.start();
    }

    //------ Inner classes

    /**
     * Lstener for qpid protocol exceptions
     */
    private class QpidSessionExceptionListener implements org.apache.qpidity.nclient.ClosedListener
    {
        public void onClosed(ErrorCode errorCode, String reason, Throwable t)
        {
            synchronized (this)
            {
                //todo check the error code for finding out if we need to notify the
                // JMS connection exception listener
                _currentException = new QpidException(reason,errorCode,null);
            }
        }
    }

    /**
     * Convenient class for storing incoming messages associated with a consumer ID.
     * <p> Those messages are enqueued in _incomingAsynchronousMessages
     */
    private class IncomingMessage
    {
        // The consumer ID
        private String _consumerId;
        // The message
        private QpidMessage _message;

        //-- constructor
        /**
         * Creat a new incoming message
         *
         * @param consumerId The consumer ID
         * @param message    The message to be delivered
         */
        IncomingMessage(String consumerId, QpidMessage message)
        {
            _consumerId = consumerId;
            _message = message;
        }

        // Getters
        /**
         * Get the consumer ID
         *
         * @return The consumer ID for this message
         */
        public String getConsumerId()
        {
            return _consumerId;
        }

        /**
         * Get the message.
         *
         * @return The message.
         */
        public QpidMessage getMessage()
        {
            return _message;
        }
    }

    /**
     * A MessageDispatcherThread is attached to every SessionImpl.
     * <p/>
     * This thread is responsible for removing messages from m_incomingMessages and
     * dispatching them to the appropriate MessageConsumer.
     * <p> Messages have to be dispatched serially.
     */
    private class MessageDispatcherThread extends Thread
    {
        //--- Constructor
        /**
         * Create a Deamon thread for dispatching messages to this session listeners.
         */
        MessageDispatcherThread()
        {
            super("MessageDispatcher");
            // this thread is Deamon
            setDaemon(true);
        }

        /**
         * Use to run this thread.
         */
        public void run()
        {
            IncomingMessage message = null;
            // deliver messages to asynchronous consumers until the stop flag is set.
            do
            {
                // When this session is not closing and and stopped
                // then this thread needs to wait until messages are delivered.
                synchronized (_incomingAsynchronousMessages)
                {
                    while (!_isClosing && !_isStopped && _incomingAsynchronousMessages.isEmpty())
                    {
                        try
                        {
                            _incomingAsynchronousMessages.wait();
                        }
                        catch (InterruptedException ie)
                        {
                            /* ignore */
                        }
                    }
                }
                // If this session is stopped then we need to wait on the stoppingLock
                synchronized (_stoppingLock)
                {
                    try
                    {
                        while (_isStopped)
                        {
                            // if the session is stopped we have to notify the stopper thread
                            synchronized (_stoppingJoin)
                            {
                                _hasStopped = true;
                                _stoppingJoin.notify();
                            }
                            _stoppingLock.wait();
                        }
                    }
                    catch (Exception ie)
                    {
                        /* ignore */
                    }
                }
                synchronized (_incomingAsynchronousMessages)
                {
                    if (!_isClosing && !_incomingAsynchronousMessages.isEmpty())
                    {
                        message = _incomingAsynchronousMessages.getFirst();
                    }
                }

                if (message != null)
                {
                    MessageConsumerImpl mc;
                    synchronized (_messageActors)
                    {
                        mc = (MessageConsumerImpl) _messageActors.get(message.getConsumerId());
                    }
                    if (mc != null)
                    {
                        try
                        {
                            // mc.onMessage(message.getMessage());
                            mc.notifyMessageListener(message.getMessage());
                        }
                        catch (RuntimeException t)
                        {
                            // the JMS specification tells us to flag that to the client!
                            _logger.error(
                                    "Warning! Asynchronous message consumer" + mc + " from session " + this + " has thrown a RunTimeException " + t);
                        }
                    }
                }
                message = null;
            }
            while (!_isClosing);   // repeat as long as this session is not closing
        }
    }

}
