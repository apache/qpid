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

import org.apache.qpidity.njms.message.MessageHelper;
import org.apache.qpidity.njms.message.MessageImpl;
import org.apache.qpidity.QpidException;

import javax.jms.*;
import java.util.UUID;
import java.io.IOException;

/**
 * Implements  MessageProducer
 */
public class MessageProducerImpl extends MessageActor implements MessageProducer
{
    /**
     * If true, messages will not get a timestamp.
     */
    private boolean _disableTimestamps = false;

    /**
     * Priority of messages created by this producer.
     */
    private int _messagePriority = Message.DEFAULT_PRIORITY;

    /**
     * Time to live of messages. Specified in milliseconds but AMQ has 1 second resolution.
     */
    private long _timeToLive;

    /**
     * Delivery mode used for this producer.
     */
    private int _deliveryMode = DeliveryMode.PERSISTENT;

    /**
     * Speicify whether the messageID is disable
     */
    private boolean _disableMessageId = false;

    //-- constructors
    public MessageProducerImpl(SessionImpl session, DestinationImpl destination)
    {
        super(session, destination,"");
    }

    //--- Interface javax.njms.MessageProducer
    /**
     * Sets whether message IDs are disabled.
     *
     * @param value Specify whether the MessageID must be disabled
     * @throws JMSException If disabling messageID fails due to some internal error.
     */
    public void setDisableMessageID(boolean value) throws JMSException
    {
        checkNotClosed();
        _disableMessageId = value;
    }

    /**
     * Gets an indication of whether message IDs are disabled.
     *
     * @return true is messageID is disabled, false otherwise
     * @throws JMSException If getting whether messagID is disabled fails due to some internal error.
     */
    public boolean getDisableMessageID() throws JMSException
    {
        checkNotClosed();
        return _disableMessageId;
    }

    /**
     * Sets whether message timestamps are disabled.
     * <P> JMS spec says:
     * <p> Since timestamps take some effort to create and increase a
     * message's size, some JMS providers may be able to optimize message
     * overhead if they are given a hint that the timestamp is not used by an
     * application....
     * these messages must have the timestamp set to zero; if the provider
     * ignores the hint, the timestamp must be set to its normal value.
     * <p>Message timestamps are enabled by default.
     *
     * @param value Indicates if message timestamps are disabled
     * @throws JMSException if disabling the timestamps fails due to some internal error.
     */
    public void setDisableMessageTimestamp(boolean value) throws JMSException
    {
        checkNotClosed();
        _disableTimestamps = value;
    }

    /**
     * Gets an indication of whether message timestamps are disabled.
     *
     * @return an indication of whether message timestamps are disabled
     * @throws JMSException if getting whether timestamps are disabled fails due to some internal error.
     */
    public boolean getDisableMessageTimestamp() throws JMSException
    {
        checkNotClosed();
        return _disableTimestamps;
    }

    /**
     * Sets the producer's default delivery mode.
     * <p> JMS specification says:
     * <p>Delivery mode is set to {@link DeliveryMode#PERSISTENT} by default.
     *
     * @param deliveryMode The message delivery mode for this message producer; legal
     *                     values are {@link DeliveryMode#NON_PERSISTENT}
     *                     and {@link DeliveryMode#PERSISTENT}.
     * @throws JMSException if setting the delivery mode fails due to some internal error.
     */
    public void setDeliveryMode(int deliveryMode) throws JMSException
    {
        checkNotClosed();
        if ((deliveryMode != DeliveryMode.NON_PERSISTENT) && (deliveryMode != DeliveryMode.PERSISTENT))
        {
            throw new JMSException(
                    "DeliveryMode must be either NON_PERSISTENT or PERSISTENT. Value of " + deliveryMode + " is illegal");
        }
        _deliveryMode = deliveryMode;
    }

