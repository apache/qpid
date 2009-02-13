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
package org.apache.qpid.server.routing;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.commons.configuration.Configuration;

public interface RoutingTable
{        
    /**
     * Called after instantiation in order to configure the message store. A particular implementation can define
     * whatever parameters it wants.
     *
     * @param virtualHost The virtual host using by this store
     * @param base        The base element identifier from which all configuration items are relative. For example, if
     *                    the base element is "store", the all elements used by concrete classes will be "store.foo" etc.
     * @param config      The apache commons configuration object.
     *
     * @throws Exception If any error occurs that means the store is unable to configure itself.
     */
    void configure(VirtualHost virtualHost, String base, Configuration config) throws Exception;

    /**
     * Called to close and cleanup any resources used by the message store.
     *
     * @throws Exception If the close fails.
     */
    void close() throws Exception;


    /**
     * Makes the specified exchange persistent.
     *
     * @param exchange The exchange to persist.
     *
     * @throws org.apache.qpid.AMQException If the operation fails for any reason.
     */
    void createExchange(Exchange exchange) throws AMQException;

    /**
     * Removes the specified persistent exchange.
     *
     * @param exchange The exchange to remove.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void removeExchange(Exchange exchange) throws AMQException;

    /**
     * Binds the specified queue to an exchange with a routing key.
     *
     * @param exchange   The exchange to bind to.
     * @param routingKey The routing key to bind by.
     * @param queue      The queue to bind.
     * @param args       Additional parameters.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void bindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException;

    /**
     * Unbinds the specified from an exchange under a particular routing key.
     *
     * @param exchange   The exchange to unbind from.
     * @param routingKey The routing key to unbind.
     * @param queue      The queue to unbind.
     * @param args       Additonal parameters.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void unbindQueue(Exchange exchange, AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException;

    /**
     * Makes the specified queue persistent.
     *
     * @param queue The queue to store.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void createQueue(AMQQueue queue) throws AMQException;

    /**
     * Makes the specified queue persistent.
     *
     * @param queue     The queue to store.
     * @param arguments The additional arguments to the binding
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void createQueue(AMQQueue queue, FieldTable arguments) throws AMQException;

    /**
     * Removes the specified queue from the persistent store.
     *
     * @param queue The queue to remove.
     *
     * @throws AMQException If the operation fails for any reason.
     */
    void removeQueue(final AMQQueue queue) throws AMQException;
}
