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

package org.apache.qpid.server.management;

import javax.management.JMException;
import java.io.IOException;

/**
 * The ManagedBroker is the management interface to expose management
 * features of the Broker.
 *
 * @author   Bhupendra Bhardwaj
 * @version  0.1
 */
public interface ManagedBroker
{
    static final String TYPE = "BrokerManager";

    /**
     * Creates a new Exchange.
     * @param name
     * @param type
     * @param durable
     * @param passive
     * @throws IOException
     * @throws JMException
     */
    void createNewExchange(String name, String type, boolean durable, boolean passive)
        throws IOException, JMException;

    /**
     * unregisters all the channels, queuebindings etc and unregisters
     * this exchange from managed objects.
     * @param exchange
     * @throws IOException
     * @throws JMException
     */
    void unregisterExchange(String exchange)
        throws IOException, JMException;

    /**
     * Create a new Queue on the Broker server
     * @param queueName
     * @param durable
     * @param owner
     * @param autoDelete
     * @throws IOException
     * @throws JMException
     */
    void createQueue(String queueName, boolean durable, String owner, boolean autoDelete)
        throws IOException, JMException;

    /**
     * Unregisters the Queue bindings, removes the subscriptions and unregisters
     * from the managed objects.
     * @param queueName
     * @throws IOException
     * @throws JMException
     */
    void deleteQueue(String queueName)
        throws IOException, JMException;
}
