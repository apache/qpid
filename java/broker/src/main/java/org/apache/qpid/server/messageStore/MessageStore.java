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
package org.apache.qpid.server.messageStore;

import org.apache.qpid.server.exception.*;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.txn.TransactionManager;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.commons.configuration.Configuration;

import javax.transaction.xa.Xid;
import java.util.Collection;

/**
 * Created by Arnaud Simon
 * Date: 29-Mar-2007
 * Time: 17:34:02
 */
public interface MessageStore
{
    /**
     * Create a new exchange
     *
     * @param exchange the exchange to be persisted
     * @throws InternalErrorException If an error occurs
     */
    public void createExchange(Exchange exchange)
            throws
            InternalErrorException;

    /**
     * Remove an exchange
     *
     * @param exchange The exchange to be removed
     * @throws InternalErrorException If an error occurs
     */
    public void removeExchange(Exchange exchange)
            throws
            InternalErrorException;

    /**
     * Bind a queue with an exchange given a routing key
     *
     * @param exchange   The exchange to bind the queue with
     * @param routingKey The routing key
     * @param queue      The queue to be bound
     * @param args       Args
     * @throws InternalErrorException If an error occurs
     */
    public void bindQueue(Exchange exchange,
                          AMQShortString routingKey,
                          StorableQueue queue, FieldTable args)
            throws
            InternalErrorException;

    /**
     * Unbind a queue from an exchange
     *
     * @param exchange   The exchange the queue was bound to
     * @param routingKey The routing queue
     * @param queue      The queue to unbind
     * @param args       args
     * @throws InternalErrorException If an error occurs
     */
    public void unbindQueue(Exchange exchange,
                            AMQShortString routingKey,
                            StorableQueue queue, FieldTable args)
            throws
            InternalErrorException;

    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param virtualHost The virtual host using by this store
     * @param tm          The transaction manager implementation
     * @param base        The base element identifier from which all configuration items are relative. For example, if the base
     *                    element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config      The apache commons configuration object
     * @throws InternalErrorException   If an error occurs that means the store is unable to configure itself
     * @throws IllegalArgumentException If the configuration arguments are illegal
     */
    void configure(VirtualHost virtualHost, TransactionManager tm, String base, Configuration config)
            throws
            InternalErrorException,
            IllegalArgumentException;

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws InternalErrorException if close fails
     */
    void close()
            throws
            InternalErrorException;

    /**
     * Create a queue
     *
     * @param queue the queue to be created
     * @throws InternalErrorException      In case of internal message store problem
     * @throws QueueAlreadyExistsException If the queue already exists in the store
     */
    public void createQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueAlreadyExistsException;

    /**
     * Destroy a queue
     *
     * @param queue The queue to be destroyed
     * @throws InternalErrorException    In case of internal message store problem
     * @throws QueueDoesntExistException If the queue does not exist in the store
     */
    public void destroyQueue(StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException;

    /**
     * Stage the message before effective enqueue
     *
     * @param m The message to stage
     * @throws InternalErrorException        In case of internal message store problem
     * @throws MessageAlreadyStagedException If the message is already staged
     */
    public void stage(StorableMessage m)
            throws
            InternalErrorException,
            MessageAlreadyStagedException;


    /**
     * Append more data with a previously staged message
     *
     * @param m      The message to which data must be appended
     * @param data   Data to happen to the message
     * @param offset The number of bytes from the beginning of the payload
     * @param size   The number of bytes to be written
     * @throws InternalErrorException      In case of internal message store problem
     * @throws MessageDoesntExistException If the message has not been staged
     */
    public void appendContent(StorableMessage m, byte[] data, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException;

    /**
     * Get the content of previously staged or enqueued message.
     * The message headers are also set.
     *
     * @param m      The message for which the content must be loaded
     * @param offset The number of bytes from the beginning of the payload
     * @param size   The number of bytes to be loaded
     * @return The message content
     * @throws InternalErrorException      In case of internal message store problem
     * @throws MessageDoesntExistException If the message does not exist
     */
    public byte[] loadContent(StorableMessage m, int offset, int size)
            throws
            InternalErrorException,
            MessageDoesntExistException;

    /**
     * Get the content header of this message
     *
     * @param m      The message 
     * @return The message content
     * @throws InternalErrorException      In case of internal message store problem
     * @throws MessageDoesntExistException If the message does not exist
     */
    public ContentHeaderBody getContentHeaderBody(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException;

    /**
     * Get the MessagePublishInfo of this message
     *
     * @param m      The message
     * @return The message content
     * @throws InternalErrorException      In case of internal message store problem
     * @throws MessageDoesntExistException If the message does not exist
     */
      public MessagePublishInfo getMessagePublishInfo(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException;

    /**
     * Destroy a previously staged message
     *
     * @param m the message to be destroyed
     * @throws InternalErrorException      In case of internal message store problem
     * @throws MessageDoesntExistException If the message does not exist in the store
     */
    public void destroy(StorableMessage m)
            throws
            InternalErrorException,
            MessageDoesntExistException;

    /**
     * Enqueue a message under the scope of the transaction branch
     * identified by xid when specified.
     * <p> This operation is propagated to the queue and the message.
     * <p> A message that has been previously staged is assumed to have had
     * its payload already added (see appendContent)
     *
     * @param xid   The xid of the transaction branch under which the message must be enqueued.
     *              <p> It he xid is null then the message is enqueued outside the scope of any transaction.
     * @param m     The message to be enqueued
     * @param queue The queue into which the message must be enqueued
     * @throws InternalErrorException      In case of internal message store problem
     * @throws QueueDoesntExistException   If the queue does not exist in the store
     * @throws InvalidXidException         The transaction branch is invalid
     * @throws UnknownXidException         The transaction branch is unknown
     * @throws MessageDoesntExistException If the Message does not exist
     */
    public void enqueue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException,
            MessageDoesntExistException;

    /**
     * Dequeue a message under the scope of the transaction branch identified by xid
     * if specified.
     * <p> This operation is propagated to the queue and the message.
     *
     * @param xid   The xid of the transaction branch under which the message must be dequeued.
     *              <p> It he xid is null then the message is dequeued outside the scope of any transaction.
     * @param m     The message to be dequeued
     * @param queue The queue from which the message must be dequeued
     * @throws InternalErrorException    In case of internal message store problem
     * @throws QueueDoesntExistException If the queue does not exist in the store
     * @throws InvalidXidException       The transaction branch is invalid
     * @throws UnknownXidException       The transaction branch is unknown
     */
    public void dequeue(Xid xid, StorableMessage m, StorableQueue queue)
            throws
            InternalErrorException,
            QueueDoesntExistException,
            InvalidXidException,
            UnknownXidException;

    //=========================================================
    // Recovery specific methods
    //=========================================================

    /**
     * List all the persistent queues
     *
     * @return All the persistent queues
     * @throws InternalErrorException In case of internal message store problem
     */
    public Collection<StorableQueue> getAllQueues()
            throws
            InternalErrorException;

    /**
     * All enqueued messages of a given queue
     *
     * @param queue The queue where the message are retrieved from
     * @return The list all enqueued messages of a given queue
     * @throws InternalErrorException In case of internal message store problem
     */
    public Collection<StorableMessage> getAllMessages(StorableQueue queue)
            throws
            InternalErrorException;

    /**
     * Get a new message ID
     *
     * @return A new message ID
     */
    public long getNewMessageId();
}
