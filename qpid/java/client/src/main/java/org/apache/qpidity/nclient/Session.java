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
 * When it is created, a session is not associated with an underlying channel.
 * The session is single threaded. </p>
 * <p/>
 * All the Session commands are asynchronous. Synchronous behavior is achieved through invoking the sync method.
 * For example, <code>command1</code> will be synchronously invoked by using the following sequence:
 * <ul>
 * <li> <code>session.command1()</code>
 * <li> <code>session.sync()</code>
 * </ul>
 */
public interface Session
{
    public static final short TRANSFER_ACQUIRE_MODE_NO_ACQUIRE = 1;
    public static final short TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE = 0;
    public static final short TRANSFER_CONFIRM_MODE_REQUIRED = 0;
    public static final short TRANSFER_CONFIRM_MODE_NOT_REQUIRED = 1;
    public static final short MESSAGE_FLOW_MODE_CREDIT = 0;
    public static final short MESSAGE_FLOW_MODE_WINDOW = 1;
    public static final short MESSAGE_FLOW_UNIT_MESSAGE = 0;
    public static final short MESSAGE_FLOW_UNIT_BYTE = 1;
    public static final long  MESSAGE_FLOW_MAX_BYTES = 0xFFFFFFFF;
    public static final short MESSAGE_REJECT_CODE_GENERIC = 0;
    public static final short MESSAGE_REJECT_CODE_IMMEDIATE_DELIVERY_FAILED = 1;
    public static final short MESSAGE_ACQUIRE_ANY_AVAILABLE_MESSAGE = 0;
    public static final short MESSAGE_ACQUIRE_MESSAGES_IF_ALL_ARE_AVAILABLE = 1;

    //------------------------------------------------------
    //                 Session housekeeping methods
    //------------------------------------------------------

    /**
     * Sync method will block the session until all outstanding commands
     * are executed.
     */
    public void sync();

    public void close();

    public void sessionDetach(byte[] name);

    public void sessionRequestTimeout(long expiry);

    public byte[] getName();

    public void setAutoSync(boolean value);

    //------------------------------------------------------
    //                 Messaging methods
    //                   Producer
    //------------------------------------------------------
    /**
     * Transfer a message to a specified exchange.
     * <p/>
     * <p>This transfer provides a complete message
     * using a single method. The method is internally mapped to messageTransfer() and headers() followed
     * by data() and endData().
     * <b><i>This method should only be used by small messages.</b></i></p>
     *
     * @param destination The exchange the message is being sent to.
     * @param msg         The Message to be sent.
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required. Once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred.
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    is not considered transferred until the original
     *                    transfer is complete. A complete transfer is signaled by execution.complete.
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message
     *                    must be explicitly acquired.
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message is
     *                    acquired when the transfer starts.
     *                    </ul>
     * @throws java.io.IOException If transferring a message fails due to some internal communication error, an exception is thrown.
     */
    public void messageTransfer(String destination, Message msg, short confirmMode, short acquireMode)
            throws IOException;


    /**
     * <p>This transfer streams a complete message using a single method. 
     * It uses pull-semantics instead of doing a push.</p>
     * <p>Data is pulled from a Message object using read()
     * and pushed using messageTransfer() and headers() followed by data() and endData().
     * <br><b><i>This method should only be used by large messages</b></i><br>
     * There are two convenience Message classes to do this.
     * <ul>
     * <li> <code>{@link org.apache.qpidity.nclient.util.FileMessage}</code>
     * <li> <code>{@link org.apache.qpidity.nclient.util.StreamingMessage}</code>
     * </ul>
     * You can also implement a <code>Message</code> interface to wrap any
     * data stream.
     * </p>
     *
     * @param destination The exchange the message is being sent to.
     * @param msg         The Message to be sent.
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required. Once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred.
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    is not considered transferred until the original
     *                    transfer is complete. A complete transfer is signaled by execution.complete.
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message
     *                    must be explicitly acquired.
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message
     *                    is acquired when the transfer starts.
     *                    </ul>
     * @throws java.io.IOException If transferring a message fails due to some internal communication error, an exception is thrown.
     */
    public void messageStream(String destination, Message msg, short confirmMode, short acquireMode) throws IOException;

