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
package org.apache.qpid.management.common.mbeans;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.management.JMException;
import javax.management.MBeanOperationInfo;
import javax.management.openmbean.TabularData;

import org.apache.qpid.management.common.mbeans.annotations.MBeanAttribute;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperation;
import org.apache.qpid.management.common.mbeans.annotations.MBeanOperationParameter;

/**
 * The management interface exposed to allow management of an Exchange.
 *
 * @version 1.8
 */
public interface ManagedExchange
{
    static final String TYPE = "Exchange";
    
    //TabularType and contained CompositeType key/description info for DIRECT/TOPIC/FANOUT exchanges.
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set. 
    String BINDING_KEY = "Binding Key";
    String QUEUE_NAMES = "Queue Names";

    List<String> COMPOSITE_ITEM_NAMES = Collections.unmodifiableList(Arrays.asList(BINDING_KEY, QUEUE_NAMES));
    List<String> COMPOSITE_ITEM_DESCRIPTIONS = Collections.unmodifiableList(Arrays.asList(BINDING_KEY, QUEUE_NAMES));
    List<String> TABULAR_UNIQUE_INDEX = Collections.unmodifiableList(Arrays.asList(BINDING_KEY));

    //TabularType and contained CompositeType key/description info for HEADERS exchange only.
    //For compatibility reasons, DONT MODIFY the existing key values if expanding the set. 
    String HDR_BINDING_NUMBER = "Binding No";
    String HDR_QUEUE_NAME = "Queue  Name";
    String HDR_QUEUE_BINDINGS = "Queue Bindings";

    List<String> HEADERS_COMPOSITE_ITEM_NAMES = Collections.unmodifiableList(Arrays.asList(HDR_BINDING_NUMBER, HDR_QUEUE_NAME, HDR_QUEUE_BINDINGS));
    List<String> HEADERS_COMPOSITE_ITEM_DESC = Collections.unmodifiableList(Arrays.asList(HDR_BINDING_NUMBER, HDR_QUEUE_NAME, HDR_QUEUE_BINDINGS));
    List<String> HEADERS_TABULAR_UNIQUE_INDEX = Collections.unmodifiableList(Arrays.asList(HDR_BINDING_NUMBER));

    /**
     * Returns the name of the managed exchange.
     * @return the name of the exchange.
     * @throws IOException
     */
    @MBeanAttribute(name="Name", description=TYPE + " Name")
    String getName() throws IOException;

    @MBeanAttribute(name="ExchangeType", description="Exchange Type")
    String getExchangeType() throws IOException;

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
    @MBeanOperation(name="bindings", description="view the queue bindings for this exchange")
    TabularData bindings() throws IOException, JMException;

    /**
     * Creates new binding with the given queue and binding.
     * @param queueName
     * @param binding
     * @throws JMException
     */
    @MBeanOperation(name="createNewBinding",
                    description="create a new binding with this exchange",
                    impact= MBeanOperationInfo.ACTION)
    void createNewBinding(@MBeanOperationParameter(name= ManagedQueue.TYPE, description="Queue name") String queueName,
                          @MBeanOperationParameter(name="Binding", description="New binding")String binding)
        throws JMException;
    
    /**
     * Removes an exchange binding from a queue.
     * 
     * @param exchangeName the Exchange name
     * @param routingKey the routing key
     * @throws IOException
     * @throws JMException
     * @since Qpid JMX API 1.8
     */
    @MBeanOperation(name="removeBinding",
                    description="Removes an exchange binding from the Queue",
                    impact= MBeanOperationInfo.ACTION)
    void removeBinding(@MBeanOperationParameter(name= ManagedQueue.TYPE, description="Queue name") String queueName,
                       @MBeanOperationParameter(name="Binding", description="Binding key")String binding)
            throws IOException, JMException;
}
