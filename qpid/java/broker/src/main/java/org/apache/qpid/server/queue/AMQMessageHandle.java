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
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;

/**
 * A pluggable way of getting message data. Implementations can provide intelligent caching for example or
 * even no caching at all to minimise the broker memory footprint.
 */
public interface AMQMessageHandle extends BodyContentHolder
{
    ContentHeaderBody getContentHeaderBody() throws AMQException;

    /**
     *
     * @return the messageId for the message associated with this handle
     */
    Long getMessageId();


    /**
     * @return the size of the body
     */
    long getBodySize() throws AMQException;

    void addContentBodyFrame(ContentChunk contentBody, boolean isLastContentBody) throws AMQException;

    MessagePublishInfo getMessagePublishInfo() throws AMQException;

    boolean isPersistent();

    MessageMetaData setPublishAndContentHeaderBody(MessagePublishInfo messagePublishInfo,
                                                   ContentHeaderBody contentHeaderBody);

    void removeMessage() throws AMQException;

    long getArrivalTime();
}
