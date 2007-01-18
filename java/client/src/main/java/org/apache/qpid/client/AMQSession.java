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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQInvalidSelectorException;
import org.apache.qpid.AMQUndeliveredException;
import org.apache.qpid.client.failover.FailoverSupport;
import org.apache.qpid.client.message.AbstractJMSMessage;
import org.apache.qpid.client.message.JMSStreamMessage;
import org.apache.qpid.client.message.MessageFactoryRegistry;
import org.apache.qpid.client.message.UnprocessedMessage;
import org.apache.qpid.client.protocol.AMQProtocolHandler;
import org.apache.qpid.client.util.FlowControllingBlockingQueue;
import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQFrame;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ChannelCloseBody;
import org.apache.qpid.framing.ChannelCloseOkBody;
import org.apache.qpid.framing.ChannelFlowBody;
import org.apache.qpid.framing.ExchangeBoundBody;
import org.apache.qpid.framing.ExchangeBoundOkBody;
import org.apache.qpid.framing.ExchangeDeclareBody;
import org.apache.qpid.framing.ExchangeDeclareOkBody;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.FieldTableFactory;
import org.apache.qpid.framing.MessageConsumeBody;
import org.apache.qpid.framing.MessageOkBody;
import org.apache.qpid.framing.MessageRecoverBody;
import org.apache.qpid.framing.QueueBindBody;
import org.apache.qpid.framing.QueueDeclareBody;
import org.apache.qpid.framing.QueueDeleteBody;
import org.apache.qpid.framing.QueueDeleteOkBody;
import org.apache.qpid.framing.TxCommitBody;
import org.apache.qpid.framing.TxCommitOkBody;
import org.apache.qpid.framing.TxRollbackBody;
import org.apache.qpid.framing.TxRollbackOkBody;
import org.apache.qpid.protocol.AMQMethodEvent;
import org.apache.qpid.jms.Session;
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.handler.ExchangeBoundHandler;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.URLSyntaxException;

public class AMQSession extends Closeable implements Session, QueueSession, TopicSession
{
    private static final Logger _logger = Logger.getLogger(AMQSession.class);

    public static final int DEFAULT_PREFETCH_HIGH_MARK = 5000;
    public static final int DEFAULT_PREFETCH_LOW_MARK = 2500;

    private AMQConnection _connection;

    private boolean _transacted;

    private int _acknowledgeMode;

    private int _channelId;

    private int _defaultPrefetchHighMark = DEFAULT_PREFETCH_HIGH_MARK;
    private int _defaultPrefetchLowMark = DEFAULT_PREFETCH_LOW_MARK;

    /**
     * Used to reference durable subscribers so they requests for unsubscribe can be handled
     * correctly.  Note this only keeps a record of subscriptions which have been created
     * in the current instance.  It does not remember subscriptions between executions of the
     * client
     */
    private final ConcurrentHashMap<String, TopicSubscriberAdaptor> _subscriptions =
            new ConcurrentHashMap<String, TopicSubscriberAdaptor>();
    private final ConcurrentHashMap<BasicMessageConsumer, String> _reverseSubscriptionMap =
            new ConcurrentHashMap<BasicMessageConsumer, String>();

    /**
     * Used in the consume method. We generate the consume tag on the client so that we can use the nowait
     * feature.
     */
    private int _nextTag = 1;

    /**
     * This queue is bounded and is used to store messages before being dispatched to the consumer
     */
    private final FlowControllingBlockingQueue _queue;

    private Dispatcher _dispatcher;

    private MessageFactoryRegistry _messageFactoryRegistry;

    /**
     * Set of all producers created by this session
     */
    private Map _producers = new ConcurrentHashMap();

    /**
     * Maps from consumer tag (String) to JMSMessageConsumer instance
     */
    private Map<String, BasicMessageConsumer> _consumers = new ConcurrentHashMap<String, BasicMessageConsumer>();

    /**
     * Maps from destination to count of JMSMessageConsumers
     */
    private ConcurrentHashMap<Destination, AtomicInteger> _destinationConsumerCount =
            new ConcurrentHashMap<Destination, AtomicInteger>();

    /**
     * Default value for immediate flag used by producers created by this session is false, i.e. a consumer does not
     * need to be attached to a queue
     */
    protected static final boolean DEFAULT_IMMEDIATE = false;

    /**
     * Default value for mandatory flag used by producers created by this sessio is true, i.e. server will not silently
     * drop messages where no queue is connected to the exchange for the message
     */
    protected static final boolean DEFAULT_MANDATORY = true;

    /**
     * The counter of the next producer id. This id is generated by the session and used only to allow the
     * producer to identify itself to the session when deregistering itself.
     * <p/>
     * Access to this id does not require to be synchronized since according to the JMS specification only one
     * thread of control is allowed to create producers for any given session instance.
     */
    private long _nextProducerId;

    /**
     * Track the 'stopped' state of the dispatcher, a session starts in the stopped state.
     */
    private volatile AtomicBoolean _stopped = new AtomicBoolean(true);

    /**
     * Set when recover is called. This is to handle the case where recover() is called by application code
     * during onMessage() processing. We need to make sure we do not send an auto ack if recover was called.
     */
    private boolean _inRecovery;



    /**
     * Responsible for decoding a message fragment and passing it to the appropriate message consumer.
     */
    private class Dispatcher extends Thread
    {
        public Dispatcher()
        {
            super("Dispatcher-Channel-" + _channelId);
        }

        public void run()
        {
            UnprocessedMessage message;
            _stopped.set(false);
            try
            {
                while (!_stopped.get() && (message = (UnprocessedMessage) _queue.take()) != null)
                {
                    dispatchMessage(message);
                }
            }
            catch (InterruptedException e)
            {
                ;
            }

            _logger.info("Dispatcher thread terminating for channel " + _channelId);
        }

