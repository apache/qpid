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
package org.apache.qpid.server.store;

import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.MessageMetaData;
import org.apache.qpid.server.virtualhost.VirtualHost;

public interface MessageStore
{
    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     * @param virtualHost the virtual host using by this store
     * @param base the base element identifier from which all configuration items are relative. For example, if the base
     * element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config the apache commons configuration object
     * @throws Exception if an error occurs that means the store is unable to configure itself
     */
    void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     * @throws Exception if close fails
     */
    void close() throws Exception;

    void removeMessage(StoreContext storeContext, Long messageId) throws AMQException;

    void createQueue(AMQQueue queue) throws AMQException;

    void removeQueue(AMQShortString name) throws AMQException;

    void enqueueMessage(StoreContext context, AMQShortString name, Long messageId) throws AMQException;

    void dequeueMessage(StoreContext context, AMQShortString name, Long messageId) throws AMQException;

    void beginTran(StoreContext context) throws AMQException;

    void commitTran(StoreContext context) throws AMQException;

    void abortTran(StoreContext context) throws AMQException;

    boolean inTran(StoreContext context);

    /**
     * Recreate all queues that were persisted, including re-enqueuing of existing messages
     * @return
     * @throws AMQException
     */
    List<AMQQueue> createQueues() throws AMQException;

    /**
     * Return a valid, currently unused message id.
     * @return a message id
     */
    Long getNewMessageId();

    void storeContentBodyChunk(StoreContext context, Long messageId, int index, ContentBody contentBody, boolean lastContentBody) throws AMQException;

    void storeMessageMetaData(StoreContext context, Long messageId, MessageMetaData messageMetaData) throws AMQException;

    MessageMetaData getMessageMetaData(Long messageId) throws AMQException;

    ContentBody getContentBodyChunk(Long messageId, int index) throws AMQException;

}
