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
package org.apache.qpidity.nclient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.qpidity.transport.*;
import org.apache.qpidity.api.Message;

/**
 * <p>A session is associated with a connection.
 * When created a Session is not attached with an underlying channel.
 * Session is single threaded </p>
 * <p/>
 * All the Session commands are asynchronous, synchronous invocation is achieved through invoking the sync method.
 * That is to say that <code>command1</code> will be synchronously invoked using the following sequence:
 * <ul>
 * <li> <code>session.command1()</code>
 * <li> <code>session.sync()</code>
 * </ul>
 */
public interface Session
{
    public static final short TRANSFER_ACQUIRE_MODE_NO_ACQUIRE = 1;
    public static final short TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE = 0;
    public static final short TRANSFER_CONFIRM_MODE_REQUIRED = 1;
    public static final short TRANSFER_CONFIRM_MODE_NOT_REQUIRED = 0;
    public static final short MESSAGE_FLOW_MODE_CREDIT = 0;
    public static final short MESSAGE_FLOW_MODE_WINDOW = 1;
    public static final short MESSAGE_FLOW_UNIT_MESSAGE = 0;
    public static final short MESSAGE_FLOW_UNIT_BYTE = 1;
    public static final short MESSAGE_REJECT_CODE_GENERIC = 0;
    public static final short MESSAGE_REJECT_CODE_IMMEDIATE_DELIVERY_FAILED = 1;
    public static final short MESSAGE_ACQUIRE_ANY_AVAILABLE_MESSAGE = 0;
    public static final short MESSAGE_ACQUIRE_MESSAGES_IF_ALL_ARE_AVAILABLE = 1;

    //------------------------------------------------------
    //                 Session housekeeping methods
    //------------------------------------------------------

    /**
     * Sync method will block until all outstanding commands
     * are executed.
     */
    public void sync();

    /**
     * Close this session and any associated resources.
     */
    public void sessionClose();

    /**
     * Suspend this session resulting in interrupting the traffic with the broker.
     * <p> The session timer will start to tick in suspend.
     * <p> When a session is suspend any operation of this session and of the associated resources are unavailable.
     */
    public void sessionSuspend();

    //------------------------------------------------------ 
    //                 Messaging methods
    //                   Producer           
    //------------------------------------------------------
    /**
     * Transfer the given
     *
     *  to a specified exchange.
     * <p/>
     * <p>This is a convinience method for providing a complete message
     * using a single method which internaly is mapped to messageTransfer(), headers() followed
     * by data() and an endData().
     * <b><i>This method should only be used by small messages</b></i></p>
     *
     * @param destination The exchange the message is being sent.
     * @param msg         The Message to be sent
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required, once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    (whether acquisition was implicit as in pre-acquire mode or
     *                    explicit as in no-acquire mode) is not considered transferred until the original
     *                    transfer is complete (signaled via execution.complete)
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message
     *                    must be explicitly acquired
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message is
     *                    acquired when the transfer starts
     *                    </ul>
     * @throws java.io.IOException If transferring a message fails due to some internal communication error.
     */
    public void messageTransfer(String destination, Message msg, short confirmMode, short acquireMode)
            throws IOException;

    /**
     * <p>This is a convinience method for streaming a message using pull semantics
     * using a single method as opposed to doing a push using messageTransfer(), headers() followed
     * by a series of data() and an endData().</p>
     * <p>Internally data will be pulled from Message object(which wrap's a data stream) using it's read()
     * and pushed using messageTransfer(), headers() followed by a series of data() and an endData().
     * <br><b><i>This method should only be used by large messages</b></i><br>
     * There are two convinience Message classes provided to facilitate this.
     * <ul>
     * <li> <code>{@link org.apache.qpidity.nclient.util.FileMessage}</code>
     * <li> <code>{@link org.apache.qpidity.nclient.util.StreamingMessage}</code>
     * </ul>
     * You could also implement a the <code>Message</code> interface to and wrap any
     * data stream you want.
     * </p>
     *
     * @param destination The exchange the message is being sent.
     * @param msg         The Message to be sent
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required, once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    (whether acquisition was implicit as in pre-acquire mode or
     *                    explicit as in no-acquire mode) is not considered transferred until the original
     *                    transfer is complete (signaled via execution.complete)
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message
     *                    must be explicitly acquired
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message
     *                    is acquired when the transfer starts
     *                    </ul>
     * @throws java.io.IOException If transferring a message fails due to some internal communication error.
     */
    public void messageStream(String destination, Message msg, short confirmMode, short acquireMode) throws IOException;