        private void dispatchMessage(UnprocessedMessage message)
        {
            if (message.contents != null)
            {
                final BasicMessageConsumer consumer = (BasicMessageConsumer) _consumers.get(message.contentHeader.getDestination());

                if (consumer == null)
                {
                    _logger.warn("Received a message from queue " + message.contentHeader.getDestination() + " without a handler - ignoring...");
                    _logger.warn("Consumers that exist: " + _consumers);
                    _logger.warn("Session hashcode: " + System.identityHashCode(this));
                }
                else
                {

                    consumer.notifyMessage(message, _channelId);

                }
            }
            /*else
            {
                try
                {
                    // Bounced message is processed here, away from the mina thread
                    AbstractJMSMessage bouncedMessage = _messageFactoryRegistry.createMessage(0,
                                                                                              false,
                                                                                              message.contentHeader,
                                                                                              message.content);

                    int errorCode = message.bounceBody.replyCode;
                    String reason = message.bounceBody.replyText;
                    _logger.debug("Message returned with error code " + errorCode + " (" + reason + ")");

                    //@TODO should this be moved to an exception handler of sorts. Somewhere errors are converted to correct execeptions.
                    if (errorCode == AMQConstant.NO_CONSUMERS.getCode())
                    {
                        _connection.exceptionReceived(new AMQNoConsumersException("Error: " + reason, bouncedMessage));
                    }
                    else if (errorCode == AMQConstant.NO_ROUTE.getCode())
                    {
                        _connection.exceptionReceived(new AMQNoRouteException("Error: " + reason, bouncedMessage));
                    }
                    else
                    {
                        _connection.exceptionReceived(new AMQUndeliveredException(errorCode, "Error: " + reason, bouncedMessage));
                    }

                }
                catch (Exception e)
                {
                    _logger.error("Caught exception trying to raise undelivered message exception (dump follows) - ignoring...", e);
                }
            }*/
        }

