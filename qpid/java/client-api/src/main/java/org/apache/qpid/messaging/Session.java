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
package org.apache.qpid.messaging;

/**
 * A session represents a distinct 'conversation' which can involve sending and receiving messages to and from different addresses.
 */
public interface Session
{
    /**
     * Returns true if the session is closed.
     */
    public boolean isClosed();

    /**
     * Closes a session and all associated senders and receivers.
     */
    public void  close() throws MessagingException;

    /**
     * Commits all messages sent or received during the current transaction.
     */
    public void commit() throws MessagingException;

    /**
     * Rolls back all messages sent or received during the current transaction.
     */
    public void rollback() throws MessagingException;

    /**
     * Acknowledges all outstanding messages that have been received by the application on this session.
     * @param sync If true, request synchronization with the peer.
     */
    public void acknowledge(boolean sync) throws MessagingException;

    /**
     * Acknowledges the specified message.
     * @param message The message to be acknowledged
     * @param sync If true, request synchronization with the peer.
     */
    public void acknowledge (Message message, boolean sync) throws MessagingException;

    /**
     * Rejects the specified message.
     * @param message The message to be rejected.
     */
    public void reject(Message message) throws MessagingException;

    /**
     * Releases the specified message.
     * @param message The message to be released.
     */
    public void release(Message message) throws MessagingException;

    /**
     * Request synchronization with the peer.
     * @param block If true, block until synchronization is complete.
     */
    public void sync(boolean block) throws MessagingException;

    /**
     * Returns the total number of messages received and waiting to be fetched by all Receivers belonging to this session.
     */
    public int getReceivable() throws MessagingException;

    /**
     * Returns The number of messages received by this session that have been acknowledged, but for which that acknowledgment has not yet been confirmed by the peer.
     */
    public int getUnsettledAcks() throws MessagingException;

    /**
     * Returns the receiver for the next available message.
     * This method blocks until a message arrives or the timeout expires.
     * A timeout of zero never expires, and the call blocks indefinitely until a message arrives.
     * @param timeout The timeout value in milliseconds.
     * @return The receiver for the next available message.
     */
    public Receiver nextReceiver(long timeout) throws MessagingException;

    /**
     * Create a new sender through which messages can be sent to the specified address.
     * @param address @see Address
     */
    public Sender createSender(Address address) throws MessagingException;

    /**
     * Create a new sender through which messages can be sent to the specified address.
     * @param address The string containing a valid address @see Address for the format.
     */
    public Sender createSender (String address) throws MessagingException;

    /**
     * Create a new receiver through which messages can be received from the specified address.
     * @param address @see Address
     */
    public Receiver createReceiver (Address address) throws MessagingException;

    /**
     * Create a new receiver through which messages can be received from the specified address.
     * @param address The string containing a valid address @see Address for the format.
     */
    public Receiver createReceiver (String address) throws MessagingException;

    /**
     * Returns the connection this session is associated with.
     * @return
     */
    public Connection getConnection() throws MessagingException;

    public boolean hasError();

    /**
     * If the session has been rendered invalid by some exception,
     * this method will result in that exception being thrown on calling this method.
     */
    public void checkError() throws MessagingException;
}
