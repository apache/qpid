/*
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
 */
package org.apache.qpid.nclient;

import java.util.Map;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;
import org.apache.qpidity.Header;
import org.apache.qpidity.Range;
import org.apache.qpidity.api.Message;

/**
 * <p>A session is associated with a connection.
 * When created a Session is not attached with an underlying channel.
 * Session is single threaded </p>
 */
public interface Session
{
    public static final short ACQUIRE_MODE_NO_ACQUIRE = 0;
    public static final short ACQUIRE_MODE_PRE_ACQUIRE = 1;
    public static final short CONFIRM_MODE_REQUIRED = 1;
    public static final short CONFIRM_MODE_NOT_REQUIRED = 0;
    public static final short MESSAGE_FLOW_MODE_CREDIT = 0;
    public static final short MESSAGE_FLOW_MODE_WINDOW = 1;
    public static final short MESSAGE_FLOW_UNIT_MESSAGE = 0;
    public static final short MESSAGE_FLOW_UNIT_BYTE = 1;

    //------------------------------------------------------
    //                 Session housekeeping methods
    //------------------------------------------------------
    /**
     * Close this session and any associated resources.
     *
     * @throws QpidException If the communication layer fails to close this session or if an internal error happens
     *                       when closing this session resources. .
     */
    public void close() throws QpidException;

    /**
     * Suspend this session resulting in interrupting the traffic with the broker.
     * <p> The session timer will start to tick in suspend.
     * <p> When a session is suspend any operation of this session and of the associated resources are unavailable.
     *
     * @throws QpidException If the communication layer fails to suspend this session
     */
    public void suspend() throws QpidException;

    /**
     * This will resume an existing session
     * <p> Upon resume the session is attached with an underlying channel
     * hence making operation on this session available.
     *
     * @throws QpidException If the communication layer fails to execute this properly
     */
    public void resume() throws QpidException;

    //------------------------------------------------------ 
    //                 Messaging methods
    //                   Producer           
    //------------------------------------------------------
    /**
     * Transfer the given message to a specified exchange.
     * <p> Following are the valid options for messageTransfer
     * <ul>
     * <li> CONFIRM
     * <li> PRE_ACCQUIRE
     * </ul>
     * <p> In the absence of a particular option, the defaul value is:
     * <ul>
     * <li> CONFIRM = false
     * <li> NO-ACCQUIRE
     * </ul>
     *
     * @param exchange The exchange the message is being sent.
     * @param msg      The Message to be sent
     * @param options  A list of valid options
     * @throws QpidException If the session fails to send the message due to some error
     */
    public void messageTransfer(String exchange, Message msg, Option... options) throws QpidException;

    /**
     * Declare the beginning of a message transfer operation. This operation must
     * be followed by {@link Session#addMessageHeaders} then followed by any number of {@link Session#addData}.
     * The transfer is ended by endData.
     * <p> This way of transferring messages is useful when streaming large messages
     * <p> In the interval [messageTransfer endData] any attempt to call a method other than
     * {@link Session#addMessageHeaders}, {@link Session#endData} ore {@link Session#close}
     * will result in an exception being thrown.
     * <p> Following are the valid options for messageTransfer
     * <ul>
     * <li> CONFIRM
     * <li> PRE_ACCQUIRE
     * </ul>
     * <p> In the absence of a particular option, the defaul value is:
     * <ul>
     * <li> CONFIRM = false
     * <li> NO-ACCQUIRE
     * </ul>
     *
     * @param exchange The exchange the message is being sent.
     * @param options  Set of options.
     * @throws QpidException If the session fails to send the message due to some error.
     */
    public void messageTransfer(String exchange, Option... options) throws QpidException;

