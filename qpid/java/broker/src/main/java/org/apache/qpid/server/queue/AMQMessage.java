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

import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.abstraction.ContentChunk;
import org.apache.qpid.framing.abstraction.MessagePublishInfo;
import org.apache.qpid.server.protocol.AMQProtocolSession;
import org.apache.qpid.server.store.StoreContext;
import org.apache.qpid.AMQException;

import java.util.Iterator;

public interface AMQMessage
{
    //Get Content relating to this message

    Long getMessageId();

    Iterator<AMQDataBlock> getBodyFrameIterator(AMQProtocolSession protocolSession, int channel);

    Iterator<ContentChunk> getContentBodyIterator();

    ContentHeaderBody getContentHeaderBody();

    ContentChunk getContentChunk(int index);

    Object getPublisherClientInstance();

    Object getPublisherIdentifier();

    MessagePublishInfo getMessagePublishInfo();

    int getBodyCount();

    long getSize();

    long getArrivalTime();



    //Check the status of this message

    /** Is this a persistent message
     *
     * @return true if the message is persistent
     */
    boolean isPersistent();


    boolean isImmediate();


    void setClientIdentifier(AMQProtocolSession.ProtocolSessionIdentifier sessionIdentifier);

    /**
     * This is called when all the content has been received.
     * @param storeContext
     *@param messagePublishInfo
     * @param contentHeaderBody @throws org.apache.qpid.AMQException
     */
    void setPublishAndContentHeaderBody(StoreContext storeContext, MessagePublishInfo messagePublishInfo, ContentHeaderBody contentHeaderBody)
            throws AMQException;

    void addContentBodyFrame(StoreContext storeContext, ContentChunk contentChunk, boolean isLastContentBody)
            throws AMQException;

    void recoverFromMessageMetaData(MessageMetaData mmd);

    void recoverContentBodyFrame(ContentChunk contentChunk, boolean isLastContentBody) throws AMQException;


    String toString();

    String debugIdentity();

    void setExpiration(long expiration);

    long getExpiration();
}
