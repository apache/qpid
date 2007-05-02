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

/**
 * A storable message can be persisted in the message store.
 *
 * Created by Arnaud Simon
 * Date: 03-Apr-2007
 * Time: 08:56:48
 */
public interface StorableMessage
{
    /**
     * The message ID is used by the store to identify a message.
     *
     * @return The message identifier
     */
    public long getMessageId();

    /**
     * Get the message header body that is saved when the message is staged.
     *
     * @return The message header body
     */
    public byte[] getHeaderBody();

    /**
     * Get the message header body size in bytes.
     *
     * @return The message header body size
     */
    public int getHeaderSize();

    /**
     * Get the message payload. This is required when the message is
     * enqueued without any prior staging.
     * <p> When the message is staged, the payload  can be partial or even empty.
     *
     * @return The message payload
     */
    public byte[] getData();

    /**
     * Get the message payload size in bytes.
     *
     * @return The message payload size in bytes
     */
    public int getPayloadSize();

    /**
     * Specify whether this message has been enqueued
     * 
     * @return true if this message is enqueued, false otherwise
     */
    public boolean isEnqueued();

    /**
     * This is called by the message store when this message is enqueued in the message store.
     *
     * @param queue The storable queue into which the message is enqueued
     */
    public void enqueue(StorableQueue queue);

    /**
     *  This is called by the message store when this message is dequeued.
     *
     * @param queue The storable queue out of which the message is dequeued
     */
    public void dequeue(StorableQueue queue);

    /**
     * A message can be enqueued in several queues.
     * The queue position represents the index of the provided queue within the ordered
     * list of queues the message has been enqueued.
     * <p>For example:
     * <p> If the message is successively enqueued in queue Q1, Q2 and Q3 then
     * the position of Q1 is 0, position of Q2 is 1 and position of Q3 is 2.
     * <p> If the message is dequeud form Q2 then position of Q1 is stil 0 but position
     * of Q3 becomes 1.
     *
     * @param queue The storable queue for which the position should be determined
     *
     * @return The position of the specified storable queue
     */
    public int getQueuePosition(StorableQueue queue);

    /**
     * Indicates whether this message has been staged.
     * 
     * @return True if the message has been staged, false otherwise
     */
    public boolean isStaged();

    /**
     * Call by the message store when this message is staged. 
     */
    public void staged();

}
