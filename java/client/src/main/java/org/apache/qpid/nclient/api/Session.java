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
package org.apache.qpid.nclient.api;

import org.apache.qpid.nclient.exception.QpidException;
import org.apache.qpidity.Option;

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
	 * -------------------------------------
	 * Session housekeeping methods
	 * -------------------------------------
	 */
	
    /**
     * Close this session and any associated resources.
     *
     * @throws QpidException If the communication layer fails to close this session or if an internal error happens
     *                       when closing this session resources. .
     */
    public void sessionClose()
            throws
            QpidException;

    /**
     * Suspend this session resulting in interrupting the traffic with the broker.
     * When a session is suspend any operation of this session and of the associated resources is unavailable.
     *
     * @throws QpidException If the communication layer fails to suspend this session
     */
    public void sessionSuspend()
            throws
            QpidException;

    /**
     * Unsuspended a Session is equivalent to attaching a communication channel that can be used to
     * communicate with the broker. All the operations of this session and of the associated resources
     * are made available.
     *
     * @throws QpidException If the communication layer fails to unsuspend this session
     */
    public void sessionUnsuspend()
    
            throws
            QpidException;

    
    /**
	 * -------------------------------------
	 * Messaging methods
	 * -------------------------------------
	 */
    
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
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> NO_LOCAL
     * <li> EXCLUSIVE
     * <li> NO_ACQUIRE
     * <li> CONFIRM
     * </ul>
     * </p>
     * 
     * <p> In the absence of a particular option, the defaul value is:  
     * <ul>
     * <li> NO_LOCAL = false
     * <li> EXCLUSIVE = false
     * <li> PRE-ACCQUIRE
     * <li> CONFIRM = false
     * </ul>
     * </p>
     * 
     * @param queueName The queue this receiver is receiving messages from.
     * @param options   Set of Options.
     * @return A receiver for queue queueName.
     * @throws QpidException If the session fails to create the receiver due to some error.
     * @see Option
     */
    public MessageReceiver createReceiver(String queueName, Option... options)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like queue name not valid?

    
    /**
	 * -------------------------------------
	 * Transaction methods
	 * -------------------------------------
	 */

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
     * @throws QpidException         If the session fails to be transacted due to some error.
     * @throws IllegalStateException If this session is already transacted.
     */
    public void setTransacted()
            throws
            QpidException,
            IllegalStateException;

    /**
	 * -------------------------------------
	 * Queue methods
	 * -------------------------------------
	 */

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
     * 
     * <p>In the absence of a particular option, the defaul value is false for each option
     * 
     * @param queueName The name of the delcared queue.
     * @param options   Set of Options.
     * @throws QpidException If the session fails to declare the queue due to some error.
     * @see Option
     */
    public void queueDeclare(String queueName, Option... options)
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
    public void queueBind(String queueName, String exchangeName, String routingKey)
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
    public void queueUnbind(String queueName, String exchangeName, String routingKey)
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
    public void queuePurge(String queueName)
            throws
            QpidException;

    /**
     * Delet a queue.
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> IF_EMPTY
     * <li> IF_UNUSE
     * <li> NO_WAIT
     * </ul>
     * </p>
     * 
     * <p>In the absence of a particular option, the defaul value is false for each option</p>
     *
     * @param queueName The name of the queue to be deleted
     * @param options   Set of options
     * @throws QpidException If the session fails to delete the queue due to some error.
     * @see Option
     * 
     * Following are the valid options 
     */
    public void queueDelete(String queueName, Option options)
            throws
            QpidException;

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
     * 
     * <p>In the absence of a particular option, the defaul value is false for each option</p>     *
     *
     * @param exchangeName  The exchange name.
     * @param exchangeClass The fully qualified name of the exchange class.
     * @param options       Set of options.
     * @throws QpidException  If the session fails to declare the exchange due to some error.
     * @see Option
     */
    public void exchangeDeclare(String exchangeName, String exchangeClass, String alternateExchange, Option... options)
            throws
            QpidException;
    //Todo: Do we need to define more specific exceptions like exchange already exist?

    /**
     * Delete an exchange.
     * <p> Following are the valid options for createReceive
     * <ul>
     * <li> IF_UNUSEDL
     * <li> NO_WAIT
     * </ul>
     * </p>
     * 
     * <p>In the absence of a particular option, the defaul value is false for each option
     *    Immediately deleted even if it is used by another resources.</p>
     *
     * @param exchangeName The name of exchange to be deleted.
     * @param options      Set of options.
     * @throws QpidException If the session fails to delete the exchange due to some error.
     * @see Option
     */
    public void exchangeDelete(String exchangeName, Option... options)
            throws
            QpidException;
     //Todo: Do we need to define more specific exceptions like exchange does not exist?
}
