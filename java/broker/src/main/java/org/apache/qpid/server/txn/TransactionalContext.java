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
package org.apache.qpid.server.txn;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.ack.UnacknowledgedMessageMap;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.QueueEntry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.store.StoreContext;

/**
 * TransactionalContext provides a context in which transactional operations on {@link AMQMessage}s are performed.
 * Different levels of transactional support for the delivery of messages may be provided by different implementations
 * of this interface.
 *
 * <p/>The fundamental transactional operations that can be performed on a message queue are 'enqueue' and 'dequeue'.
 * In this interface, these have been recast as the {@link #messageFullyReceived} and {@link #acknowledgeMessage}
 * operations. This interface essentially provides a way to make enqueueing and dequeuing transactional.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Explicitly accept a transaction start notification.
 * <tr><td> Commit all pending operations in a transaction.
 * <tr><td> Rollback all pending operations in a transaction.
 * <tr><td> Deliver a message to a queue as part of a transaction.
 * <tr><td> Redeliver a message to a queue as part of a transaction.
 * <tr><td> Mark a message as acknowledged as part of a transaction.
 * <tr><td> Accept notification that a message has been completely received as part of a transaction.
 * <tr><td> Accept notification that a message has been fully processed as part of a transaction.
 * <tr><td> Associate a message store context with this transaction context.
 * </table>
 *
 * @todo The 'fullyReceived' and 'messageProcessed' events sit uncomfortably in the responsibilities of a transactional
 *       context. They are non-transactional operations, used to trigger other side-effects. Consider moving them
 *       somewhere else, a seperate interface for example.
 *
 * @todo This transactional context could be written as a wrapper extension to a Queue implementation, that provides
 *       transactional management of the enqueue and dequeue operations, with added commit/rollback methods. Any
 *       queue implementation could be made transactional by wrapping it as a transactional queue. This would mean
 *       that the enqueue/dequeue operations do not need to be recast as deliver/acknowledge operations, which may be
 *       conceptually neater.
 *
 * For example:
 * <pre>
 * public interface Transactional
 * {
 *    public void commit();
 *    public void rollback();
 * }
 *
 * public interface TransactionalQueue<E> extends Transactional, SizeableQueue<E>
 * {}
 *
 * public class Queues
 * {
 *    ...
 *    // For transactional messaging, take a transactional view onto the queue.
 *    public static <E> TransactionalQueue<E> getTransactionalQueue(SizeableQueue<E> queue) { ... }
 *
 *    // For non-transactional messaging, take a non-transactional view onto the queue.
 *    public static <E> TransactionalQueue<E> getNonTransactionalQueue(SizeableQueue<E> queue) { ... }
 * }
 * </pre>
 */
public interface TransactionalContext
{
    /**
     * Explicitly begins the transaction, if it has not already been started. {@link #commit} or {@link #rollback}
     * should automatically begin the next transaction in the chain.
     *
     * @throws AMQException If the transaction cannot be started for any reason.
     */
    void beginTranIfNecessary() throws AMQException;

    /**
     * Makes all pending operations on the transaction permanent and visible.
     *
     * @throws AMQException If the transaction cannot be committed for any reason.
     */
    void commit() throws AMQException;

    /**
     * Erases all pending operations on the transaction.
     *
     * @throws AMQException If the transaction cannot be committed for any reason.
     */
    void rollback() throws AMQException;

    /**
     * Delivers the specified message to the specified queue.
     *
     * <p/>This is an 'enqueue' operation.
     *
     * @param queue
     * @param message      The message to deliver
     * @throws AMQException If the message cannot be delivered for any reason.
     */
    void deliver(final AMQQueue queue, AMQMessage message) throws AMQException;

    /**
         * Requeues the specified message entry (message queue pair)
         *
         *
         * @param queueEntry      The message,queue pair
         *
         * @throws AMQException If the message cannot be delivered for any reason.
         */
    void requeue(QueueEntry queueEntry) throws AMQException;


    /**
     * Acknowledges a message or many messages as delivered. All messages up to a specified one, may be acknowledged by
     * setting the 'multiple' flag. It is also possible for the acknowledged message id to be zero, when the 'multiple'
     * flag is set, in which case an acknowledgement up to the latest delivered message should be done.
     *
     * <p/>This is a 'dequeue' operation.
     *
     * @param deliveryTag              The id of the message to acknowledge, or zero, if using multiple acknowledgement
     *                                 up to the latest message.
     * @param lastDeliveryTag          The latest message delivered.
     * @param multiple                 <tt>true</tt> if all message ids up the acknowledged one or latest delivered, are
     *                                 to be acknowledged, <tt>false</tt> otherwise.
     * @param unacknowledgedMessageMap The unacknowledged messages in the transaction, to remove the acknowledged message
     *                                 from.
     *
     * @throws AMQException If the message cannot be acknowledged for any reason.
     */
    void acknowledgeMessage(long deliveryTag, long lastDeliveryTag, boolean multiple,
        UnacknowledgedMessageMap unacknowledgedMessageMap) throws AMQException;

    /**
     * Notifies the transactional context that a message has been fully received. The actual message that was received
     * is not specified. This event may be used to trigger a process related to the receipt of the message, for example,
     * flushing its data to disk.
     *
     * @param persistent <tt>true</tt> if the received message is persistent, <tt>false</tt> otherwise.
     *
     * @throws AMQException If the fully received event cannot be processed for any reason.
     */
    void messageFullyReceived(boolean persistent) throws AMQException;

    /**
     * Notifies the transactional context that a message has been delivered, succesfully or otherwise. The actual
     * message that was delivered is not specified. This event may be used to trigger a process related to the
     * outcome of the delivery of the message, for example, cleaning up failed deliveries.
     *
     * @param protocolSession The protocol session of the deliverable message.
     *
     * @throws AMQException If the message processed event cannot be handled for any reason.
     */
    void messageProcessed(AMQProtocolSession protocolSession) throws AMQException;

    /**
     * Gets the message store context associated with this transactional context.
     *
     * @return The message store context associated with this transactional context.
     */
    StoreContext getStoreContext();
}
