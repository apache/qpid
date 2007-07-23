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
package org.apache.qpid.nclient.qpidapi;

import org.apache.qpid.nclient.exception.QpidException;

/**
 * A session is associated with a connection.
 * <p> When created a Session is not attached with an underlying channel. Unsuspended a Session is
 * equivalent to attaching a communication channel that can be used to communicate with the broker.
 * <p/>
 * Created by Arnaud Simon
 * Date: 20-Jul-2007
 * Time: 09:36:24
 */
public interface Session
{
    /**
     * Close this session and any associated resources.
     *
     * @throws QpidException If the communication layer fails to close this session or if an internal error happens
     *                       when closing this session resources. .
     */
    public void close()
            throws
            QpidException;

    /**
     * Suspend this session resulting in interrupting the traffic with the broker.
     * When a session is suspend any operation of this session and of the associated resources is unavailable.
     *
     * @throws QpidException If the communication layer fails to suspend this session
     */
    public void suspend()
            throws
            QpidException;

    /**
     * Unsuspended a Session is equivalent to attaching a communication channel that can be used to
     * communicate with the broker. All the operations of this session and of the associated resources
     * are made available.
     *
     * @throws QpidException If the communication layer fails to unsuspend this session
     */
    public void unsuspend()
            throws
            QpidException;

    /**
     * Create a message sender for sending messages to queue queueName.
     *
     * @param queueName The queue this sender is sending messages.
     * @return A sender for queue queueName
     * @throws QpidException If the session fails to create the sended due to some error
     */
    public MessageSender createSender(String queueName)
            throws
            QpidException;
    //Todo: Do we need to define more specific exception like queue name not valid?

    /**
     * Create a message receiver for receiving messages from queue queueName.
     * <p> see available options: {@link CreateReceiverOption}
     * <p> When non of the options are set then the receiver is created with:
     * <ul>
     * <li> no_local = false
     * <li> non exclusive
     * <li> pre-acquire mode
     * <li> no confirmation
     * </ul>
     *
     * @param queueName The queue this receiver is receiving messages from.
     * @param options   Set of Options.
     * @return A receiver for queue queueName.
     * @throws QpidException If the session fails to create the receiver due to some error.
     * @see CreateReceiverOption
     */
    public MessageReceiver createReceiver(String queueName, CreateReceiverOption... options)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like queue name not valid?

    /**
     * Commit the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws QpidException         If the session fails to commit due to some error.
     * @throws IllegalStateException If this session is not transacted.
     */
    public void commit()
            throws
            QpidException,
            IllegalStateException;

    /**
     * Rollback the receipt and the delivery of all messages exchanged by this session resources.
     *
     * @throws QpidException         If the session fails to rollback due to some error.
     * @throws IllegalStateException If this session is not transacted.
     */
    public void rollback()
            throws
            QpidException,
            IllegalStateException;

    /**
     * Set this session as transacted.
     * <p> This operation is irreversible.
     *
     * @throws QpidException         If the session fail to be transacted due to some error.
     * @throws IllegalStateException If this session is already transacted.
     */
    public void setTransacted()
            throws
            QpidException,
            IllegalStateException;

    /**
     * Declare a queue of name queueName
     * <p> see available options: {@link declareQueueOption}
     * <p> When non of the options are set then the receiver is created with:
     * <ul>
     * <li> auto_delete = false
     * <li> non-durable
     * <li> non-exccusive
     * <li> nowait = false
     * <li> not passive
     * </ul>
     *
     * @param queueName The name of the delcared queue.
     * @param options   Set of Options.
     * @throws QpidException If the session fails to declare the queue due to some error.
     * @see declareQueueOption
     */
    public void declareQueue(String queueName, declareQueueOption... options)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like queue name already exist?

    /**
     * Bind a queue with an exchange.
     *
     * @param queueName    The queue to be bound.
     * @param exchangeName The exchange name.
     * @param routingKey   The routing key.
     * @param nowait       nowait
     * @throws QpidException If the session fails to bind the queue due to some error.
     */
    public void bindQueue(String queueName, String exchangeName, String routingKey, boolean nowait)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like exchange does not exist?

    /**
     * Unbind a queue from an exchange.
     *
     * @param queueName    The queue to be unbound.
     * @param exchangeName The exchange name.
     * @param routingKey   The routing key.
     * @throws QpidException If the session fails to unbind the queue due to some error.
     */
    public void unbindQueue(String queueName, String exchangeName, String routingKey)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like exchange does not exist?

    /**
     * Purge a queue. i.e. delete all enqueued messages
     * TODO: Define the exact semantic i.e. are message sent to a dead letter queue?
     *
     * @param queueName The queue to be purged
     * @param nowait    nowait
     * @throws QpidException If the session fails to purge the queue due to some error.
     */
    public void purgeQueue(String queueName, boolean nowait)
            throws
            QpidException;

    /**
     * Delet a queue.
     * <p> see available options: {@link DeleteQueueOption}
     * <p> When non of the options are set then The queue is immediately deleted even
     * if it still contains messages or if ti is used by another resource.
     *
     * @param queueName The name of the queue to be deleted
     * @param options   Set of options
     * @throws QpidException If the session fails to delete the queue due to some error.
     * @see DeleteQueueOption
     */
    public void deleteQueue(String queueName, DeleteQueueOption options)
            throws
            QpidException;

    /**
     * Declare an exchange.
     * <p> see available options: {@link DeclareExchangeOption}
     * <p> When non of the options are set then the exchange is declared with:
     * <ul>
     * <li> auto_delete = false
     * <li> non-durable
     * <li> internal = false
     * <li> nowait = false
     * <li> not passive
     * </ul>
     *
     * @param exchangeName  The exchange name.
     * @param exchangeClass The fully qualified name of the exchange class.
     * @param options       Set of options.
     * @throws QpidException  If the session fails to declare the exchange due to some error.
     * @see DeclareExchangeOption
     */
    public void declareExchange(String exchangeName, String exchangeClass, DeclareExchangeOption... options)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like exchange already exist?

    /**
     * Delete an exchange.
     * <p> see available options: {@link DeclareExchangeOption}
     * <p> When non of the options are set then the exchange
     * Immediately deleted even if it is used by another resources.
     *
     * @param exchangeName The name of exchange to be deleted.
     * @param options      Set of options.
     * @throws QpidException If the session fails to delete the exchange due to some error.
     * @see DeleteExchangeOption 
     */
    public void deleteExchange(String exchangeName, DeleteExchangeOption... options)
            throws
            QpidException;
     //Todo: Do we need to define more specific exceptions like exchange does not exist?
}
