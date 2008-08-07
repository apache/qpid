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
package org.apache.qpid.extras.exchanges.diagnostic;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;

import javax.management.JMException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.BasicContentHeaderProperties;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.queue.IncomingMessage;
import org.apache.qpid.server.queue.AMQQueue;

import org.apache.qpid.junit.extensions.util.SizeOf;

/**
 * 
 * This is a special diagnostic exchange type which doesn't actually do anything
 * with messages. When it receives a message, it writes information about the
 * current memory usage to the "memory" property of the message and places it on the
 * diagnosticqueue for retrieval 
 * 
 * @author Aidan Skinner
 * 
 */

public class DiagnosticExchange extends AbstractExchange
{
   
    public static final AMQShortString DIAGNOSTIC_EXCHANGE_CLASS = new AMQShortString("x-diagnostic");
    public static final AMQShortString DIAGNOSTIC_EXCHANGE_NAME = new AMQShortString("diagnostic");

    /**
     * the logger.
     */
   //private static final Logger _logger = Logger.getLogger(DiagnosticExchange.class);

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Diagnostic Exchange")
    private final class DiagnosticExchangeMBean extends ExchangeMBean
    {

        /**
         * Usual constructor.
         * 
         * @throws JMException
         */
        @MBeanConstructor("Creates an MBean for AMQ Diagnostic exchange")
        public DiagnosticExchangeMBean() throws JMException
        {
            super();
            _exchangeType = "diagnostic";
            init();
        }

        /**
         * Returns nothing, there can be no tabular data for this...
         * 
         * @throws OpenDataException
         * @returns null
         * @todo ... or can there? Could this actually return all the
         *       information in one easy to read table?
         */
        public TabularData bindings() throws OpenDataException
        {
            return null;
        }

        /**
         * This exchange type doesn't support queues, so this method does
         * nothing.
         * 
         * @param queueName
         *            the queue you'll fail to create
         * @param binding
         *            the binding you'll fail to create
         * @throws JMException
         *             an exception that will never be thrown
         */
        public void createNewBinding(String queueName, String binding) throws JMException
        {
            // No Op
        }

    } // End of MBean class

    /**
     * Creates a new MBean instance
     * 
     * @return the newly created MBean
     * @throws AMQException
     *             if something goes wrong
     */
    protected ExchangeMBean createMBean() throws AMQException
    {
        try
        {
            return new DiagnosticExchange.DiagnosticExchangeMBean();
        }
        catch (JMException ex)
        {
         //   _logger.error("Exception occured in creating the direct exchange mbean", ex);
            throw new AMQException(null, "Exception occured in creating the direct exchange mbean", ex);
        }
    }

    public AMQShortString getType()
    {
        return DIAGNOSTIC_EXCHANGE_CLASS;
    }

    /**
     * Does nothing.
     * 
     * @param routingKey
     *            pointless
     * @param queue
     *            pointless
     * @param args
     *            pointless
     * @throws AMQException
     *             never
     */
    public void registerQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        // No op
    }

    /**
     * Does nothing.
     * 
     * @param routingKey
     *            pointless
     * @param queue
     *            pointless
     * @param args
     *            pointless
     * @throws AMQException
     *             never
     */
    public void deregisterQueue(AMQShortString routingKey, AMQQueue queue, FieldTable args) throws AMQException
    {
        // No op
    }

    public boolean isBound(AMQShortString routingKey, AMQQueue queue)
    {
        return false;
    }

    public boolean isBound(AMQShortString routingKey)
    {
        return false;
    }

    public boolean isBound(AMQQueue queue)
    {
        return false;
    }

    public boolean hasBindings()
    {
        return false;
    }

    public void route(IncomingMessage payload) throws AMQException
    {
        
        Long value = new Long(SizeOf.getUsedMemory());
        AMQShortString key = new AMQShortString("memory");
        
        FieldTable headers = ((BasicContentHeaderProperties)payload.getContentHeaderBody().properties).getHeaders();
        headers.put(key, value);
        ((BasicContentHeaderProperties)payload.getContentHeaderBody().properties).setHeaders(headers);
        AMQQueue q = getQueueRegistry().getQueue(new AMQShortString("diagnosticqueue"));

        ArrayList<AMQQueue> queues =  new ArrayList<AMQQueue>();
        queues.add(q);
        payload.enqueue(queues);
        
    }

	
	public boolean isBound(AMQShortString routingKey, FieldTable arguments,
			AMQQueue queue) {
		// TODO Auto-generated method stub
		return false;
	}
}
