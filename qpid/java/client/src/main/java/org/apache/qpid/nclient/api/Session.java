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

import java.util.Map;
import java.util.UUID;

import org.apache.qpidity.QpidException;
import org.apache.qpidity.Option;
import org.apache.qpidity.Header;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.api.StreamingMessageListener;

/**
 * <p>A session is associated with a connection.
 * When created a Session is not attached with an underlying channel.
 * Session is single threaded </p>
 */
public interface Session
{

    //------------------------------------------------------ 
	//                 Session housekeeping methods 
	//------------------------------------------------------
	
    /**
     * Close this session and any associated resources.
     *
     * @throws QpidException If the communication layer fails to close this session or if an internal error happens
     *                       when closing this session resources. .
     */
    public void sessionClose() throws QpidException;

    /**
     * Suspend this session resulting in interrupting the traffic with the broker.
     * An important distinction btw sessionFlow() and this method
     * is that the session timer will start to tick in suspend.
     * When a session is suspend any operation of this session and of the associated resources are unavailable.
     *
     * @throws QpidException If the communication layer fails to suspend this session
     */
    public void sessionSuspend() throws QpidException;


    /**
     * This will stop the communication flow in the session
     * However the session is still considered active and the session timer will not tick.
     * This method is used for session level flow control.
     *
     * @throws QpidException If the communication layer fails to execute the flow method properly
     */
    public void sessionFlow(Option... options) throws QpidException;

    /**
     * This is used for failover. This will resume an existing session
     *
     * @throws QpidException If the communication layer fails to execute this properly
     */
    public void sessionResume(UUID sessionId) throws QpidException;

    
    //------------------------------------------------------ 
	//                 Messaging methods 
    //                   Producer           
	//------------------------------------------------------
    
	/**
     * Transfer the given message. 
     * This is a convinience method
     *
     * @param destination The exchange the message being sent.
     * @return msg The Message to be sent
     * @throws QpidException If the session fails to send the message due to some error
     */
    public void messageTransfer(String destination,Message msg,Option... options)throws QpidException; 
    
    /**
     * Transfer the given message.
     * <p> Following are the valid options for messageTransfer
     * <ul>
     * <li> CONFIRM
     * <li> PRE_ACCQUIRE
     * </ul>
     * </p>
     * 
     * <p> In the absence of a particular option, the defaul value is:
     * <ul>
     * <li> CONFIRM = false
     * <li> NO-ACCQUIRE
     * </ul>
     * </p>
     *
     * @param destination The exchange the message being sent.
     * @return options set of options
     * @throws QpidException If the session fails to send the message due to some error
     */
    public void messageTransfer(String destination,Option... options)throws QpidException; 
    
    /**
     * Add the following headers to content bearing frame
     *
     * @param Header Either DeliveryProperties or ApplicationProperties
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void messageHeaders(Header ... headers)throws QpidException; 
    
    /**
     * Add the following byte array to the content.
     * This method is useful when streaming large messages
     *
     * @param src data to be added or streamed
     * @throws QpidException If the session fails to execute the method due to some error
     */
    public void data(byte[] src)throws QpidException; 
    
    /**
     * Signals the end of data for the message.     * 
     * This method is useful when streaming large messages
     *
     * @throws QpidException If the session fails to execute the method due to some error
     */    
    public void endData()throws QpidException; 

    /**
     * Acknowledge the receipt of this message.
     * <p>The message must have been previously acquired either by receiving it in
     * pre-acquire mode or by explicitly acquiring it.
     *
     * @throws QpidException         If the acknowledgement of the message fails due to some error.
     * @throws IllegalStateException If this messages is not acquired.
     */
    public void messageAcknowledge() throws QpidException;

    /**
     * Reject a previously acquired message.
     * <p> A rejected message will not be delivered to any receiver
     * and may be either discarded or moved to the broker dead letter queue.
     *
     * @throws QpidException         If this message cannot be rejected dus to some error
     * @throws IllegalStateException If this message is not acquired.
     */
    public void messageReject() throws QpidException;

    /**
     * Try to acquire this message hence releasing it form the queue. This means that once acknowledged,
     * this message will not be delivered to any other receiver.
     * <p> As this message may have been consumed by another receiver, message acquisition can fail.
     * The outcome of the acquisition is returned as a Boolean.
     *
     * @return True if the message is successfully acquired, False otherwise.
     * @throws QpidException         If this message cannot be acquired dus to some error
     * @throws IllegalStateException If this message has already been acquired.
     */
    public boolean messageAcquire() throws QpidException;

    /**
     * Give up responsibility for processing this message.
     *
     * @throws QpidException          If this message cannot be released dus to some error.
     * @throws IllegalStateException  If this message has already been acknowledged.
     */
    public void messageRelease() throws QpidException;
    
    
    //------------------------------------------------------ 
	//                 Messaging methods 
    //                   Consumer           
	//------------------------------------------------------
    
    /**
     * Create a message receiver for receiving messages from queue queueName. 
     * <p> Following are the valid options for messageSubscribe
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
     * @param queue The queue this receiver is receiving messages from.
     * @param destination The destination for the subscriber ,a.k.a the delivery tag.
     * @param options   Set of Options.
     * @throws QpidException If the session fails to create the receiver due to some error.
     * @see Option
     */
    public void messageSubscribe(String queue, String destination, Map<String,?> filter, Option ... _options) throws QpidException;
    
    public void messageSubscribe(String queue, String destination, Map<String,?> filter,StreamingMessageListener listener,Option ... _options) throws QpidException;

    /**
     * Cancels a subscription
     * 
     * @param destination The destination for the subscriber used at subscription
     */
    public void messageCancel(String destination) throws QpidException;
    
    /**
     * We currently allow one listerner per destination
     * 
     * @param destination
     * @param listener
     */
    public void setMessageListener(String destination,StreamingMessageListener listener);
        
    /**
     * We currently allow one listerner per destination
     * 
     * @param destination
     * @param listener
     */
    public void setMessageListener(String destination,MessageListener listener);
    
    
    // -----------------------------------------------
    //            Transaction methods 
    //  ----------------------------------------------

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
    
    
    /**
     * Selects the session for transactions
     * 
     * @throws QpidException
     */
    public void txSelect() throws QpidException;
    
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
     * @param queueName The name of the delcared queue.
     * @param options   Set of Options.
     * @throws QpidException If the session fails to declare the queue due to some error.
     * @see Option
     */
    public void queueDeclare(String queueName, String alternateExchange, Map<String, ?> arguments,
                             Option... options) throws QpidException;
    //Todo: Do we need to define more specific exceptions like queue name already exist?

    /**
     * Bind a queue with an exchange.
     *
     * @param queueName    The queue to be bound.
     * @param exchangeName The exchange name.
     * @param routingKey   The routing key.
     * @throws QpidException If the session fails to bind the queue due to some error.
     */
    public void queueBind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments) throws
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
    public void queueUnbind(String queueName, String exchangeName, String routingKey, Map<String, ?> arguments) throws
                                                                                                                QpidException;
    //Todo: Do we need to define more specific exceptions like exchange does not exist?

    /**
     * Purge a queue. i.e. delete all enqueued messages
     * TODO: Define the exact semantic i.e. are message sent to a dead letter queue?
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
    //Todo: Do we need to define more specific exceptions like exchange does not exist?
}
