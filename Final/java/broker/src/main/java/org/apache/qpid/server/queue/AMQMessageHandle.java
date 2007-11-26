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
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;

/**
 * A pluggable way of getting message data. Implementations can provide intelligent caching for example or
 * even no caching at all to minimise the broker memory footprint.
 *
 * The method all take a messageId to avoid having to store it in the instance - the AMQMessage container
 * must already keen the messageId so it is pointless storing it twice.
 */
public interface AMQMessageHandle
{
    ContentHeaderBody getContentHeaderBody(StoreContext context, Long messageId) throws AMQException;

    /**
     * @return the number of body frames associated with this message
     */
    int getBodyCount(StoreContext context, Long messageId) throws AMQException;

    /**
     * @return the size of the body
     */
    long getBodySize(StoreContext context, Long messageId) throws AMQException;

    /**
     * Get a particular content body
     * @param index the index of the body to retrieve, must be between 0 and getBodyCount() - 1
     * @return a content body
     * @throws IllegalArgumentException if the index is invalid
     */
    ContentChunk getContentChunk(StoreContext context, Long messageId, int index) throws IllegalArgumentException, AMQException;

    void addContentBodyFrame(StoreContext storeContext, Long messageId, ContentChunk contentBody, boolean isLastContentBody) throws AMQException;

    MessagePublishInfo getMessagePublishInfo(StoreContext context, Long messageId) throws AMQException;

    boolean isRedelivered();

    void setRedelivered(boolean redelivered);

    boolean isPersistent(StoreContext context, Long messageId) throws AMQException;

    void setPublishAndContentHeaderBody(StoreContext storeContext, Long messageId, MessagePublishInfo messagePublishInfo,
                                        ContentHeaderBody contentHeaderBody)
            throws AMQException;

    void removeMessage(StoreContext storeContext, Long messageId) throws AMQException;

    void enqueue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException;

    void dequeue(StoreContext storeContext, Long messageId, AMQQueue queue) throws AMQException;

    long getArrivalTime();
}
