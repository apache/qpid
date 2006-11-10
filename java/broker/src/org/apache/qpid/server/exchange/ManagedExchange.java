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

import org.apache.qpid.server.management.MBeanAttribute;
import org.apache.qpid.server.management.MBeanOperation;
import org.apache.qpid.server.management.MBeanOperationParameter;

import javax.management.openmbean.TabularData;
import javax.management.JMException;
import javax.management.MBeanOperationInfo;
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
    @MBeanAttribute(name="Name", description="Name of exchange")
    String getName() throws IOException;

    @MBeanAttribute(name="TicketNo", description="Exchange Ticket No")
    Integer getTicketNo() throws IOException;

    /**
     * Tells if the exchange is durable or not.
     * @return true if the exchange is durable.
     * @throws IOException
     */
    @MBeanAttribute(name="Durable", description="true if Exchange is durable")
    boolean isDurable() throws IOException;

    /**
     * Tells if the exchange is set for autodelete or not.
     * @return true if the exchange is set as autodelete.
     * @throws IOException
     */
    @MBeanAttribute(name="AutoDelete", description="true if Exchange is AutoDelete")
    boolean isAutoDelete() throws IOException;

    // Operations

    /**
     * Returns all the bindings this exchange has with the queues.
     * @return  the bindings with the exchange.
     * @throws IOException
     * @throws JMException
     */
    @MBeanOperation(name="viewBindings", description="view the queue bindings for this exchange")
    TabularData viewBindings() throws IOException, JMException;

    /**
     * Creates new binding with the given queue and binding.
     * @param queueName
     * @param binding
     * @throws JMException
     */
    @MBeanOperation(name="createBinding",
                         description="create a new binding with this exchange",
                         impact= MBeanOperationInfo.ACTION)
    void createBinding(@MBeanOperationParameter(name="queue name", description="queue name") String queueName,
                       @MBeanOperationParameter(name="binding", description="queue binding")String binding)
        throws JMException;

}