    /**
     * Gets the producer's delivery mode.
     *
     * @return The message delivery mode for this message producer
     * @throws JMSException If getting the delivery mode fails due to some internal error.
     */
    public int getDeliveryMode() throws JMSException
    {
        checkNotClosed();
        return _deliveryMode;
    }

    /**
     * Sets the producer's message priority.
     * <p> The njms spec says:
     * <p> The JMS API defines ten levels of priority value, with 0 as the
     * lowest priority and 9 as the highest. Clients should consider priorities
     * 0-4 as gradations of normal priority and priorities 5-9 as gradations
     * of expedited priority.
     * <p> Priority is set to 4 by default.
     *
     * @param priority The message priority for this message producer; must be a value between 0 and 9
     * @throws JMSException if setting this producer priority fails due to some internal error.
     */
    public void setPriority(int priority) throws JMSException
    {
        checkNotClosed();
        if ((priority < 0) || (priority > 9))
        {
            throw new IllegalArgumentException(
                    "Priority of " + priority + " is illegal. Value must be in range 0 to 9");
        }
        _messagePriority = priority;
    }

    /**
     * Gets the producer's message priority.
     *
     * @return The message priority for this message producer.
     * @throws JMSException If getting this producer message priority fails due to some internal error.
     */
    public int getPriority() throws JMSException
    {
        checkNotClosed();
        return _messagePriority;
    }

    /**
     * Sets the default length of time in milliseconds from its dispatch time
     * that a produced message should be retained by the message system.
     * <p> The JMS spec says that time to live must be set to zero by default.
     *
     * @param timeToLive The message time to live in milliseconds; zero is unlimited
     * @throws JMSException If setting the default time to live fails due to some internal error.
     */
    public void setTimeToLive(long timeToLive) throws JMSException
    {
        checkNotClosed();
        if (timeToLive < 0)
        {
            throw new IllegalArgumentException("Time to live must be non-negative - supplied value was " + timeToLive);
        }
        _timeToLive = timeToLive;
    }

    /**
     * Gets the default length of time in milliseconds from its dispatch time
     * that a produced message should be retained by the message system.
     *
     * @return The default message time to live in milliseconds; zero is unlimited
     * @throws JMSException if getting the default time to live fails due to some internal error.
     * @see javax.jms.MessageProducer#setTimeToLive
     */
    public long getTimeToLive() throws JMSException
    {
        checkNotClosed();
        return _timeToLive;
    }

    /**
     * Gets the destination associated with this producer.
     *
     * @return This producer's destination.
     * @throws JMSException If getting the destination for this producer fails
     *                      due to some internal error.
     */
    public Destination getDestination() throws JMSException
    {
        checkNotClosed();
        return _destination;
    }

    /**
     * Sends a message using the producer's default delivery mode, priority, destination
     * and time to live.
     *
     * @param message the message to be sent
     * @throws JMSException                If sending the message fails due to some internal error.
     * @throws MessageFormatException      If an invalid message is specified.
     * @throws InvalidDestinationException If this producer destination is invalid.
     * @throws java.lang.UnsupportedOperationException
     *                                     If a client uses this method with a producer that did
     *                                     not specify a destination at creation time.
     */
    public void send(Message message) throws JMSException
    {
        send(message, _deliveryMode, _messagePriority, _timeToLive);
    }

    /**
     * Sends a message to this producer default destination, specifying delivery mode,
     * priority, and time to live.
     *
     * @param message      The message to send
     * @param deliveryMode The delivery mode to use
     * @param priority     The priority for this message
     * @param timeToLive   The message's lifetime (in milliseconds)
     * @throws JMSException                If sending the message fails due to some internal error.
     * @throws MessageFormatException      If an invalid message is specified.
     * @throws InvalidDestinationException If this producer's destination is invalid.
     * @throws java.lang.UnsupportedOperationException
     *                                     If a client uses this method with a producer that did
     *                                     not specify a destination at creation time.
     */
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
    {
        send(_destination, message, deliveryMode, priority, timeToLive);
    }