        public void stopDispatcher()
        {
            _stopped.set(true);
            interrupt();
        }
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry)
    {
        this(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, DEFAULT_PREFETCH_HIGH_MARK, DEFAULT_PREFETCH_LOW_MARK);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetch)
    {
        this(con, channelId, transacted, acknowledgeMode, messageFactoryRegistry, defaultPrefetch, defaultPrefetch);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode,
               MessageFactoryRegistry messageFactoryRegistry, int defaultPrefetchHighMark, int defaultPrefetchLowMark)
    {
        _connection = con;
        _transacted = transacted;
        if (transacted)
        {
            _acknowledgeMode = javax.jms.Session.SESSION_TRANSACTED;
        }
        else
        {
            _acknowledgeMode = acknowledgeMode;
        }
        _channelId = channelId;
        _messageFactoryRegistry = messageFactoryRegistry;
        _defaultPrefetchHighMark = defaultPrefetchHighMark;
        _defaultPrefetchLowMark = defaultPrefetchLowMark;

        if (_acknowledgeMode == NO_ACKNOWLEDGE)
        {
            _queue = new FlowControllingBlockingQueue(_defaultPrefetchHighMark, _defaultPrefetchLowMark,
                                                      new FlowControllingBlockingQueue.ThresholdListener()
                                                      {
                                                          public void aboveThreshold(int currentValue) 
                                                          {
                                                              if (_acknowledgeMode == NO_ACKNOWLEDGE)
                                                              {
                                                                  _logger.warn("Above threshold(" + _defaultPrefetchHighMark + ") so suspending channel. Current value is " + currentValue);
                                                                 try{
                                                                  	suspendChannel();
                                                              	  }catch (AMQException e) {
                                                              		  _logger.error("FlowControllingBlockingQueue,aboveThreshold, Cannot Suspend the channel",e);
                                                              	  }
                                                              }
                                                          }

                                                          public void underThreshold(int currentValue)
                                                          {
                                                              if (_acknowledgeMode == NO_ACKNOWLEDGE)
                                                              {
                                                                  _logger.warn("Below threshold(" + _defaultPrefetchLowMark + ") so unsuspending channel. Current value is " + currentValue);
                                                                  	try {
																		unsuspendChannel();
																	} catch (AMQException e) {
																		_logger.error("FlowControllingBlockingQueue,underThreshold, Cannot Unsuspend the channel",e);
																	}
                                                              }
                                                          }
                                                      });
        }
        else
        {
            _queue = new FlowControllingBlockingQueue(_defaultPrefetchHighMark, null);
        }
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry());
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetch)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetch);
    }

    AMQSession(AMQConnection con, int channelId, boolean transacted, int acknowledgeMode, int defaultPrefetchHigh, int defaultPrefetchLow)
    {
        this(con, channelId, transacted, acknowledgeMode, MessageFactoryRegistry.newDefaultRegistry(), defaultPrefetchHigh, defaultPrefetchLow);
    }

    public AMQConnection getAMQConnection()
    {
        return _connection;
    }

    public BytesMessage createBytesMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                return (BytesMessage) _messageFactoryRegistry.createMessage("application/octet-stream");
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create message: " + e);
            }
        }
    }

    public MapMessage createMapMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                return (MapMessage) _messageFactoryRegistry.createMessage("jms/map-message");
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create message: " + e);
            }
        }
    }

    public javax.jms.Message createMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                return (BytesMessage) _messageFactoryRegistry.createMessage("application/octet-stream");
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create message: " + e);
            }
        }
    }

    public ObjectMessage createObjectMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                return (ObjectMessage) _messageFactoryRegistry.createMessage("application/java-object-stream");
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create message: " + e);
            }
        }
    }

    public ObjectMessage createObjectMessage(Serializable object) throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                ObjectMessage msg = (ObjectMessage) _messageFactoryRegistry.createMessage("application/java-object-stream");
                msg.setObject(object);
                return msg;
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create message: " + e);
            }
        }
    }

    public StreamMessage createStreamMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();

            try
            {
                return (StreamMessage) _messageFactoryRegistry.createMessage(JMSStreamMessage.MIME_TYPE);
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create text message: " + e);
            }
        }
    }

    public TextMessage createTextMessage() throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();

            try
            {
                return (TextMessage) _messageFactoryRegistry.createMessage("text/plain");
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create text message: " + e);
            }
        }
    }

    public TextMessage createTextMessage(String text) throws JMSException
    {
        synchronized(_connection.getFailoverMutex())
        {
            checkNotClosed();
            try
            {
                TextMessage msg = (TextMessage) _messageFactoryRegistry.createMessage("text/plain");
                msg.setText(text);
                return msg;
            }
            catch (AMQException e)
            {
                throw new JMSException("Unable to create text message: " + e);
            }
        }
    }

    public boolean getTransacted() throws JMSException
    {
        checkNotClosed();
        return _transacted;
    }

    public int getAcknowledgeMode() throws JMSException
    {
        checkNotClosed();
        return _acknowledgeMode;
    }

    public void commit() throws JMSException
    {
        checkTransacted();
        try
        {
            // Acknowledge up to message last delivered (if any) for each consumer.
            //need to send ack for messages delivered to consumers so far
            for (Iterator<BasicMessageConsumer> i = _consumers.values().iterator(); i.hasNext();)
            {
                //Sends acknowledgement to server
                i.next().acknowledgeLastDelivered();
            }

            // Commits outstanding messages sent and outstanding acknowledgements.
            // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            _connection.getProtocolHandler().syncWrite(_channelId, TxCommitBody.createMethodBody((byte)0, (byte)9), TxCommitOkBody.class);
        }
        catch (AMQException e)
        {
            JMSException exception = new JMSException("Failed to commit: " + e.getMessage());
            exception.setLinkedException(e);
            throw exception;
        }
    }

    public void rollback() throws JMSException
    {
        checkTransacted();
        try
        {
            // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            _connection.getProtocolHandler().syncWrite(_channelId, 
                    TxRollbackBody.createMethodBody((byte)0, (byte)9), TxRollbackOkBody.class);
        }
        catch (AMQException e)
        {
            throw(JMSException) (new JMSException("Failed to rollback: " + e).initCause(e));
        }
    }

    public void close() throws JMSException
    {
        // We must close down all producers and consumers in an orderly fashion. This is the only method
        // that can be called from a different thread of control from the one controlling the session
        synchronized(_connection.getFailoverMutex())
        {
            //Ensure we only try and close an open session.
            if (!_closed.getAndSet(true))
            {
                // we pass null since this is not an error case
                closeProducersAndConsumers(null);

                try
                {
                    _connection.getProtocolHandler().closeSession(this);
        	        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        	        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        	        // Be aware of possible changes to parameter order as versions change.
                    final AMQMethodBody methodBody = ChannelCloseBody.createMethodBody(
                        (byte)0, (byte)9,	// AMQP version (major, minor)
                        0,	// classId
                        0,	// methodId
                        AMQConstant.REPLY_SUCCESS.getCode(),	// replyCode
                        "JMS client closing channel");	// replyText
                    _connection.getProtocolHandler().syncWrite(getChannelId(), methodBody, ChannelCloseOkBody.class);
                    // When control resumes at this point, a reply will have been received that
                    // indicates the broker has closed the channel successfully

                }
                catch (AMQException e)
                {
                    JMSException jmse = new JMSException("Error closing session: " + e);
                    jmse.setLinkedException(e);
                    throw jmse;
                }
                finally
                {
                    _connection.deregisterSession(_channelId);
                }
            }
        }
    }

    /**
     * Close all producers or consumers. This is called either in the error case or when closing the session normally.
     *
     * @param amqe the exception, may be null to indicate no error has occurred
     */
    private void closeProducersAndConsumers(AMQException amqe)
    {
        try
        {
            closeProducers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
        try
        {
            closeConsumers(amqe);
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
    }

    /**
     * Called when the server initiates the closure of the session
     * unilaterally.
     *
     * @param e the exception that caused this session to be closed. Null causes the
     */
    public void closed(Throwable e)
    {
        synchronized(_connection.getFailoverMutex())
        {
            // An AMQException has an error code and message already and will be passed in when closure occurs as a
            // result of a channel close request
            _closed.set(true);
            AMQException amqe;
            if (e instanceof AMQException)
            {
                amqe = (AMQException) e;
            }
            else
            {
                amqe = new AMQException(_logger, "Closing session forcibly", e);
            }
            _connection.deregisterSession(_channelId);
            closeProducersAndConsumers(amqe);
        }
    }

    /**
     * Called to mark the session as being closed. Useful when the session needs to be made invalid, e.g. after
     * failover when the client has veoted resubscription.
     * <p/>
     * The caller of this method must already hold the failover mutex.
     */
    void markClosed()
    {
        _closed.set(true);
        _connection.deregisterSession(_channelId);
        markClosedProducersAndConsumers();
    }

    private void markClosedProducersAndConsumers()
    {
        try
        {
            // no need for a markClosed* method in this case since there is no protocol traffic closing a producer
            closeProducers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
        try
        {
            markClosedConsumers();
        }
        catch (JMSException e)
        {
            _logger.error("Error closing session: " + e, e);
        }
    }

    /**
     * Called to close message producers cleanly. This may or may <b>not</b> be as a result of an error. There is
     * currently no way of propagating errors to message producers (this is a JMS limitation).
     */
    private void closeProducers() throws JMSException
    {
        // we need to clone the list of producers since the close() method updates the _producers collection
        // which would result in a concurrent modification exception
        final ArrayList clonedProducers = new ArrayList(_producers.values());

        final Iterator it = clonedProducers.iterator();
        while (it.hasNext())
        {
            final BasicMessageProducer prod = (BasicMessageProducer) it.next();
            prod.close();
        }
        // at this point the _producers map is empty
    }

    /**
     * Called to close message consumers cleanly. This may or may <b>not</b> be as a result of an error.
     *
     * @param error not null if this is a result of an error occurring at the connection level
     */
    private void closeConsumers(Throwable error) throws JMSException
    {
        if (_dispatcher != null)
        {
            _dispatcher.stopDispatcher();
        }
        // we need to clone the list of consumers since the close() method updates the _consumers collection
        // which would result in a concurrent modification exception
        final ArrayList<BasicMessageConsumer> clonedConsumers = new ArrayList(_consumers.values());

        final Iterator<BasicMessageConsumer> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final BasicMessageConsumer con = it.next();
            if (error != null)
            {
                con.notifyError(error);
            }
            else
            {
                con.close();
            }
        }
        // at this point the _consumers map will be empty
    }

    private void markClosedConsumers() throws JMSException
    {
        if (_dispatcher != null)
        {
            _dispatcher.stopDispatcher();
        }
        // we need to clone the list of consumers since the close() method updates the _consumers collection
        // which would result in a concurrent modification exception
        final ArrayList<BasicMessageConsumer> clonedConsumers = new ArrayList<BasicMessageConsumer>(_consumers.values());

        final Iterator<BasicMessageConsumer> it = clonedConsumers.iterator();
        while (it.hasNext())
        {
            final BasicMessageConsumer con = it.next();
            con.markClosed();
        }
        // at this point the _consumers map will be empty
    }

    /**
     * Asks the broker to resend all unacknowledged messages for the session.
     *
     * @throws JMSException
     */
    public void recover() throws JMSException
    {
        checkNotClosed();
        checkNotTransacted(); // throws IllegalStateException if a transacted session
        // this is set only here, and the before the consumer's onMessage is called it is set to false
        _inRecovery = true;
        for (BasicMessageConsumer consumer : _consumers.values())
        {
            consumer.clearUnackedMessages();
        }
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        try {
			_connection.getProtocolHandler().writeRequest(_channelId,
			    MessageRecoverBody.createMethodBody((byte)0, (byte)9,	// AMQP version (major, minor)
			        false));	// requeue
		} catch (AMQException e) {
			_logger.error("Error recovering",e);
			JMSException ex = new JMSException("Error Recovering");
			ex.initCause(e);
			throw ex;
		}
    }

    boolean isInRecovery()
    {
        return _inRecovery;
    }

    void setInRecovery(boolean inRecovery)
    {
        _inRecovery = inRecovery;
    }

    public void acknowledge() throws JMSException
    {
        if (isClosed())
        {
            throw new IllegalStateException("Session is already closed");
        }
        for (BasicMessageConsumer consumer : _consumers.values())
        {
            consumer.acknowledge();
        }


    }


    public MessageListener getMessageListener() throws JMSException
    {
        checkNotClosed();
        throw new java.lang.UnsupportedOperationException("MessageListener interface not supported");
    }

    public void setMessageListener(MessageListener listener) throws JMSException
    {
        checkNotClosed();
        throw new java.lang.UnsupportedOperationException("MessageListener interface not supported");
    }

    public void run()
    {
        throw new java.lang.UnsupportedOperationException();
    }

    public MessageProducer createProducer(Destination destination, boolean mandatory,
                                          boolean immediate, boolean waitUntilSent)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate, waitUntilSent);
    }

    public MessageProducer createProducer(Destination destination, boolean mandatory, boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate);
    }

    public MessageProducer createProducer(Destination destination, boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, DEFAULT_MANDATORY, immediate);
    }

    public MessageProducer createProducer(Destination destination) throws JMSException
    {
        return createProducerImpl(destination, DEFAULT_MANDATORY, DEFAULT_IMMEDIATE);
    }

    private org.apache.qpid.jms.MessageProducer createProducerImpl(Destination destination, boolean mandatory,
                                                                   boolean immediate)
            throws JMSException
    {
        return createProducerImpl(destination, mandatory, immediate, false);
    }

    private org.apache.qpid.jms.MessageProducer createProducerImpl(final Destination destination, final boolean mandatory,
                                                                   final boolean immediate, final boolean waitUntilSent)
            throws JMSException
    {
        return (org.apache.qpid.jms.MessageProducer) new FailoverSupport()
        {
            public Object operation() throws JMSException
            {
                try
                {
                    checkNotClosed();
                    long producerId = getNextProducerId();
                    BasicMessageProducer producer = new BasicMessageProducer(_connection, (AMQDestination) destination, _transacted, _channelId,
                                                                             AMQSession.this, _connection.getProtocolHandler(),
                                                                             producerId, immediate, mandatory, waitUntilSent);
                    registerProducer(producerId, producer);
                    return producer;
                }
                catch (AMQException e) { throw new JMSException(e.toString()); }
            }
        }.execute(_connection);
    }

    /**
     * Creates a QueueReceiver
     *
     * @param destination
     * @return QueueReceiver - a wrapper around our MessageConsumer
     * @throws JMSException
     */
    public QueueReceiver createQueueReceiver(Destination destination) throws JMSException
    {
        checkValidDestination(destination);
        AMQQueue dest = (AMQQueue) destination;
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(destination);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    /**
     * Creates a QueueReceiver using a message selector
     *
     * @param destination
     * @param messageSelector
     * @return QueueReceiver - a wrapper around our MessageConsumer
     * @throws JMSException
     */
    public QueueReceiver createQueueReceiver(Destination destination, String messageSelector) throws JMSException
    {
        checkValidDestination(destination);
        AMQQueue dest = (AMQQueue) destination;
        BasicMessageConsumer consumer = (BasicMessageConsumer)
                createConsumer(destination, messageSelector);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  false,
                                  false,
                                  null,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  false,
                                  false,
                                  messageSelector,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  noLocal,
                                  false,
                                  messageSelector,
                                  null,
                                  false,
                                  false);
    }

    public MessageConsumer createBrowserConsumer(Destination destination,
                                         String messageSelector,
                                         boolean noLocal)
            throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination,
                                  _defaultPrefetchHighMark,
                                  _defaultPrefetchLowMark,
                                  noLocal,
                                  false,
                                  messageSelector,
                                  null,
                                  true,
                                  true);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetch,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetch, prefetch, noLocal, exclusive, selector, null, false, false);
    }


    public MessageConsumer createConsumer(Destination destination,
                                          int prefetchHigh,
                                          int prefetchLow,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive, selector, null, false, false);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetch,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector,
                                          FieldTable rawSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetch, prefetch, noLocal, exclusive,
                                  selector, rawSelector, false, false);
    }

    public MessageConsumer createConsumer(Destination destination,
                                          int prefetchHigh,
                                          int prefetchLow,
                                          boolean noLocal,
                                          boolean exclusive,
                                          String selector,
                                          FieldTable rawSelector) throws JMSException
    {
        checkValidDestination(destination);
        return createConsumerImpl(destination, prefetchHigh, prefetchLow, noLocal, exclusive,
                                  selector, rawSelector, false, false);
    }

    protected MessageConsumer createConsumerImpl(final Destination destination,
                                                 final int prefetchHigh,
                                                 final int prefetchLow,
                                                 final boolean noLocal,
                                                 final boolean exclusive,
                                                 final String selector,
                                                 final FieldTable rawSelector,
                                                 final boolean noConsume,
                                                 final boolean autoClose) throws JMSException
    {
        checkTemporaryDestination(destination);

        return (org.apache.qpid.jms.MessageConsumer) new FailoverSupport()
        {
            public Object operation() throws JMSException
            {
                checkNotClosed();

                AMQDestination amqd = (AMQDestination) destination;

                final AMQProtocolHandler protocolHandler = _connection.getProtocolHandler();
                // TODO: construct the rawSelector from the selector string if rawSelector == null
                final FieldTable ft = FieldTableFactory.newFieldTable();
                //if (rawSelector != null)
                //    ft.put("headers", rawSelector.getDataAsBytes());
                if (rawSelector != null)
                {
                    ft.addAll(rawSelector);
                }
                BasicMessageConsumer consumer = new BasicMessageConsumer(_channelId, _connection, amqd, selector, noLocal,
                                                                         _messageFactoryRegistry, AMQSession.this,
                                                                         protocolHandler, ft, prefetchHigh, prefetchLow, exclusive,
                                                                         _acknowledgeMode, noConsume, autoClose);

                try
                {
                    registerConsumer(consumer, false);
                }
                catch (AMQInvalidSelectorException ise)
                {
                    JMSException ex = new InvalidSelectorException(ise.getMessage());
                    ex.setLinkedException(ise);
                    throw ex;
                }
                catch (AMQException e)
                {
                    JMSException ex = new JMSException("Error registering consumer: " + e);
                    ex.setLinkedException(e);
                    throw ex;
                }

                synchronized(destination)
                {
                    _destinationConsumerCount.putIfAbsent(destination, new AtomicInteger());
                    _destinationConsumerCount.get(destination).incrementAndGet();
                }

                return consumer;
            }
        }.execute(_connection);
    }

    private void checkTemporaryDestination(Destination destination)
            throws JMSException
    {
        if ((destination instanceof TemporaryDestination))
        {
            _logger.debug("destination is temporary");
            final TemporaryDestination tempDest = (TemporaryDestination) destination;
            if (tempDest.getSession() != this)
            {
                _logger.debug("destination is on different session");
                throw new JMSException("Cannot consume from a temporary destination created onanother session");
            }
            if (tempDest.isDeleted())
            {
                _logger.debug("destination is deleted");
                throw new JMSException("Cannot consume from a deleted destination");
            }
        }
    }


    public boolean hasConsumer(Destination destination)
    {
        AtomicInteger counter = _destinationConsumerCount.get(destination);

        return (counter != null) && (counter.get() != 0);
    }


    public void declareExchange(String name, String type) throws AMQException
    {
        declareExchange(name, type, _connection.getProtocolHandler());
    }

    public void declareExchangeSynch(String name, String type) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ExchangeDeclareBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            null,	// arguments
            false,	// autoDelete
            false,	// durable
            name,	// exchange
            false,	// internal
            false,	// nowait
            false,	// passive
            0,	// ticket
            type);	// type
        _connection.getProtocolHandler().syncWrite(_channelId, methodBody, ExchangeDeclareOkBody.class);
    }

    private void declareExchange(AMQDestination amqd, AMQProtocolHandler protocolHandler)throws AMQException
    {
        declareExchange(amqd.getExchangeName(), amqd.getExchangeClass(), protocolHandler);
    }

    private void declareExchange(String name, String type, AMQProtocolHandler protocolHandler) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ExchangeDeclareBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            null,	// arguments
            false,	// autoDelete
            false,	// durable
            name,	// exchange
            false,	// internal
            true,	// nowait
            false,	// passive
            0,	// ticket
            type);	// type
        
        protocolHandler.writeRequest(_channelId, methodBody);
    }

    /**
     * Declare the queue.
     *
     * @param amqd
     * @param protocolHandler
     * @return the queue name. This is useful where the broker is generating a queue name on behalf of the client.
     * @throws AMQException
     */
    private String declareQueue(AMQDestination amqd, AMQProtocolHandler protocolHandler) throws AMQException
    {
        // For queues (but not topics) we generate the name in the client rather than the
        // server. This allows the name to be reused on failover if required. In general,
        // the destination indicates whether it wants a name generated or not.
        if (amqd.isNameRequired())
        {
            amqd.setQueueName(protocolHandler.generateQueueName());
        }

        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = QueueDeclareBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            null,	// arguments
            amqd.isAutoDelete(),	// autoDelete
            amqd.isDurable(),	// durable
            amqd.isExclusive(),	// exclusive
            true,	// nowait
            false,	// passive
            amqd.getQueueName(),	// queue
            0);	// ticket

        protocolHandler.writeRequest(_channelId, methodBody);
        return amqd.getQueueName();
    }

    private void bindQueue(AMQDestination amqd, String queueName, AMQProtocolHandler protocolHandler, FieldTable ft) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = QueueBindBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            ft,	// arguments
            amqd.getExchangeName(),	// exchange
            true,	// nowait
            queueName,	// queue
            amqd.getRoutingKey(),	// routingKey
            0);	// ticket

        protocolHandler.writeRequest(_channelId, methodBody);
    }

    /**
     * Register to consume from the queue.
     *
     * @param queueName
     * @return the consumer tag generated by the broker
     */
    private void consumeFromQueue(BasicMessageConsumer consumer, String queueName, AMQProtocolHandler protocolHandler,
                                  boolean nowait, String messageSelector) throws AMQException
    {
        //fixme prefetch values are not used here. Do we need to have them as parametsrs?
        //need to generate a consumer tag on the client so we can exploit the nowait flag
        String tag = Integer.toString(_nextTag++);

        FieldTable arguments = FieldTableFactory.newFieldTable();
        if (messageSelector != null && !messageSelector.equals(""))
        {
            arguments.put(AMQPFilterTypes.JMS_SELECTOR.getValue(), messageSelector);
        }
        if(consumer.isAutoClose())
        {
            arguments.put(AMQPFilterTypes.AUTO_CLOSE.getValue(), Boolean.TRUE);
        }
        if(consumer.isNoConsume())
        {
            arguments.put(AMQPFilterTypes.NO_CONSUME.getValue(), Boolean.TRUE);
        }

        consumer.setConsumerTag(tag);
        // we must register the consumer in the map before we actually start listening
        _consumers.put(tag, consumer);

        try
        {
            // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            /*AMQFrame jmsConsume = BasicConsumeBody.createAMQFrame(_channelId,
                (byte)0, (byte)9,	// AMQP version (major, minor)
                arguments,	// arguments
                tag,	// consumerTag
                consumer.isExclusive(),	// exclusive
                consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE,	// noAck
                consumer.isNoLocal(),	// noLocal
                nowait,	// nowait
                queueName,	// queue
                0);	// ticket */
        	
            AMQMethodBody methodBody = MessageConsumeBody.createMethodBody(
        			                (byte)0, (byte)9,	// AMQP version (major, minor)
        							tag,	// consumerTag
				        			consumer.isExclusive(),	// exclusive
				        			arguments, // arguments in the form of a field table
				                    consumer.getAcknowledgeMode() == Session.NO_ACKNOWLEDGE,	// noAck
				                    consumer.isNoLocal(),	// noLocal
				                    queueName, // queue
				                    0); // ticket */
        	/*
            if (nowait)
            {
                protocolHandler.writeFrame(jmsConsume);
            }
            else
            {
            	protocolHandler.syncWrite(jmsConsume, BasicConsumeOkBody.class);
            }*/
            
            protocolHandler.syncWrite(_channelId, methodBody, MessageOkBody.class);
        }
        catch (AMQException e)
        {
            // clean-up the map in the event of an error
            _consumers.remove(tag);
            throw e;
        }
    }

    public Queue createQueue(String queueName) throws JMSException
    {
        checkNotClosed();
        if (queueName.indexOf('/') == -1)
        {
            return new AMQQueue(queueName);
        }
        else
        {
            try
            {
                return new AMQQueue(new AMQBindingURL(queueName));
            }
            catch (URLSyntaxException urlse)
            {
                JMSException jmse = new JMSException(urlse.getReason());
                jmse.setLinkedException(urlse);

                throw jmse;
            }
        }
    }

    /**
     * Creates a QueueReceiver wrapping a MessageConsumer
     *
     * @param queue
     * @return QueueReceiver
     * @throws JMSException
     */
    public QueueReceiver createReceiver(Queue queue) throws JMSException
    {
        checkNotClosed();
        AMQQueue dest = (AMQQueue) queue;
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(dest);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    /**
     * Creates a QueueReceiver wrapping a MessageConsumer using a message selector
     *
     * @param queue
     * @param messageSelector
     * @return QueueReceiver
     * @throws JMSException
     */
    public QueueReceiver createReceiver(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        AMQQueue dest = (AMQQueue) queue;
        BasicMessageConsumer consumer = (BasicMessageConsumer)
                createConsumer(dest, messageSelector);
        return new QueueReceiverAdaptor(dest, consumer);
    }

    public QueueSender createSender(Queue queue) throws JMSException
    {
        checkNotClosed();
        //return (QueueSender) createProducer(queue);
        return new QueueSenderAdapter(createProducer(queue), queue);
    }

    public Topic createTopic(String topicName) throws JMSException
    {
        checkNotClosed();

        if (topicName.indexOf('/') == -1)
        {
            return new AMQTopic(topicName);
        }
        else
        {
            try
            {
                return new AMQTopic(new AMQBindingURL(topicName));
            }
            catch (URLSyntaxException urlse)
            {
                JMSException jmse = new JMSException(urlse.getReason());
                jmse.setLinkedException(urlse);

                throw jmse;
            }
        }
    }

    /**
     * Creates a non-durable subscriber
     *
     * @param topic
     * @return TopicSubscriber - a wrapper round our MessageConsumer
     * @throws JMSException
     */
    public TopicSubscriber createSubscriber(Topic topic) throws JMSException
    {
        checkNotClosed();
        checkValidTopic(topic);
        AMQTopic dest = new AMQTopic(topic.getTopicName());
        return new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest));
    }

    /**
     * Creates a non-durable subscriber with a message selector
     *
     * @param topic
     * @param messageSelector
     * @param noLocal
     * @return TopicSubscriber - a wrapper round our MessageConsumer
     * @throws JMSException
     */
    public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal) throws JMSException
    {
        checkNotClosed();
        checkValidTopic(topic);
        AMQTopic dest = new AMQTopic(topic.getTopicName());
        return new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest, messageSelector, noLocal));
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException
    {
        checkNotClosed();
        checkValidTopic(topic);
        AMQTopic dest = AMQTopic.createDurableTopic((AMQTopic) topic, name, _connection);
        TopicSubscriberAdaptor subscriber = _subscriptions.get(name);
        if (subscriber != null)
        {
            if (subscriber.getTopic().equals(topic))
            {
                throw new IllegalStateException("Already subscribed to topic " + topic + " with subscription exchange " +
                                                name);
            }
            else
            {
                unsubscribe(name);
            }
        }
        else
        {
            // if the queue is bound to the exchange but NOT for this topic, then the JMS spec
            // says we must trash the subscription.
            if (isQueueBound(dest.getQueueName()) &&
                !isQueueBound(dest.getQueueName(), topic.getTopicName()))
            {
                deleteQueue(dest.getQueueName());
            }
        }

        subscriber = new TopicSubscriberAdaptor(dest, (BasicMessageConsumer) createConsumer(dest));

        _subscriptions.put(name, subscriber);
        _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);

        return subscriber;
    }

    void deleteQueue(String queueName) throws JMSException
    {
        try
        {
            // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
            // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
            // Be aware of possible changes to parameter order as versions change.
            AMQMethodBody methodBody = QueueDeleteBody.createMethodBody(
                (byte)0, (byte)9,	// AMQP version (major, minor)
                false,	// ifEmpty
                false,	// ifUnused
                true,	// nowait
                queueName,	// queue
                0);	// ticket
            _connection.getProtocolHandler().syncWrite(_channelId, methodBody, QueueDeleteOkBody.class);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
    }

    /**
     * Note, currently this does not handle reuse of the same name with different topics correctly.
     */
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal)
            throws JMSException
    {
        checkNotClosed();
        checkValidTopic(topic);
        AMQTopic dest = AMQTopic.createDurableTopic((AMQTopic) topic, name, _connection);
        BasicMessageConsumer consumer = (BasicMessageConsumer) createConsumer(dest, messageSelector, noLocal);
        TopicSubscriberAdaptor subscriber = new TopicSubscriberAdaptor(dest, consumer);
        _subscriptions.put(name, subscriber);
        _reverseSubscriptionMap.put(subscriber.getMessageConsumer(), name);
        return subscriber;
    }

    public TopicPublisher createPublisher(Topic topic) throws JMSException
    {
        checkNotClosed();
        return new TopicPublisherAdapter((BasicMessageProducer) createProducer(topic), topic);
    }

    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        return createBrowser(queue, null);
    }

    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException
    {
        checkNotClosed();
        checkValidQueue(queue);
        return new AMQQueueBrowser(this, (AMQQueue) queue,messageSelector);
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        checkNotClosed();
        return new AMQTemporaryQueue(this);
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        checkNotClosed();
        return new AMQTemporaryTopic(this);
    }

    public void unsubscribe(String name) throws JMSException
    {
        checkNotClosed();
        TopicSubscriberAdaptor subscriber = _subscriptions.get(name);
        if (subscriber != null)
        {
            // send a queue.delete for the subscription
            deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
            _subscriptions.remove(name);
            _reverseSubscriptionMap.remove(subscriber);
        }
        else
        {
            if (isQueueBound(AMQTopic.getDurableTopicQueueName(name, _connection)))
            {
                deleteQueue(AMQTopic.getDurableTopicQueueName(name, _connection));
            }
            else
            {
                throw new InvalidDestinationException("Unknown subscription exchange:" + name);
            }
        }
    }

    boolean isQueueBound(String queueName) throws JMSException
    {
        return isQueueBound(queueName, null);
    }

    boolean isQueueBound(String queueName, String routingKey) throws JMSException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ExchangeBoundBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            ExchangeDefaults.TOPIC_EXCHANGE_NAME,	// exchange
            queueName,	// queue
            routingKey);	// routingKey
        AMQMethodEvent response = null;
        try
        {
            response = _connection.getProtocolHandler().syncWrite(_channelId, methodBody, ExchangeBoundOkBody.class);
        }
        catch (AMQException e)
        {
            throw new JMSAMQException(e);
        }
        ExchangeBoundOkBody responseBody = (ExchangeBoundOkBody) response.getMethod();
        return (responseBody.replyCode == ExchangeBoundHandler.OK);
    }

    private void checkTransacted() throws JMSException
    {
        if (!getTransacted())
        {
            throw new IllegalStateException("Session is not transacted");
        }
    }

    private void checkNotTransacted() throws JMSException
    {
        if (getTransacted())
        {
            throw new IllegalStateException("Session is transacted");
        }
    }

    /**
     * Invoked by the MINA IO thread (indirectly) when a message is received from the transport.
     * Puts the message onto the queue read by the dispatcher.
     *
     * @param message the message that has been received
     */
    public void messageReceived(UnprocessedMessage message)
    {
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Message received in session with channel id " + _channelId);
        }

        _queue.add(message);
    }

    /**
     * Acknowledge a message or several messages. This method can be called via AbstractJMSMessage or from
     * a BasicConsumer. The former where the mode is CLIENT_ACK and the latter where the mode is
     * AUTO_ACK or similar.
     *
     * @param deliveryTag the tag of the last message to be acknowledged
     * @param multiple    if true will acknowledge all messages up to and including the one specified by the
     *                    delivery tag
     * @throws AMQException 
     */
    public void acknowledgeMessage(long requestId, boolean multiple) throws AMQException
    {
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        final AMQMethodBody methodBody = MessageOkBody.createMethodBody((byte)0, (byte)9);	// AMQP version (major, minor)
            //deliveryTag,	// deliveryTag
            //multiple);	// multiple
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Sending ack for request ID " + requestId + " on channel " + _channelId);
        }
        _connection.getProtocolHandler().writeResponse(_channelId, requestId, methodBody);
    }

    public int getDefaultPrefetch()
    {
        return _defaultPrefetchHighMark;
    }

    public int getDefaultPrefetchHigh()
    {
        return _defaultPrefetchHighMark;
    }

    public int getDefaultPrefetchLow()
    {
        return _defaultPrefetchLowMark;
    }

    public int getChannelId()
    {
        return _channelId;
    }

    void start()
    {
        if (_dispatcher != null)
        {
        	try{
            	//then we stopped this and are restarting, so signal server to resume delivery
        		unsuspendChannel();
	        }catch(AMQException e){
	        	_logger.error("Error Un Suspending Channel", e);
	        }
        }
        _dispatcher = new Dispatcher();
        _dispatcher.setDaemon(true);
        _dispatcher.start();
    }

    void stop()
    {
        //stop the server delivering messages to this session
        try{
    		suspendChannel();
        }catch(AMQException e){
        	_logger.error("Error Suspending Channel", e);
        }
        
//stop the dispatcher thread
        _stopped.set(true);
    }

    boolean isStopped()
    {
        return _stopped.get();
    }

    /**
     * Callers must hold the failover mutex before calling this method.
     *
     * @param consumer
     * @throws AMQException
     */
    void registerConsumer(BasicMessageConsumer consumer, boolean nowait) throws AMQException
    {
        AMQDestination amqd = consumer.getDestination();

        AMQProtocolHandler protocolHandler = _connection.getProtocolHandler();

        declareExchange(amqd, protocolHandler);

        String queueName = declareQueue(amqd, protocolHandler);

        bindQueue(amqd, queueName, protocolHandler, consumer.getRawSelectorFieldTable());

        try
        {
            consumeFromQueue(consumer, queueName, protocolHandler, nowait, consumer.getMessageSelector());
        }
        catch (JMSException e) //thrown by getMessageSelector
        {
            throw new AMQException(e.getMessage(), e);
        }
    }

    /**
     * Called by the MessageConsumer when closing, to deregister the consumer from the
     * map from consumerTag to consumer instance.
     *
     * @param consumer the consum
     */
    void deregisterConsumer(BasicMessageConsumer consumer)
    {
        _consumers.remove(consumer.getConsumerTag());
        String subscriptionName = _reverseSubscriptionMap.remove(consumer);
        if (subscriptionName != null)
        {
            _subscriptions.remove(subscriptionName);
        }

        Destination dest = consumer.getDestination();
        synchronized(dest)
        {
            if (_destinationConsumerCount.get(dest).decrementAndGet() == 0)
            {
                _destinationConsumerCount.remove(dest);
            }
        }
    }

    private void registerProducer(long producerId, MessageProducer producer)
    {
        _producers.put(new Long(producerId), producer);
    }

    void deregisterProducer(long producerId)
    {
        _producers.remove(new Long(producerId));
    }

    private long getNextProducerId()
    {
        return ++_nextProducerId;
    }

    /**
     * Resubscribes all producers and consumers. This is called when performing failover.
     *
     * @throws AMQException
     */
    void resubscribe() throws AMQException
    {
        resubscribeProducers();
        resubscribeConsumers();
    }

    private void resubscribeProducers() throws AMQException
    {
        ArrayList producers = new ArrayList(_producers.values());
        _logger.info(MessageFormat.format("Resubscribing producers = {0} producers.size={1}", producers, producers.size())); // FIXME: remove
        for (Iterator it = producers.iterator(); it.hasNext();)
        {
            BasicMessageProducer producer = (BasicMessageProducer) it.next();
            producer.resubscribe();
        }
    }

    private void resubscribeConsumers() throws AMQException
    {
        ArrayList consumers = new ArrayList(_consumers.values());
        _consumers.clear();

        for (Iterator it = consumers.iterator(); it.hasNext();)
        {
            BasicMessageConsumer consumer = (BasicMessageConsumer) it.next();
            registerConsumer(consumer, true);
        }
    }

    private void suspendChannel() throws AMQException
    {
        _logger.warn("Suspending channel");
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ChannelFlowBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            false);	// active
        _connection.getProtocolHandler().writeRequest(_channelId, methodBody);
    }

    private void unsuspendChannel() throws AMQException
    {
        _logger.warn("Unsuspending channel");
        // AMQP version change: Hardwire the version to 0-9 (major=0, minor=9)
        // TODO: Connect this to the session version obtained from ProtocolInitiation for this session.
        // Be aware of possible changes to parameter order as versions change.
        AMQMethodBody methodBody = ChannelFlowBody.createMethodBody(
            (byte)0, (byte)9,	// AMQP version (major, minor)
            true);	// active
        _connection.getProtocolHandler().writeRequest(_channelId, methodBody);
    }

    public void confirmConsumerCancelled(String consumerTag)
    {
        BasicMessageConsumer consumer = (BasicMessageConsumer) _consumers.get(consumerTag);
        if((consumer != null) && (consumer.isAutoClose()))
        {
            consumer.closeWhenNoMessages(true);
        }
    }


    /*
     * I could have combined the last 3 methods, but this way it improves readability
     */
    private void checkValidTopic(Topic topic) throws JMSException
    {
        if (topic == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Topic");
        }
        if ((topic instanceof TemporaryDestination) && ((TemporaryDestination) topic).getSession() != this)
        {
            throw new JMSException("Cannot create a subscription on a temporary topic created in another session");
        }
    }

    private void checkValidQueue(Queue queue) throws InvalidDestinationException
    {
        if (queue == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }

    private void checkValidDestination(Destination destination) throws InvalidDestinationException
    {
        if (destination == null)
        {
            throw new javax.jms.InvalidDestinationException("Invalid Queue");
        }
    }

}