    /**
     * This command transfers a message between two peers.
     *
     * @param destination Specifies the destination to which the message is to be transferred.
     * @param acceptMode Indicates whether message.accept, session.complete,
     *                  or nothing at all is required to indicate successful transfer of the message.
     * 
     * @param acquireMode Indicates whether or not the transferred message has been acquired.
     */
    public void messageTransfer(String destination, MessageAcceptMode acceptMode, MessageAcquireMode acquireMode);

    /**
     * Make a set of headers to be sent together with a message
     *
     * @param headers headers to be added
     * @see org.apache.qpidity.transport.DeliveryProperties
     * @see org.apache.qpidity.transport.MessageProperties
     * @return The added headers.
     */
    public Header header(Struct... headers);

    /**
     * Add a byte array to the content of the message being sent.
     *
     * @param data Data to be added.
     */
    public void data(byte[] data);

    /**
     * A Add a ByteBuffer to the content of the message being sent.
     * <p> Note that only the data between the buffer's current position and the
     * buffer limit is added.
     * It is therefore recommended to flip the buffer before adding it to the message,
     *
     * @param buf Data to be added.
     */
    public void data(ByteBuffer buf);

    /**
     * Add a string to the content of the message being sent.
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
     * <p> The destination is bound to a queue, and messages are filtered based
     * on the provider filter map (message filtering is specific to the provider and in some cases might not be handled).
     * <p> The valid options are:
     * <ul>
     * <li>{@link Option#EXCLUSIVE}: <p> Requests exclusive subscription access, so that only this
     * subscription can access the queue.
     * <li>{@link Option#NO_OPTION}: <p> This is an empty option, and has no effect.
     * </ul>
     *
     * @param queue       The queue that the receiver is receiving messages from.
     * @param destination The destination, or delivery tag, for the subscriber.
     * @param confirmMode <ul> </li>off ({@link Session#TRANSFER_CONFIRM_MODE_NOT_REQUIRED}): confirmation
     *                    is not required. Once a message has been transferred in pre-acquire
     *                    mode (or once acquire has been sent in no-acquire mode) the message is considered
     *                    transferred.
     *                    <p/>
     *                    <li> on  ({@link Session#TRANSFER_CONFIRM_MODE_REQUIRED}): an acquired message
     *                    is not considered transferred until the original
     *                    transfer is complete. A complete transfer is signaled by execution.complete.
     *                    </ul>
     * @param acquireMode <ul>
     *                    <li> no-acquire  ({@link Session#TRANSFER_ACQUIRE_MODE_NO_ACQUIRE}): the message must
     *                    be explicitly acquired.
     *                    <li> pre-acquire ({@link Session#TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE}): the message is
     *                    acquired when the transfer starts.
     *                    </ul>
     * @param listener    The listener for this destination. To transfer large messages
     *                    use a {@link org.apache.qpidity.nclient.MessagePartListener}.
     * @param options     Set of options. Valid options are {{@link Option#EXCLUSIVE}
     *                    and {@link Option#NO_OPTION}.
     * @param filter      A set of filters for the subscription. The syntax and semantics of these filters varies
     *                    according to the provider's implementation.
     */
    public void messageSubscribe(String queue, String destination, short confirmMode, short acquireMode,
                                 MessagePartListener listener, Map<String, Object> filter, Option... options);

    /**
     * This method cancels a consumer. The server will not send any more messages to the specified destination. 
     * This does not affect already delivered messages.
     * The client may receive a
     * number of messages in between sending the cancel method and receiving 
     * notification that the cancellation has been completed.
     *
     * @param destination The destination to be cancelled.
     */
    public void messageCancel(String destination);

    /**
     * Associate a message listener with a destination.
     * <p> Only one listener is permitted for each destination. When a new listener is created,
     * it replaces the previous message listener. To prevent message loss, this occurs only when the original listener
     * has completed processing a message.
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
    public void messageSetFlowMode(String destination, MessageFlowMode mode);


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
    public void messageFlow(String destination, MessageCreditUnit unit, long value);

    /**
     * Forces the broker to exhaust its credit supply.
     * <p> The credit on the broker will remain at zero once
     * this method is completed.
     *
     * @param destination The destination on which the credit supply is to be exhausted.
     */
    public void messageFlush(String destination);

    /**
     * On receipt of this method, the brokers set credit to zero for a given
     * destination. When confirmation of this method
     * is issued credit is set to zero. No further messages will be sent until
     * further credit is received.
     *
     * @param destination The destination on which to reset credit.
     */
    public void messageStop(String destination);

