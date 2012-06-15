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
 * Interface through which messages are received.
 */
public interface Receiver
{
    /**
     * Retrieves a message from this receivers local queue, or waits for upto the specified timeout for a message to become available.
     * A timeout of zero never expires, and the call blocks indefinitely until a message arrives.
     * @param timeout Timeout in milliseconds.
     * @return The message received and null if not.
     */
    public Message get(long timeout) throws MessagingException;

    /**
     * Retrieves a message for this receivers subscription or waits for up to the specified timeout for one to become available.
     * A timeout of zero never expires, and the call blocks indefinitely until a message arrives.
     * @param timeout Timeout in milliseconds.
     * @return The message received and null if not.
     */
    public Message fetch(long timeout) throws MessagingException;

    /**
     * Sets the capacity for the receiver.
     * @param capacity Number of messages
     */
    public void setCapacity (int capacity) throws MessagingException;

    /**
     * Returns the capacity of this receiver
     * @return capacity
     */
    public int getCapacity() throws MessagingException;

    /**
     * Returns the number of messages for which there is available capacity.
     * @return available capacity
     */
    public int getAvailable() throws MessagingException;

    /**
     * Returns The number of messages received (by this receiver) that have been acknowledged, but for which that acknowledgment has not yet been confirmed by the peer.
     * @return unsettled message count.
     */
    public int getUnsettled() throws MessagingException;

    /**
     * Cancels this receiver.
     */
    public void close() throws MessagingException;

    /**
     * Returns true if the receiver was closed by a call to close()
     */
    public boolean isClosed() throws MessagingException;

    /**
     * Returns the name that uniquely identifies this receiver within the given session.
     * @return Identifier for this Receiver.
     */
    public String getName() throws MessagingException;

    /**
     * Returns the session associated with this receiver.
     */
    public Session getSession() throws MessagingException;

}
