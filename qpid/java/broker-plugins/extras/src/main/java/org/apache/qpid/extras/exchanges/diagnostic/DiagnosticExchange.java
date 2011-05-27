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

import java.util.ArrayList;
import java.util.Map;

import javax.management.JMException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.TabularData;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.management.common.mbeans.annotations.MBeanConstructor;
import org.apache.qpid.management.common.mbeans.annotations.MBeanDescription;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.exchange.AbstractExchange;
import org.apache.qpid.server.exchange.AbstractExchangeMBean;
import org.apache.qpid.server.exchange.ExchangeType;
import org.apache.qpid.server.message.InboundMessage;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.virtualhost.VirtualHost;

/**
 * This is a special diagnostic exchange type which doesn't actually do anything
 * with messages. When it receives a message, it writes information about the
 * current memory usage to the "memory" property of the message and places it on the
 * diagnosticqueue for retrieval
 */
public class DiagnosticExchange extends AbstractExchange
{
    private static final Logger _logger = Logger.getLogger(DiagnosticExchange.class);

    public static final AMQShortString DIAGNOSTIC_EXCHANGE_CLASS = new AMQShortString("x-diagnostic");
    public static final AMQShortString DIAGNOSTIC_EXCHANGE_NAME = new AMQShortString("diagnostic");

    /** The logger */
    //private static final Logger _logger = Logger.getLogger(DiagnosticExchange.class);

    /**
     * MBean class implementing the management interfaces.
     */
    @MBeanDescription("Management Bean for Diagnostic Exchange")
    private final class DiagnosticExchangeMBean extends AbstractExchangeMBean<DiagnosticExchange>
    {
        /**
         * Usual constructor.
         *
         * @throws JMException
         */
        @MBeanConstructor("Creates an MBean for AMQ Diagnostic exchange")
        public DiagnosticExchangeMBean() throws JMException
        {
            super(DiagnosticExchange.this);

            init();
        }

        /**
         * Returns nothing, there can be no tabular data for this...
         *
         * @throws OpenDataException
         * @returns null
         * TODO or can there? Could this actually return all the information in one easy to read table?
         */
        @Override
        public TabularData bindings() throws OpenDataException
        {
            return null;
        }

        /**
         * This exchange type doesn't support queues, so this method does
         * nothing.
         *
         * @param queueName the queue you'll fail to create
         * @param binding the binding you'll fail to create
         * @throws JMException an exception that will never be thrown
         */
        @Override
        public void createNewBinding(String queueName, String binding) throws JMException
        {
            // No Op
        }

        /**
         * This exchange type doesn't support queues.
         * 
         * @see #createNewBinding(String, String)
         */
        @Override
        public void removeBinding(String queueName, String binding) throws JMException
        {
            // No Op
        }
    }


    public static final ExchangeType<DiagnosticExchange> TYPE = new ExchangeType<DiagnosticExchange>()
    {

        public AMQShortString getName()
        {
            return DIAGNOSTIC_EXCHANGE_CLASS;
        }

        public Class<DiagnosticExchange> getExchangeClass()
        {
            return DiagnosticExchange.class;
        }

        public DiagnosticExchange newInstance(VirtualHost host,
                                            AMQShortString name,
                                            boolean durable,
                                            int ticket,
                                            boolean autoDelete) throws AMQException
        {
            DiagnosticExchange exch = new DiagnosticExchange();
            exch.initialise(host,name,durable,ticket,autoDelete);
            return exch;
        }

        public AMQShortString getDefaultExchangeName()
        {
            return DIAGNOSTIC_EXCHANGE_NAME ;
        }
    };

    public DiagnosticExchange()
    {
        super(TYPE);
    }

    /**
     * Creates a new MBean instance
     *
     * @return the newly created MBean
     * @throws AMQException
     *             if something goes wrong
     */
    protected AbstractExchangeMBean createMBean() throws JMException
    {
        return new DiagnosticExchange.DiagnosticExchangeMBean();
    }

    public Logger getLogger()
    {
        return _logger;
    }

    public void registerQueue(String routingKey, AMQQueue queue, Map<String, Object> args) throws AMQException
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

    public ArrayList<AMQQueue> doRoute(InboundMessage payload)
    {
        //TODO shouldn't modify messages... perhaps put a new message on the queue?
        /*
        Long value = new Long(SizeOf.getUsedMemory());
        AMQShortString key = new AMQShortString("memory");
        FieldTable headers = ((BasicContentHeaderProperties)payload.getMessageHeader().properties).getHeaders();
        headers.put(key, value);
        ((BasicContentHeaderProperties)payload.getMessageHeader().properties).setHeaders(headers);
        */
        AMQQueue q = getQueueRegistry().getQueue(new AMQShortString("diagnosticqueue"));
        ArrayList<AMQQueue> queues =  new ArrayList<AMQQueue>();
        queues.add(q);
        return queues;
    }


	public boolean isBound(AMQShortString routingKey, FieldTable arguments,
			AMQQueue queue) {
		// TODO Auto-generated method stub
		return false;
	}

    protected void onBind(final Binding binding)
    {
        // No op
    }

    protected void onUnbind(final Binding binding)
    {
        // No op
    }
}
