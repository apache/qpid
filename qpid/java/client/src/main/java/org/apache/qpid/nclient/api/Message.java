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


import org.apache.qpid.nclient.FieldTable;
import org.apache.qpidity.QpidException;

import java.nio.ByteBuffer;

/**
 * A message is sent and received by resources. It is composed of a set of header and a payload.
 */
public interface Message
{
    /**
     * Get this message auto-allocated messageID.
     *
     * @return This message ID.
     */
    public long getMessageID();

    /**
     * Set this message headers
     * <p> Previous headers are reset.
     *
     * @param headers The message headers as a field table.
     * @see FieldTable
     */
    public void setHeaders(FieldTable headers);

    /**
     * Access to this message headers.
     *
     * @return This message headers as a field table.
     */
    public FieldTable getHeaders();

    /**
     * Set this message payload.
     *
     * @param buffer This message payload.
     */
    public void setBody(ByteBuffer buffer);

    /**
     * Access this message body.
     *
     * @return The payload of this message.
     */
    public ByteBuffer getBody();

    /**
     * Acknowledge the receipt of this message.
     * <p>The message must have been previously acquired either by receiving it in
     * pre-acquire mode or by explicitly acquiring it.
     *
     * @throws QpidException         If the acknowledgement of the message fails due to some error.
     * @throws IllegalStateException If this messages is not acquired.
     */
    public void acknowledge() throws QpidException, IllegalStateException;

    /**
     * Acknowledge the receipt of an acquired messages which IDs are within
     * the interval [this.messageID, message.messageID]
     *
     * @param message The last message to be acknowledged.
     * @throws QpidException         If the acknowledgement of this set of messages fails due to some error.
     * @throws IllegalStateException If some messages are not acquired.
     */
    public void acknowledge(Message message) throws QpidException, IllegalStateException;

    /**
     * Reject a previously acquired message.
     * <p> A rejected message will not be delivered to any receiver
     * and may be either discarded or moved to the broker dead letter queue.
     *
     * @throws QpidException         If this message cannot be rejected dus to some error
     * @throws IllegalStateException If this message is not acquired.
     */
    public void reject() throws QpidException, IllegalStateException;

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
    public boolean acquire() throws QpidException, IllegalStateException;

    /**
     * Give up responsibility for processing this message.
     *
     * @throws QpidException          If this message cannot be released dus to some error.
     * @throws IllegalStateException  If this message has already been acknowledged.
     */
    public void release() throws QpidException, IllegalStateException;
}