    /**
     * Sends a message to a specified destination using this producer's default
     * delivery mode, priority and time to live.
     * <p/>
     * <P>Typically, a message producer is assigned a destination at creation
     * time; however, the JMS API also supports unidentified message producers,
     * which require that the destination be supplied every time a message is
     * sent.
     *
     * @param destination The destination to send this message to
     * @param message     The message to send
     * @throws JMSException                If sending the message fails due to some internal error.
     * @throws MessageFormatException      If an invalid message is specified.
     * @throws InvalidDestinationException If an invalid destination is specified.
     */
    public void send(Destination destination, Message message) throws JMSException
    {
        send(destination, message, _deliveryMode, _messagePriority, _timeToLive);
    }

    /**
     * Sends a message to a destination specifying delivery mode, priority and time to live.
     *
     * @param destination  The destination to send this message to.
     * @param message      The message to be sent.
     * @param deliveryMode The delivery mode to use.
     * @param priority     The priority for this message.
     * @param timeToLive   The message's lifetime (in milliseconds)
     * @throws JMSException                If sending the message fails due to some internal error.
     * @throws MessageFormatException      If an invalid message is specified.
     * @throws InvalidDestinationException If an invalid destination is specified.
     */
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive)
            throws JMSException
    {
        checkNotClosed();
        getSession().checkDestination(destination);
        // Do not allow negative timeToLive values
        if (timeToLive < 0)
        {
            throw new IllegalArgumentException("Time to live must be non-negative - supplied value was " + timeToLive);
        }
        // Only get current time if required
        long currentTime = Long.MIN_VALUE;
        if (!((timeToLive == 0) && _disableTimestamps))
        {
            currentTime = System.currentTimeMillis();
        }
        // the messae UID
        String uid = (_disableMessageId) ? "MSG_ID_DISABLED" : UUID.randomUUID().toString();
        MessageImpl qpidMessage;
        // check that the message is not a foreign one
        try
        {
            qpidMessage = (MessageImpl) message;
        }
        catch (ClassCastException cce)
        {
            // this is a foreign message
            qpidMessage = MessageHelper.transformMessage(message);
            // set message's properties in case they are queried after send.
            message.setJMSDestination(destination);
            message.setJMSDeliveryMode(deliveryMode);
            message.setJMSPriority(priority);
            message.setJMSMessageID(uid);
            if (timeToLive != 0)
            {
                message.setJMSExpiration(timeToLive + currentTime);
                _logger.debug("Setting JMSExpiration:" + message.getJMSExpiration());
            }
            else
            {
                message.setJMSExpiration(timeToLive);
            }
            message.setJMSTimestamp(currentTime);
        }
        // set the message properties
        qpidMessage.setJMSDestination(destination);
        qpidMessage.setJMSMessageID(uid);
        qpidMessage.setJMSDeliveryMode(deliveryMode);
        qpidMessage.setJMSPriority(priority);
        if (timeToLive != 0)
        {
            qpidMessage.setJMSExpiration(timeToLive + currentTime);
        }
        else
        {
            qpidMessage.setJMSExpiration(timeToLive);
        }
        qpidMessage.setJMSTimestamp(currentTime);
        qpidMessage.setRoutingKey(((DestinationImpl) destination).getDestinationName());
        qpidMessage.setExchangeName(((DestinationImpl) destination).getExchangeName());
        // call beforeMessageDispatch
        try
        {
            qpidMessage.beforeMessageDispatch();
        }
        catch (QpidException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
        try
        {
            getSession().getQpidSession().messageTransfer(qpidMessage.getExchangeName(),
                                                          qpidMessage.getQpidityMessage(),
                                                          org.apache.qpidity.nclient.Session.TRANSFER_CONFIRM_MODE_NOT_REQUIRED,
                                                          org.apache.qpidity.nclient.Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE);
        }
        catch (IOException e)
        {
            throw ExceptionHelper.convertQpidExceptionToJMSException(e);
        }
    }
}
