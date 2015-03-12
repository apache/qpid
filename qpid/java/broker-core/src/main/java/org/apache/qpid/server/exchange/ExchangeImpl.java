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
package org.apache.qpid.server.exchange;

import java.util.Map;

import org.apache.qpid.server.binding.BindingImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.queue.AMQQueue;

public interface ExchangeImpl<T extends ExchangeImpl<T>> extends Exchange<T>, ExchangeReferrer, MessageDestination
{

    /**
     * @return true if the exchange will be deleted after all queues have been detached
     */
    boolean isAutoDelete();


    boolean addBinding(String bindingKey, AMQQueue queue, Map<String, Object> arguments);
    boolean deleteBinding(String bindingKey, AMQQueue queue);
    boolean hasBinding(String bindingKey, AMQQueue queue);


    boolean replaceBinding(String bindingKey,
                           AMQQueue queue,
                           Map<String, Object> arguments);

    void deleteWithChecks();

    /**
     * Determines whether a message would be isBound to a particular queue using a specific routing key and arguments
     * @param bindingKey
     * @param arguments
     * @param queue
     * @return
     */

    boolean isBound(String bindingKey, Map<String,Object> arguments, AMQQueue queue);

    /**
     * Determines whether a message would be isBound to a particular queue using a specific routing key
     * @param bindingKey
     * @param queue
     * @return
     */

    boolean isBound(String bindingKey, AMQQueue queue);

    /**
     * Determines whether a message is routing to any queue using a specific _routing key
     * @param bindingKey
     * @return
     */
    boolean isBound(String bindingKey);

    /**
     * Returns true if this exchange has at least one binding associated with it.
     * @return
     */
    boolean hasBindings();

    boolean isBound(AMQQueue queue);

    boolean isBound(Map<String, Object> arguments);

    boolean isBound(String bindingKey, Map<String, Object> arguments);

    boolean isBound(Map<String, Object> arguments, AMQQueue queue);

    void removeReference(ExchangeReferrer exchange);

    void addReference(ExchangeReferrer exchange);

    boolean hasReferrers();

    BindingImpl getBinding(String bindingName, AMQQueue queue);

    EventLogger getEventLogger();

    void addBinding(BindingImpl binding);


    public interface BindingListener
    {
        void bindingAdded(ExchangeImpl exchange, BindingImpl binding);
        void bindingRemoved(ExchangeImpl exchange, BindingImpl binding);
    }

}