    /**
     * Acknowledge the receipt of a range of messages.
     * <p>Messages must already be acquired, either by receiving them in
     * pre-acquire mode or by explicitly acquiring them.
     *
     * @param ranges Range of messages to be acknowledged.
     * @param accept pecify whether to send a message accept to the broker
     */
    public void messageAcknowledge(RangeSet ranges, boolean accept);

    /**
     * Reject a range of acquired messages.
     * <p>The broker will deliver rejected messages to the
     * alternate-exchange on the queue from which it came. If no alternate-exchange is
     * defined for that queue the broker will discard the message.
     *
     * @param ranges Range of messages to be rejected.
     * @param code   The reject code must be one of {@link Session#MESSAGE_REJECT_CODE_GENERIC} or
     *               {@link Session#MESSAGE_REJECT_CODE_IMMEDIATE_DELIVERY_FAILED} (immediate delivery was attempted but
     *               failed).
     * @param text   String describing the reason for a message transfer rejection.
     */
    public void messageReject(RangeSet ranges, MessageRejectCode code, String text);

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
     * @param ranges Ranges of messages to be acquired.
     * @return Indicates the acquired messages
     */
    public Future<Acquired> messageAcquire(RangeSet ranges);

    /**
     * Give up responsibility for processing ranges of messages.
     * <p> Released messages are re-enqueued.
     *
     * @param ranges Ranges of messages to be released.
     * @param options Valid option is: {@link Option#SET_REDELIVERED})
     */
    public void messageRelease(RangeSet ranges, Option ... options);

    // -----------------------------------------------
    //            Local transaction methods
    //  ----------------------------------------------
    /**
     * Selects the session for local transaction support.
     */
    public void txSelect();

    /**
     * Commit the receipt and delivery of all messages exchanged by this session's resources.
     *
     * @throws IllegalStateException If this session is not transacted, an exception will be thrown.
     */
    public void txCommit() throws IllegalStateException;

    /**
     * Roll back the receipt and delivery of all messages exchanged by this session's resources.
     *
     * @throws IllegalStateException If this session is not transacted, an exception will be thrown.
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
    public void exchangeBind(String queueName, String exchangeName, String routingKey, Map<String, Object> arguments);

    /**
     * Unbind a queue from an exchange.
     *
     * @param queueName    Specifies the name of the queue to unbind.
     * @param exchangeName The name of the exchange to unbind from.
     * @param routingKey   Specifies the routing key of the binding to unbind.
     */
    public void exchangeUnbind(String queueName, String exchangeName, String routingKey);

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
    public Future<ExchangeBoundResult> exchangeBound(String exchange, String queue, String routingKey,
                                                     Map<String, Object> arguments);

    // --------------------------------------
    //              exhcange methods
    // --------------------------------------

    /**
     * This method creates an exchange. If the exchange already exists,
     * the method verifies the class and checks the details are correct.
     * <p>Valid options are:
     * <ul>
     * <li>{@link Option#AUTO_DELETE}: <p>If set, the exchange is deleted when all queues have finished using it.
     * <li>{@link Option#DURABLE}: <p>If set, the exchange will
     * be marked as durable. Durable exchanges remain active when a server restarts. Non-durable exchanges (transient
     * exchanges) are purged when a server restarts.
     * <li>{@link Option#PASSIVE}: <p>If set, the server will not create the exchange.
     * The client can use this to check whether an exchange exists without modifying the server state.
     * <li> {@link Option#NO_OPTION}: <p>This option is an empty option, and has no effect.
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
     * <li> {@link Option#NO_OPTION}: <p> Has no effect as it represents an empty option.
     * </ul>
     * <p>Note that if an option is not set, it will default to false.
     *
     * @param exchangeName The name of exchange to be deleted.
     * @param options      Set of options. Valid options are:  {@link Option#IF_UNUSED}, {@link Option#NO_OPTION}.
     * @see Option
     */
    public void exchangeDelete(String exchangeName, Option... options);


    /**
     * This method is used to request information about a particular exchange.
     *
     * @param exchangeName The name of the exchange about which information is requested. If not set, the method will
     *                     return information about the default exchange.
     * @return Information on the specified exchange.
     */
    public Future<ExchangeQueryResult> exchangeQuery(String exchangeName);

    /**
     * If the session receives a sessionClosed with an error code it
     * informs the session's exceptionListener
     *
     * @param exceptionListner The exceptionListener
     */
    public void setClosedListener(ClosedListener exceptionListner);
}