    /**
     * Declare the beginning of a message transfer operation. This operation must
     * be followed by {@link Session#header} then followed by any number of {@link Session#data}.
     * The transfer is ended by {@link Session#endData}.
     * <p> This way of transferring messages is useful when streaming large messages
     * <p> In the interval [messageTransfer endData] any attempt to call a method other than
     * {@link Session#header}, {@link Session#endData} ore {@link Session#sessionClose}
     * will result in an exception being thrown.
     *
     * @param destination The exchange the message is being sent.
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required, once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    (whether acquisition was implicit as in pre-acquire mode or
     *                    explicit as in no-acquire mode) is not considered transferred until the original
     *                    transfer is complete (signaled via execution.complete)
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message
     *                    must be explicitly acquired
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message
     *                    is acquired when the transfer starts
     *                    </ul>
     */
    public void messageTransfer(String destination, short confirmMode, short acquireMode);

    /**
     * Add a set of headers the following headers to the message being sent.
     *
     * @param headers Are either <code>{@link org.apache.qpidity.transport.DeliveryProperties}</code>
     *                or <code>{@link org.apache.qpidity.transport.MessageProperties}</code>
     * @see org.apache.qpidity.transport.DeliveryProperties
     * @see org.apache.qpidity.transport.MessageProperties
     */
    public Header header(Struct... headers);

    /**
     * Add the following byte array to the content of the message being sent.
     *
     * @param data Data to be added.
     */
    public void data(byte[] data);

    /**
     * Add the following ByteBuffer to the content of the message being sent.
     * <p> Note that only the data between the buffer current position and the
     * buffer limit is added.
     * It is therefore recommended to flip the buffer before adding it to the message,
     *
     * @param buf Data to be added.
     */
    public void data(ByteBuffer buf);

    /**
     * Add the following String to the content of the message being sent.
     *
     * @param str String to be added.
     */
    public void data(String str);

    /**
     * Signals the end of data for the message.
     */
    public void endData();

    //------------------------------------------------------
    //                 Messaging methods
    //                   Consumer
    //------------------------------------------------------

