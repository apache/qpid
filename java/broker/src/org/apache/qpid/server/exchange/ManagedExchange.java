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
package org.apache.qpid.server.exchange;

import javax.management.openmbean.TabularData;
import javax.management.JMException;
import java.io.IOException;

/**
 * The management interface exposed to allow management of an Exchange.
 * @author  Robert J. Greig
 * @author  Bhupendra Bhardwaj
 * @version 0.1
 */
public interface ManagedExchange
{
    static final String TYPE = "Exchange";

    /**
     * Returns the name of the managed exchange.
     * @return the name of the exchange.
     * @throws IOException
     */
    String getName() throws IOException;

    /**
     * Tells if the exchange is durable or not.
     * @return true if the exchange is durable.
     * @throws IOException
     */
    boolean isDurable() throws IOException;

    /**
     * Tells if the exchange is set for autodelete or not.
     * @return true if the exchange is set as autodelete.
     * @throws IOException
     */
    boolean isAutoDelete() throws IOException;

    int getTicketNo() throws IOException;


    // Operations

    /**
     * Returns all the bindings this exchange has with the queues.
     * @return  the bindings with the exchange.
     * @throws IOException
     * @throws JMException
     */
    TabularData viewBindings()
        throws IOException, JMException;

    /**
     * Creates new binding with the given queue and binding.
     * @param QueueName
     * @param binding
     * @throws JMException
     */
    void createBinding(String QueueName, String binding)
        throws JMException;

}