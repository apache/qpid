/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.server.store;

import org.apache.commons.configuration.Configuration;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.queue.AMQMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.queue.QueueRegistry;

import java.util.List;

public interface MessageStore
{
    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     * @param queueRegistry the registry of queues to be used by this store
     * @param base the base element identifier from which all configuration items are relative. For example, if the base
     * element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config the apache commons configuration object
     */
    void configure(QueueRegistry queueRegistry, String base, Configuration config) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     * @throws Exception
     */
    void close() throws Exception;

    void put(AMQMessage msg) throws AMQException;

    void removeMessage(long messageId) throws AMQException;

    void createQueue(AMQQueue queue) throws AMQException;

    void removeQueue(String name) throws AMQException;

    void enqueueMessage(String name, long messageId) throws AMQException;

    void dequeueMessage(String name, long messageId) throws AMQException;

    void beginTran() throws AMQException;

    void commitTran() throws AMQException;

    void abortTran() throws AMQException;

    boolean inTran();

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
    long getNewMessageId();
}