    /**
     * Add the following headers ( {@link org.apache.qpidity.DeliveryProperties}
     * or {@link org.apache.qpidity.ApplicationProperties} ) to the message being sent.
     *
     * @param headers Either <code>DeliveryProperties</code> or <code>ApplicationProperties</code>
     * @throws QpidException If the session fails to execute the method due to some error
     * @see org.apache.qpidity.DeliveryProperties
     * @see org.apache.qpidity.ApplicationProperties
     */
    public void addMessageHeaders(Header... headers) throws QpidException;

    /**
     * Add the following byte array to the content of the message being sent.
     *
     * @param data Data to be added.
     * @param off  Offset from which to start reading data
     * @param len  Number of bytes to be read
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void addData(byte[] data, int off, int len) throws QpidException;

    /**
     * Signals the end of data for the message.
     *
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void endData() throws QpidException;

    //------------------------------------------------------
    //                 Messaging methods
    //                   Consumer
    //------------------------------------------------------

    /**
     * Associate a message listener with a destination.
     * <p> The destination is bound to a queue and messages are filtered based
     * on the provider filter map (message filtering is specific to the provider and may not be handled).
     * <p/>
     * <p> Following are the valid options
     * <ul>
     * <li> NO_LOCAL
     * <li> EXCLUSIVE
     * </ul>
     * <p> In the absence of a particular option, defaul values are:
     * <ul>
     * <li> NO_LOCAL = false
     * <li> EXCLUSIVE = false
     * </ul>
     *
     * @param queue       The queue this receiver is receiving messages from.
     * @param destination The destination for the subscriber ,a.k.a the delivery tag.
     * @param confirmMode <ul> </li>off (0): confirmation is not required, once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred
     *                    <p/>
     *                    <li> on  (1): an acquired message (whether acquisition was implicit as in pre-acquire mode or
     *                    explicit as in no-acquire mode) is not considered transferred until the original
     *                    transfer is complete (signaled via execution.complete)
     *                    </ul>
     * @param acquireMode <ul> <li> no-acquire  (0): the message must be explicitly acquired
     *                    <p/>
     *                    <li> pre-acquire (1): the message is acquired when the transfer starts
     *                    </ul>
     * @param listener    The listener for this destination. When big message are transfered then
     *                    it is recommended to use a {@link MessagePartListener}.
     * @param options     Set of Options.
     * @param filter      A set of filters for the subscription. The syntax and semantics of these filters depends
     *                    on the providers implementation.
     * @throws QpidException If the session fails to create the receiver due to some error.
     */
    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode,
                                 MessagePartListener listener, Map<String, ?> filter, Option... options)
            throws QpidException;

    /**
     * This method cancels a consumer. This does not affect already delivered messages, but it does
     * mean the server will not send any more messages for that destination. The client may receive an
     * arbitrary number of messages in between sending the cancel method and receiving the
     * notification of completion of the cancel command.
     *
     * @param destination The destination for the subscriber used at subscription
     * @throws QpidException If cancelling the subscription fails due to some error.
     */
    public void messageCancel(String destination) throws QpidException;

    /**
     * Associate a message part listener with a destination.
     * We currently allow one listerner per destination this means
     * that the previous message listener is replaced. This is done gracefully i.e. the message
     * listener is replaced once it return from the processing of a message.
     *
     * @param destination The destination the listener is associated with.
     * @param listener    The new listener for this destination.
     */
    public void setMessageListener(String destination, MessagePartListener listener);

    /**
     * Sets the mode of flow control used for a given destination.
     * <p/>
     * With credit based flow control, the broker continually maintains its current
     * credit balance with the recipient. The credit balance consists of two values, a message
     * count, and a byte count. Whenever message data is sent, both counts must be decremented.
     * If either value reaches zero, the flow of message data must stop. Additional credit is
     * received via the {@link Session#messageFlow} method.
     * <p/>
     * Window based flow control is identical to credit based flow control, however message
     * acknowledgment implicitly grants a single unit of message credit, and the size of the
     * message in byte credits for each acknowledged message.
     *
     * @param destination The destination to set the flow mode on.
     * @param mode        <ul> <li>credit (0): choose credit based flow control
     *                    <li> window (1): choose window based flow control</ul>
     * @throws QpidException If setting the flow mode fails due to some error.
     */
    public void messageFlowMode(String destination, short mode) throws QpidException;


    /**
     * This method controls the flow of message data to a given destination. It is used by the
     * recipient of messages to dynamically match the incoming rate of message flow to its
     * processing or forwarding capacity. Upon receipt of this method, the sender must add "value"
     * number of the specified unit to the available credit balance for the specified destination.
     * A value of 0 indicates an infinite amount of credit. This disables any limit for
     * the given unit until the credit balance is zeroed with {@link Session#messageStop}
     * or {@link Session#messageFlush}.
     *
     * @param destination The destination to set the flow.
     * @param unit        Specifies the unit of credit balance.
     *                    <p/>
     *                    One of: <ul>
     *                    <li> message (0)
     *                    <li> byte    (1)
     *                    </ul>
     * @param value       Number of credits, a value of 0 indicates an infinite amount of credit.
     * @throws QpidException If setting the flow fails due to some error.
     */
    public void messageFlow(String destination, short unit, long value) throws QpidException;

    /**
     * Forces the broker to exhaust its credit supply.
     * <p> The broker's credit will always be zero when
     * this method completes. This method does not complete until all the message transfers occur.
     * <p> This method returns true if messages have been flushed
     * (i.e. the queue was not empty and the credit greater then zero).
     * It returns false if the queue was empty.
     *
     * @param destination The destination to call flush on.
     * @return True is messages were flushed, false otherwise.
     * @throws QpidException If flushing fails due to some error.
     */
    public boolean messageFlush(String destination) throws QpidException;

    /**
     * On receipt of this method, the brokers MUST set his credit to zero for the given
     * destination. This obeys the generic semantics of command completion, i.e. when confirmation
     * is issued credit MUST be zero and no further messages will be sent until such a time as
     * further credit is received.
     *
     * @param destination The destination to stop.
     * @throws QpidException If stopping fails due to some error.
     */
    public void messageStop(String destination) throws QpidException;

    /**
     * Acknowledge the receipt of ranges of messages.
     * <p>Message must have been previously acquired either by receiving them in
     * pre-acquire mode or by explicitly acquiring them.
     *
     * @param range Range of acknowledged messages.
     * @throws QpidException If the acknowledgement of the messages fails due to some error.
     */
    public void messageAcknowledge(Range<Long>... range) throws QpidException;

    /**
     * Reject ranges of acquired messages.
     * <p> A rejected message will not be delivered to any receiver
     * and may be either discarded or moved to the broker dead letter queue.
     *
     * @param range Range of rejected messages.
     * @throws QpidException If those messages cannot be rejected dus to some error
     */
    public void messageReject(Range<Long>... range) throws QpidException;

    /**
     * Try to acquire ranges of messages hence releasing them form the queue.
     * This means that once acknowledged, a message will not be delivered to any other receiver.
     * <p> As those messages may have been consumed by another receivers hence,
     * message acquisition can fail.
     * The outcome of the acquisition is returned as an array of ranges of qcquired messages.
     * <p> This method should only be called on non-acquired messages.
     *
     * @param range Ranges of messages to be acquired.
     * @return Ranges of explicitly acquired messages.
     * @throws QpidException If this message cannot be acquired dus to some error
     */
    public Range<Long>[] messageAcquire(Range<Long>... range) throws QpidException;

    /**
     * Give up responsibility for processing ranges of messages.
     * <p> Released messages are re-enqueued.
     *
     * @param range Ranges of messages to be released.
     * @throws QpidException If this message cannot be released dus to some error.
     */
    public void messageRelease(Range<Long>... range) throws QpidException;

    // -----------------------------------------------
    //            Local transaction methods
    //  ----------------------------------------------
    /**
     * Selects the session for local transaction support.
     *
     * @throws QpidException If selecting this session for local transaction support fails due to some error.
     */
    public void txSelect() throws QpidException;

    /**
     * Commit the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws QpidException         If the session fails to commit due to some error.
     * @throws IllegalStateException If this session is not transacted.
     */
    public void txCommit() throws QpidException, IllegalStateException;

    /**
     * Rollback the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws QpidException         If the session fails to rollback due to some error.
     * @throws IllegalStateException If this session is not transacted.
     */
    public void txRollback() throws QpidException, IllegalStateException;

    //---------------------------------------------
    //            Queue methods 
    //---------------------------------------------

    /**
     * Declare a queue with the given queueName
     * <p> Following are the valid options for declareQueue
     * <ul>
     * <li> AUTO_DELETE
     * <li> DURABLE
     * <li> EXCLUSIVE
     * <li> NO_WAIT
     * <li> PASSIVE
     * </ul>
     * </p>
     * <p/>
     * <p>In the absence of a particular option, the defaul value is false for each option
     *
     * @param queueName         The name of the delcared queue.
     * @param alternateExchange Alternate excahnge.
     * @param options           Set of Options.
     * @throws QpidException If the session fails to declare the queue due to some error.
     * @see Option
     */
    public void queueDeclare(String queueName, String alternateExchange, Map<String, ?> arguments, Option... options)
            throws QpidException;

    /**
     * Bind a queue with an exchange.
     *
     * @param queueName    The queue to be bound.
     * @param exchangeName The exchange name.
     * @param routingKey   The routing key.
     * @throws QpidException If the session fails to bind the queue due to some error.
     */
    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
            throws QpidException;

    /**
     * Unbind a queue from an exchange.
     *
     * @param queueName    The queue to be unbound.
     * @param exchangeName The exchange name.
     * @param routingKey   The routing key.
     * @throws QpidException If the session fails to unbind the queue due to some error.
     */
    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments)
            throws QpidException;

    /**
     * Purge a queue. i.e. delete all enqueued messages
     *
     * @param queueName The queue to be purged
     * @throws QpidException If the session fails to purge the queue due to some error.
     */
    public void queuePurge(String queueName) throws QpidException;

    /**
     * Delet a queue.
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> IF_EMPTY
     * <li> IF_UNUSE
     * <li> NO_WAIT
     * </ul>
     * </p>
     * <p/>
     * <p>In the absence of a particular option, the defaul value is false for each option</p>
     *
     * @param queueName The name of the queue to be deleted
     * @param options   Set of options
     * @throws QpidException If the session fails to delete the queue due to some error.
     * @see Option
     *      <p/>
     *      Following are the valid options
     */
    public void queueDelete(String queueName, Option... options) throws QpidException;

    // --------------------------------------
    //              exhcange methods 
    // --------------------------------------

    /**
     * Declare an exchange.
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> AUTO_DELETE
     * <li> DURABLE
     * <li> INTERNAL
     * <li> NO_WAIT
     * <li> PASSIVE
     * </ul>
     * </p>
     * <p/>
     * <p>In the absence of a particular option, the defaul value is false for each option</p>     *
     *
     * @param exchangeName  The exchange name.
     * @param exchangeClass The fully qualified name of the exchange class.
     * @param options       Set of options.
     * @throws QpidException If the session fails to declare the exchange due to some error.
     * @see Option
     */
    public void exchangeDeclare(String exchangeName, String exchangeClass, String alternateExchange,
                                Map<String, ?> arguments, Option... options) throws QpidException;

    /**
     * Delete an exchange.
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> IF_UNUSEDL
     * <li> NO_WAIT
     * </ul>
     * </p>
     * <p/>
     * <p>In the absence of a particular option, the defaul value is false for each option
     * Immediately deleted even if it is used by another resources.</p>
     *
     * @param exchangeName The name of exchange to be deleted.
     * @param options      Set of options.
     * @throws QpidException If the session fails to delete the exchange due to some error.
     * @see Option
     */
    public void exchangeDelete(String exchangeName, Option... options) throws QpidException;
}
