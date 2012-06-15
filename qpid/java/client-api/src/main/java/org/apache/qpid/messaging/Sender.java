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
 * Interface through which messages are sent
 */
public interface Sender
{
    /**
     * Sends a message.
     * @param message The message to be sent.
     * @param sync  Blocks until the peer confirms the message received.
     */
    public void send (Message message, boolean sync) throws MessagingException;

    /**
     * Cancels the receiver.
     */
    public void close() throws MessagingException;

    /**
     * Sets the capacity for the sender.
     * @param capacity Number of messages
     */
    public void setCapacity (int capacity) throws MessagingException;

    /**
     * Returns the capacity of this sender.
     * @return capacity
     */
    public int getCapacity() throws MessagingException;

    /**
     * Returns the number of messages for which there is available capacity.
     * @return available capacity
     */
    public int getAvailable() throws MessagingException;

    /**
     * Returns the number of sent messages pending confirmation of receipt by the broker.
     * @return unsettled message count.
     */
    public int getUnsettled() throws MessagingException;

    /**
     * Returns true if the sender was closed by a call to close()
     */
    public boolean isClosed() throws MessagingException;

    /**
     * Returns the name that uniquely identifies this sender within the given session.
     * @return Identifier for this Receiver.
     */
    public String getName() throws MessagingException;

    /**
     * Returns the session associated with this sender.
     */
    public Session getSession() throws MessagingException;
}
