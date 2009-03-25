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

import org.apache.qpid.server.configuration.VirtualHostConfiguration;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.commons.configuration.ConfigurationException;

public interface QueueBackingStore
{
    /**
     * Retrieve the message with a given ID
     *
     * This method must be thread safe.
     *
     * Multiple calls to load with a given messageId DO NOT need to return the same object.
     *
     * @param messageId the id of the message to retreive.
     * @return
     */
    AMQMessage load(Long messageId);

    /**
     * Store a message in the BackingStore.
     *
     * This method must be thread safe understanding that multiple message objects may be the same data.
     *
     * Allowing a thread to return from this method means that it is safe to call load()
     *
     * Implementer guide:
     * Until the message has been loaded the message references will all be the same object.
     *
     * One appraoch as taken by the @see FileQueueBackingStore is to block aimulataneous calls to this method 
     * until the message is fully on disk. This can be done by synchronising on message as initially it is always the
     * same object. Only after a load has taken place will there be a discrepency.
     *
     *
     * @param message the message to unload
     * @throws UnableToFlowMessageException
     */
    void unload(AMQMessage message) throws UnableToFlowMessageException;

    void delete(Long messageId);

    void close();
}
