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
package org.apache.qpid.server.store;

import org.apache.commons.configuration.Configuration;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * MessageStore defines the interface to a storage area, which can be used to preserve the state of messages, queues
 * and exchanges in a transactional manner.
 *
 * <p/>All message store, remove, enqueue and dequeue operations are carried out against a {@link StoreContext} which
 * encapsulates the transactional context they are performed in. Many such operations can be carried out in a single
 * transaction.
 *
 * <p/>The storage and removal of queues and exchanges, are not carried out in a transactional context.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities
 * <tr><td> Accept transaction boundary demarcations: Begin, Commit, Abort.
 * <tr><td> Store and remove queues.
 * <tr><td> Store and remove exchanges.
 * <tr><td> Store and remove messages.
 * <tr><td> Bind and unbind queues to exchanges.
 * <tr><td> Enqueue and dequeue messages to queues.
 * <tr><td> Generate message identifiers.
 * </table>
 */
public interface MessageStore
{
    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param virtualHost The virtual host using by this store
     * @param base        The base element identifier from which all configuration items are relative. For example, if
     *                    the base element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config      The apache commons configuration object.
     *
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    void close() throws Exception;

    /**
     * Removes the specified message from the store in the given transactional store context.
     *
     * @param storeContext The transactional context to remove the message in.
     * @param messageId    Identifies the message to remove.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void removeMessage(StoreContext storeContext, Long messageId) throws AMQException;

    /**
     * Makes the specified exchange persistent.
     *
     * @param exchange The exchange to persist.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void createExchange(Exchange exchange) throws AMQException;

    /**
     * Removes the specified persistent exchange.
     *
     * @param exchange The exchange to remove.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void removeExchange(Exchange exchange) throws AMQException;

    /**
     * Binds the specified queue to an exchange with a routing key.
     *
     * @param exchange   The exchange to bind to.
     * @param routingKey The routing key to bind by.
     * @param queue      The queue to bind.
     * @param args       Additional parameters.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException;

    /**
     * Unbinds the specified from an exchange under a particular routing key.
     *
     * @param exchange   The exchange to unbind from.
     * @param routingKey The routing key to unbind.
     * @param queue      The queue to unbind.
     * @param args       Additonal parameters.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException;

    /**
     * Makes the specified queue persistent.
     *
     * @param queue The queue to store.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void createQueue(AMQQueue queue) throws AMQException;

    /**
     * Makes the specified queue persistent.
     *
     * @param queue The queue to store.
     *
     * @param arguments The additional arguments to the binding
     * @throws AMQException If the operation fails for any reason.
     */
    void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException;

    /**
     * Removes the specified queue from the persistent store.
     *
     * @param queue The queue to remove.
     * @throws AMQException If the operation fails for any reason.
     */
    void removeQueue(final AMQQueue queue) throws AMQException;

    /**
     * Places a message onto a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queue     The queue to place the message on.
     * @param messageId The message to enqueue.
     * @throws AMQException If the operation fails for any reason.
     */
    void enqueueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException;

    /**
     * Extracts a message from a specified queue, in a given transactional context.
     *
     * @param context   The transactional context for the operation.
     * @param queue     The queue to place the message on.
     * @param messageId The message to dequeue.
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void dequeueMessage(StoreContext context, final AMQQueue queue, Long messageId) throws AMQException;

    /**
     * Begins a transactional context.
     *
     * @param context The transactional context to begin.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void beginTran(StoreContext context) throws AMQException;

    /**
     * Commits all operations performed within a given transactional context.
     *
     * @param context The transactional context to commit all operations for.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void commitTran(StoreContext context) throws AMQException;

    /**
     * Abandons all operations performed within a given transactional context.
     *
     * @param context The transactional context to abandon.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void abortTran(StoreContext context) throws AMQException;

    /**
     * Tests a transactional context to see if it has been begun but not yet committed or aborted.
     *
     * @param context The transactional context to test.
     *
     * @return <tt>true</tt> if the transactional context is live, <tt>false</tt> otherwise.
     */
    boolean inTran(StoreContext context);

    /**
     * Return a valid, currently unused message id.
     *
     * @return A fresh message id.
     */
    Long getNewMessageId();

    /**
     * Stores a chunk of message data.
     *
     * @param context         The transactional context for the operation.
     * @param messageId       The message to store the data for.
     * @param index           The index of the data chunk.
     * @param contentBody     The content of the data chunk.
     * @param lastContentBody Flag to indicate that this is the last such chunk for the message.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentChunk contentBody,
        boolean lastContentBody) throws AMQException;

    /**
     * Stores message meta-data.
     *
     * @param context         The transactional context for the operation.
     * @param messageId       The message to store the data for.
     * @param messageMetaData The message meta data to store.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException;

    /**
     * Retrieves message meta-data.
     *
     * @param context   The transactional context for the operation.
     * @param messageId The message to get the meta-data for.
     *
     * @return The message meta data.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    MessageMetaData getMessageMetaData(StoreContext context, Long messageId) throws AMQException;

    /**
     * Retrieves a chunk of message data.
     *
     * @param context   The transactional context for the operation.
     * @param messageId The message to get the data chunk for.
     * @param index     The offset index of the data chunk within the message.
     *
     * @return A chunk of message data.
     *
     * @throws AMQException If the operation fails for any reason, or if the specified message does not exist.
     */
    ContentChunk getContentBodyChunk(StoreContext context, Long messageId, int index) throws AMQException;

    /**
     * Is this store capable of persisting the data
     * 
     * @return true if this store is capable of persisting data
     */
    boolean isPersistent();

}
