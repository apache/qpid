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
package org.apache.qpid.nclient.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpid.nclient.exception.QpidException;
import org.apache.qpid.nclient.jms.message.*;

import javax.jms.*;
import javax.jms.IllegalStateException;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Vector;

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
     * The messageConsumers of this session.
     */
    private ArrayList<MessageConsumerImpl> _messageConsumers = new ArrayList<MessageConsumerImpl>();

    /**
     * The messageProducers of this session.
     */
    private ArrayList<MessageProducerImpl> _messageProducers = new ArrayList<MessageProducerImpl>();

    /**
     * All the not yet acknoledged messages
     * We use a vector as access to this list has to be synchronised
     * This is because messages are acked from messagelistner threads
     */
    private Vector<QpidMessage> _unacknowledgedMessages = new Vector<QpidMessage>();

    /**
     * Indicates whether this session is closed.
     */
    private boolean _isClosed = false;

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
    private org.apache.qpid.nclient.api.Session _qpidSession;

    /**
     * Indicates whether this session is recovering
     */
    private boolean _inRecovery = false;

    //--- javax.jms.Session API

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
        return new JMSBytesMessage();
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
        return new JMSMapMessage();
    }

    /**
     * Creates a <CODE>Message</CODE> object that holds all the
     * standard message header information. It can be sent when a message
     * containing only header information is sufficient.
     * We simply return a ByteMessage
     *
     * @return A Message.
     * @throws JMSException If Creating a Message object fails due to some internal error.
     */
    public Message createMessage() throws JMSException
    {
        return createBytesMessage();
    }

    /**
     * Creates an <CODE>ObjectMessage</CODE> used to send a message
     * that contains a serializable Java object.
     *
     * @return An ObjectMessage.
     * @throws JMSException If Creating an ObjectMessage object fails due to some internal error.
     */
    public ObjectMessage createObjectMessage() throws JMSException
    {
        checkNotClosed();
        return new JMSObjectMessage();
    }

    /**
     * Creates an initialized <CODE>ObjectMessage</CODE> used to send a message that contains
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
     * Creates a <CODE>StreamMessage</CODE>  used to send a
     * self-defining stream of primitive values in the Java programming
     * language.
     *
     * @return A StreamMessage
     * @throws JMSException If Creating an StreamMessage object fails due to some internal error.
     */
    public StreamMessage createStreamMessage() throws JMSException
    {
        checkNotClosed();
        return new JMSStreamMessage();
    }

    /**
     * Creates a <CODE>TextMessage</CODE> object used to send a message containing a String.
     *
     * @return A TextMessage object
     * @throws JMSException If Creating an TextMessage object fails due to some internal error.
     */
    public TextMessage createTextMessage() throws JMSException
    {
        checkNotClosed();
        return new JMSTextMessage();
    }

    /**
     * Creates an initialized <CODE>TextMessage</CODE>  used to send
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
            throw new IllegalStateException("Cannot commit non-transacted session", "Session is not transacted");
        }
        // commit the underlying Qpid Session
        try
        {
            // Note: this operation makes sure that asynch message processing has returned
            _qpidSession.commit();
        }
        catch (org.apache.qpidity.QpidException e)
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
            throw new IllegalStateException("Cannot rollback non-transacted session", "Session is not transacted");
        }
        // rollback the underlying Qpid Session
        try
        {
            // Note: this operation makes sure that asynch message processing has returned
            _qpidSession.rollback();
        }
        catch (org.apache.qpidity.QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }

    /**
     * Closes this session.
     * <p> The JMS specification says
     * <P> This call will block until a <CODE>receive</CODE> call or message
     * listener in progress has completed. A blocked message consumer
     * <CODE>receive</CODE> call returns <CODE>null</CODE> when this session is closed.
     * <P>Closing a transacted session must roll back the transaction in progress.
     * <P>This method is the only <CODE>Session</CODE> method that can be called concurrently.
     * <P>Invoking any other <CODE>Session</CODE> method on a closed session
     * must throw a <CODE>javax.jms.IllegalStateException</CODE>.
     * <p> Closing a closed session must <I>not</I> throw an exception.
     *
     * @throws JMSException If closing the session fails due to some internal error.
     */
    public synchronized void close() throws JMSException
    {
        if (!_isClosed)
        {
            // from now all the session methods will throw a IllegalStateException
            _isClosed = true;
            // close all the actors
            closeAllActors();
            // close the underlaying QpidSession
            try
            {
                _qpidSession.sessionClose();
            }
            catch ( org.apache.qpidity.QpidException e)
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
            throw new IllegalStateException("Session is transacted");
        }
        // release all unack messages
        for(QpidMessage message : _unacknowledgedMessages)
        {
            // release all those messages
            //Todo: message.getQpidMEssage.release();
        }
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
        throw new java.lang.UnsupportedOperationException();
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
        throw new java.lang.UnsupportedOperationException();
    }

    /**
     * Optional operation, intended to be used only by Application Servers,
     * not by ordinary JMS clients.
     * <p> This is an optional operation that is not yet supported
     */
    public void run()
    {
        throw new java.lang.UnsupportedOperationException();
    }

    public MessageProducer createProducer(Destination destination) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public MessageConsumer createConsumer(Destination destination) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public MessageConsumer createConsumer(Destination destination, String string) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public MessageConsumer createConsumer(Destination destination, String string, boolean b) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Queue createQueue(String string) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Topic createTopic(String string) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String string) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TopicSubscriber createDurableSubscriber(Topic topic, String string, String string1, boolean b) throws
                                                                                                          JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public QueueBrowser createBrowser(Queue queue) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public QueueBrowser createBrowser(Queue queue, String string) throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TemporaryQueue createTemporaryQueue() throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public TemporaryTopic createTemporaryTopic() throws JMSException
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

   /**
    * Unsubscribes a durable subscription that has been created by a client.
    *
    * <P>This method deletes the state being maintained on behalf of the
    * subscriber by its provider.
    *
    * <P>It is erroneous for a client to delete a durable subscription
    * while there is an active <CODE>TopicSubscriber</CODE> for the
    * subscription, or while a consumed message is part of a pending
    * transaction or has not been acknowledged in the session.
    *
    * @param name the name used to identify this subscription
    *
    * @exception JMSException if the session fails to unsubscribe to the durable subscription due to some internal error.
    * @exception InvalidDestinationException if an invalid subscription name
    *                                        is specified.
    */
   public void unsubscribe(String name) throws JMSException
   {
      checkNotClosed();

   }

    //----- Protected methods
    /**
     * Notify this session that a message is processed
     * @param message The processed message.
     */
    protected void preProcessMessage(QpidMessage message)
    {
        _inRecovery = false;
    }

    /**
     * Indicate whether this session is recovering .
     *
     * @return true if this session is recovering.
     */
    protected  boolean isInRecovery()
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
            _unacknowledgedMessages.add(message);
        }
        else
        {
            // acknowledge this message
            // TODO: message.acknowledgeQpidMessge();
        }
        //TODO: Implement DUPS OK heuristic
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
    protected void acknowledge() throws JMSException
    {
        checkNotClosed();
        if (getAcknowledgeMode() == Session.CLIENT_ACKNOWLEDGE)
        {
            for (QpidMessage message : _unacknowledgedMessages)
            {
                // acknowledge this message
                // TODO: message.acknowledgeQpidMessge();
            }
            //empty the list of unack messages
            _unacknowledgedMessages.clear();
        }
        //else there is no effect
    }

    //------ Private Methods

    /**
     * Close the producer and the consumers of this session
     *
     * @throws JMSException If one of the MessaeActor cannot be closed due to some internal error.
     */
    private void closeAllActors() throws JMSException
    {
        for (MessageActor messageActor : _messageProducers)
        {
            messageActor.closeMessageActor();
        }
        for (MessageActor messageActor : _messageConsumers)
        {
            messageActor.closeMessageActor();
        }
    }
}