    /**
     * Associate a message listener with a destination.
     * <p> The destination is bound to a queue and messages are filtered based
     * on the provider filter map (message filtering is specific to the provider and may not be handled).
     * <p> Following are valid options:
     * <ul>
     * <li>{@link Option#NO_LOCAL}: <p>If the no-local field is set the server will not send
     * messages to the connection that
     * published them.
     * <li>{@link Option#EXCLUSIVE}: <p> Request exclusive subscription access, meaning only this
     * ubscription can access the queue.
     * <li>{@link Option#NO_OPTION}: <p> Has no effect as it represents an �empty� option.
     * </ul>
     *
     * @param queue       The queue this receiver is receiving messages from.
     * @param destination The destination for the subscriber ,a.k.a the delivery tag.
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation is not
     *                    required, once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message (whether
     *                    acquisition was implicit as in pre-acquire mode or
     *                    explicit as in no-acquire mode) is not considered transferred until the original
     *                    transfer is complete (signaled via execution.complete)
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message must
     *                    be explicitly acquired
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message is
     *                    acquired when the transfer starts
     *                    </ul>
     * @param listener    The listener for this destination. When big message are transfered then
     *                    it is recommended to use a {@link org.apache.qpidity.nclient.MessagePartListener}.
     * @param options     Set of Options (valid options are {@link Option#NO_LOCAL}, {@link Option#EXCLUSIVE}
     *                    and {@link Option#NO_OPTION})
     * @param filter      A set of filters for the subscription. The syntax and semantics of these filters depends
     *                    on the providers implementation.
     */
    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode,
                                 MessagePartListener listener, Map<String, Object> filter, Option... options);

    /**
     * This method cancels a consumer. This does not affect already delivered messages, but it does
     * mean the server will not send any more messages for that destination. The client may receive an
     * arbitrary number of messages in between sending the cancel method and receiving the
     * notification of completion of the cancel command.
     *
     * @param destination The destination for the subscriber used at subscription
     */
    public void messageCancel(String destination);

    /**
     * Associate a message part listener with a destination.
     * <p> Only one listerner per destination is allowed. This means
     * that the previous message listener is replaced. This is done gracefully i.e. the message
     * listener is replaced once it return from the processing of a message.
     *
     * @param destination The destination the listener is associated with.
     * @param listener    The new listener for this destination.
     */
    public void setMessageListener(String destination, MessagePartListener listener);

    /**
     * Sets the mode of flow control used for a given destination.
     * <p> With credit based flow control, the broker continually maintains its current
     * credit balance with the recipient. The credit balance consists of two values, a message
     * count, and a byte count. Whenever message data is sent, both counts must be decremented.
     * If either value reaches zero, the flow of message data must stop. Additional credit is
     * received via the {@link Session#messageFlow} method.
     * <p> Window based flow control is identical to credit based flow control, however message
     * acknowledgment implicitly grants a single unit of message credit, and the size of the
     * message in byte credits for each acknowledged message.
     *
     * @param destination The destination to set the flow mode on.
     * @param mode        <ul> <li>credit ({@link Session#MESSAGE_FLOW_MODE_CREDIT}): choose credit based flow control
     *                    <li> window ({@link Session#MESSAGE_FLOW_MODE_WINDOW}): choose window based flow control</ul>
     */
    public void messageFlowMode(String destination, short mode);


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
     *                    <li> message ({@link Session#MESSAGE_FLOW_UNIT_MESSAGE})
     *                    <li> byte    ({@link Session#MESSAGE_FLOW_UNIT_BYTE})
     *                    </ul>
     * @param value       Number of credits, a value of 0 indicates an infinite amount of credit.
     */
    public void messageFlow(String destination, short unit, long value);

    /**
     * Forces the broker to exhaust its credit supply.
     * <p> The broker's credit will always be zero when
     * this method completes.
     *
     * @param destination The destination to call flush on.
     */
    public void messageFlush(String destination);

    /**
     * On receipt of this method, the brokers MUST set his credit to zero for the given
     * destination. This obeys the generic semantics of command completion, i.e. when confirmation
     * is issued credit MUST be zero and no further messages will be sent until such a time as
     * further credit is received.
     *
     * @param destination The destination to stop.
     */
    public void messageStop(String destination);

    /**
     * Acknowledge the receipt of ranges of messages.
     * <p>Message must have been previously acquired either by receiving them in
     * pre-acquire mode or by explicitly acquiring them.
     *
     * @param ranges Range of acknowledged messages.
     */
    public void messageAcknowledge(RangeSet ranges);

    /**
     * Reject ranges of acquired messages.
     * <p>  The broker MUST deliver rejected messages to the
     * alternate-exchange on the queue from which it was delivered. If no alternate-exchange is
     * defined for that queue the broker MAY discard the message.
     *
     * @param ranges Range of rejected messages.
     * @param code   The reject code must be one of {@link Session#MESSAGE_REJECT_CODE_GENERIC} or
     *               {@link Session#MESSAGE_REJECT_CODE_IMMEDIATE_DELIVERY_FAILED} (immediate delivery was attempted but
     *               failed).
     * @param text   String describing the reason for a message transfer rejection.
     */
    public void messageReject(RangeSet ranges, int code, String text);

    /**
     * This method asks the broker to redeliver all unacknowledged messages on a specified session.
     * Zero or more messages may be redelivered. This method is only allowed on non-transacted
     * sessions.
     * <p> Following are valid options:
     * <ul>
     * <li>{@link Option#REQUEUE}: <p>IIf this field is not set, the message will be redelivered to the original recipient.
     * If this option is ser, the server will attempt to requeue the message, potentially then delivering it
     * to an alternative subscriber.
     * <p/>
     * </ul>
     *
     * @param _options see available options
     */
    public void messageRecover(Option... _options);


    /**
     * As it is possible that the broker does not manage to reject some messages, after completion of
     * {@link Session#messageReject} this method will return the ranges of rejected messages.
     * <p> Note that {@link Session#messageReject} and this methods are asynchronous therefore for accessing to the
     * previously rejected messages this method must be invoked in conjunction with {@link Session#sync()}.
     * <p> A recommended invocation sequence would be:
     * <ul>
     * <li> {@link Session#messageReject}
     * <li> {@link Session#sync()}
     * <li> {@link Session#getRejectedMessages()}
     * </ul>
     *
     * @return The rejected message ranges
     */
    public RangeSet getRejectedMessages();

    /**
     * Try to acquire ranges of messages hence releasing them form the queue.
     * This means that once acknowledged, a message will not be delivered to any other receiver.
     * <p> As those messages may have been consumed by another receivers hence,
     * message acquisition can fail.
     * The outcome of the acquisition is returned as an array of ranges of qcquired messages.
     * <p> This method should only be called on non-acquired messages.
     *
     * @param mode   One of: <ul>
     *               <li> any ({@link Session#MESSAGE_ACQUIRE_ANY_AVAILABLE_MESSAGE}): acquire any available
     *               messages for consumption
     *               <li> all ({@link Session#MESSAGE_ACQUIRE_MESSAGES_IF_ALL_ARE_AVAILABLE}): only acquire messages
     *               if all are available for consumption
     *               </ul>
     * @param ranges Ranges of messages to be acquired.
     */
    public void messageAcquire(RangeSet ranges, short mode);

    /**
     * As it is possible that the broker does not manage to acquire some messages, after completion of
     * {@link Session#messageAcquire} this method will return the ranges of acquired messages.
     * <p> Note that {@link Session#messageAcquire} and this methods are asynchronous therefore for accessing to the
     * previously acquired messages this method must be invoked in conjunction with {@link Session#sync()}.
     * <p> A recommended invocation sequence would be:
     * <ul>
     * <li> {@link Session#messageAcquire}
     * <li> {@link Session#sync()}
     * <li> {@link Session#getAccquiredMessages()}
     * </ul>
     *
     * @return returns the message ranges marked by the broker as acquired.
     */
    public RangeSet getAccquiredMessages();

    /**
     * Give up responsibility for processing ranges of messages.
     * <p> Released messages are re-enqueued.
     *
     * @param ranges Ranges of messages to be released.
     */
    public void messageRelease(RangeSet ranges);

    // -----------------------------------------------
    //            Local transaction methods
    //  ----------------------------------------------
    /**
     * Selects the session for local transaction support.
     */
    public void txSelect();

    /**
     * Commit the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws IllegalStateException If this session is not transacted.
     */
    public void txCommit() throws IllegalStateException;

    /**
     * Rollback the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws IllegalStateException If this session is not transacted.
     */
    public void txRollback() throws IllegalStateException;

    //---------------------------------------------
    //            Queue methods 
    //---------------------------------------------

    /**
     * Declare a queue with the given queueName
     * <p> Following are the valid options:
     * <ul>
     * <li> {@link Option#AUTO_DELETE}: <p> If this field is set and the exclusive field is also set,
     * then the queue is deleted when the connection closes.
     * If this field is set and the exclusive field is not set the queue is deleted when all
     * the consumers have finished using it.
     * <li> {@link Option#DURABLE}: <p>  If set when creating a new queue,
     * the queue will be marked as durable. Durable queues
     * remain active when a server restarts. Non-durable queues (transient queues) are purged
     * if/when a server restarts. Note that durable queues do not necessarily hold persistent
     * messages, although it does not make sense to send persistent messages to a transient
     * queue.
     * <li> {@link Option#EXCLUSIVE}: <p>  Exclusive queues can only be used from one connection at a time.
     * Once a connection declares an exclusive queue, that queue cannot be used by any other connections until the
     * declaring connection closes.
     * <li> {@link Option#PASSIVE}: <p> If set, the server will not create the queue.
     * This field allows the client to assert the presence of a queue without modifying the server state.
     * <li>{@link Option#NO_OPTION}: <p> Has no effect as it represents an �empty� option.
     * </ul>
     * <p>In the absence of a particular option, the defaul value is false for each option
     *
     * @param queueName         The name of the delcared queue.
     * @param alternateExchange If a message is rejected by a queue, then it is sent to the alternate-exchange. A message
     *                          may be rejected by a queue for the following reasons:
     *                          <oL> <li> The queue is deleted when it is not empty;
     *                          <li> Immediate delivery of a message is requested, but there are no consumers connected to
     *                          the queue. </ol>
     * @param arguments         Used for backward compatibility
     * @param options           Set of Options ( valide options are: {@link Option#AUTO_DELETE}, {@link Option#DURABLE},
     *                          {@link Option#EXCLUSIVE}, {@link Option#PASSIVE} and  {@link Option#NO_OPTION})
     * @see Option
     */
    public void queueDeclare(String queueName, String alternateExchange, Map<String, Object> arguments,
                             Option... options);

    /**
     * Bind a queue with an exchange.
     *
     * @param queueName    Specifies the name of the queue to bind. If the queue name is empty, refers to the current
     *                     queue for the session, which is the last declared queue.
     * @param exchangeName The exchange name.
     * @param routingKey   Specifies the routing key for the binding. The routing key is used for routing messages
     *                     depending on the exchange configuration. Not all exchanges use a routing key - refer to
     *                     the specific exchange documentation. If the queue name is empty, the server uses the last
     *                     queue declared on the session. If the routing key is also empty, the server uses this
     *                     queue name for the routing key as well. If the queue name is provided but the routing key
     *                     is empty, the server does the binding with that empty routing key. The meaning of empty
     *                     routing keys depends on the exchange implementation.
     * @param arguments    Used for backward compatibility
     */
    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, Object> arguments);

    /**
     * Unbind a queue from an exchange.
     *
     * @param queueName    Specifies the name of the queue to unbind.
     * @param exchangeName The name of the exchange to unbind from.
     * @param routingKey   Specifies the routing key of the binding to unbind.
     * @param arguments    Used for backward compatibility
     */
    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, Object> arguments);

    /**
     * This method removes all messages from a queue. It does not cancel consumers. Purged messages
     * are deleted without any formal "undo" mechanism.
     *
     * @param queueName Specifies the name of the queue to purge. If the queue name is empty, refers to the
     *                  current queue for the session, which is the last declared queue.
     */
    public void queuePurge(String queueName);

    /**
     * This method deletes a queue. When a queue is deleted any pending messages are sent to a
     * dead-letter queue if this is defined in the server configuration, and all consumers on the
     * queue are cancelled.
     * <p> Following are the valid options:
     * <ul>
     * <li> {@link Option#IF_EMPTY}: <p>  If set, the server will only delete the queue if it has no messages.
     * <li> {@link Option#IF_UNUSED}: <p> If set, the server will only delete the queue if it has no consumers.
     * If the queue has consumers the server does does not delete it but raises a channel exception instead.
     * <li>{@link Option#NO_OPTION}: <p> Has no effect as it represents an �empty� option.
     * </ul>
     * </p>
     * <p/>
     * <p>In the absence of a particular option, the defaul value is false for each option</p>
     *
     * @param queueName Specifies the name of the queue to delete. If the queue name is empty, refers to the
     *                  current queue for the session, which is the last declared queue.
     * @param options   Set of options (Valid options are: {@link Option#IF_EMPTY}, {@link Option#IF_UNUSED}
     *                  and {@link Option#NO_OPTION})
     * @see Option
     */
    public void queueDelete(String queueName, Option... options);


    /**
     * This method is used to request information on a particular queue.
     *
     * @param queueName The name of the queue for which information is requested.
     * @return Information on the specified queue.
     */
    public Future<QueueQueryResult> queueQuery(String queueName);


    /**
     * This method is used to request information on a particular binding.
     *
     * @param exchange   The exchange name.
     * @param queue      The queue name.
     * @param routingKey The routing key
     * @param arguments  bacward compatibilties params.
     * @return Information on the specified binding.
     */
    public Future<BindingQueryResult> bindingQuery(String exchange, String queue, String routingKey,
                                                   Map<String, Object> arguments);

    // --------------------------------------
    //              exhcange methods 
    // --------------------------------------

    /**
     * This method creates an exchange if it does not already exist, and if the exchange exists,
     * verifies that it is of the correct and expected class.
     * <p> Following are the valid options:
     * <ul>
     * <li> {@link Option#AUTO_DELETE}: <p> If set, the exchange is deleted when all queues have finished using it.
     * <li> {@link Option#DURABLE}: <p> If set when creating a new exchange, the exchange will
     * be marked as durable. Durable exchanges remain active when a server restarts. Non-durable exchanges (transient
     * exchanges) are purged if/when a server restarts.
     * <li> {@link Option#PASSIVE}: <p> If set, the server will not create the exchange.
     * The client can use this to check whether an exchange exists without modifying the server state.
     * <li> {@link Option#NO_OPTION}: <p> Has no effect as it represents an �empty� option.
     * </ul>
     * <p>In the absence of a particular option, the defaul value is false for each option</p>
     *
     * @param exchangeName      The exchange name.
     * @param type              Each exchange belongs to one of a set of exchange types implemented by the server. The
     *                          exchange types define the functionality of the exchange - i.e. how messages are routed
     *                          through it. It is not valid or meaningful to attempt to change the type of an existing
     *                          exchange. Default exchange types are: direct, topic, headers and fanout.
     * @param alternateExchange In the event that a message cannot be routed, this is the name of the exchange to which
     *                          the message will be sent.
     * @param options           Set of options (valid options are: {@link Option#AUTO_DELETE}, {@link Option#DURABLE},
     *                          {@link Option#PASSIVE}, {@link Option#NO_OPTION})
     * @param arguments         Used for backward compatibility
     * @see Option
     */
    public void exchangeDeclare(String exchangeName, String type, String alternateExchange,
                                Map<String, Object> arguments, Option... options);

    /**
     * This method deletes an exchange. When an exchange is deleted all queue bindings on the
     * exchange are cancelled.
     * <p> Following are the valid options:
     * <ul>
     * <li> {@link Option#IF_UNUSED}: <p> If set, the server will only delete the exchange if it has no queue bindings. If the
     * exchange has queue bindings the server does not delete it but raises a channel exception
     * instead.
     * <li> {@link Option#NO_OPTION}: <p> Has no effect as it represents an �empty� option.
     * </ul>
     * <p>In the absence of a particular option, the defaul value is false for each option
     *
     * @param exchangeName The name of exchange to be deleted.
     * @param options      Set of options (valid options are:  {@link Option#IF_UNUSED}, {@link Option#NO_OPTION})
     * @see Option
     */
    public void exchangeDelete(String exchangeName, Option... options);


    /**
     * This method is used to request information on a particular exchange.
     *
     * @param exchangeName The name of the exchange for which information is requested. If not specified explicitly
     *                     the default exchange is implied.
     * @return Information on the specified exchange.
     */
    public Future<ExchangeQueryResult> exchangeQuery(String exchangeName);

    /**
     * If the session receives a sessionClosed with an error code it
     * informs the session's ExceptionListener
     *
     * @param exceptionListner The execptionListener
     */
    public void setClosedListener(ClosedListener exceptionListner);
}
