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
package org.apache.qpid.server;

import org.apache.qpid.server.management.MBeanDescription;
import org.apache.qpid.server.management.AMQManagedObject;
import org.apache.qpid.server.management.ManagedBroker;
import org.apache.qpid.server.management.MBeanConstructor;
import org.apache.qpid.server.management.ManagedObject;
import org.apache.qpid.server.queue.QueueRegistry;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.registry.IApplicationRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.AMQException;

import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;

/**
 * This MBean implements the broker management interface and exposes the
 * Broker level management features like creating and deleting exchanges and queue.
 */
@MBeanDescription("This MBean exposes the broker level management features")
public class AMQBrokerManagerMBean extends AMQManagedObject implements ManagedBroker
{
    private final QueueRegistry _queueRegistry;
    private final ExchangeRegistry _exchangeRegistry;
    private final ExchangeFactory _exchangeFactory;
    private final MessageStore _messageStore;

    @MBeanConstructor("Creates the Broker Manager MBean")
    public AMQBrokerManagerMBean() throws JMException
    {
        super(ManagedBroker.class, ManagedBroker.TYPE);
        IApplicationRegistry appRegistry = ApplicationRegistry.getInstance();
        _queueRegistry = appRegistry.getQueueRegistry();
        _exchangeRegistry = appRegistry.getExchangeRegistry();
        _exchangeFactory = ApplicationRegistry.getInstance().getExchangeFactory();
        _messageStore = ApplicationRegistry.getInstance().getMessageStore();
    }

    public String getObjectInstanceName()
    {
        return this.getClass().getName();
    }

    /**
     * Creates new exchange and registers it with the registry.
     *
     * @param exchangeName
     * @param type
     * @param durable
     * @param autoDelete
     * @throws JMException
     */
    public void createNewExchange(String exchangeName, String type, boolean durable, boolean autoDelete)
            throws JMException
    {
        try
        {
            synchronized (_exchangeRegistry)
            {
                Exchange exchange = _exchangeRegistry.getExchange(exchangeName);
                if (exchange == null)
                {
                    exchange = _exchangeFactory.createExchange(exchangeName, type, durable, autoDelete, 0);
                    _exchangeRegistry.registerExchange(exchange);
                }
                else
                {
                    throw new JMException("The exchange \"" + exchangeName + "\" already exists.");
                }
            }
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, "Error in creating exchange " + exchangeName);
        }
    }

    /**
     * Unregisters the exchange from registry.
     *
     * @param exchangeName
     * @throws JMException
     */
    public void unregisterExchange(String exchangeName) throws JMException
    {
        // TODO
        // Check if the exchange is in use.
        // boolean inUse = false;
        // Check if there are queue-bindings with the exchange and unregister
        // when there are no bindings.
        try
        {
            _exchangeRegistry.unregisterExchange(exchangeName, false);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, "Error in unregistering exchange " + exchangeName);
        }
    }

    /**
     * Creates a new queue and registers it with the registry and puts it
     * in persistance storage if durable queue.
     *
     * @param queueName
     * @param durable
     * @param owner
     * @param autoDelete
     * @throws JMException
     */
    public void createNewQueue(String queueName, boolean durable, String owner, boolean autoDelete)
            throws JMException
    {
        AMQQueue queue = _queueRegistry.getQueue(queueName);
        if (queue != null)
        {
            throw new JMException("The queue \"" + queueName + "\" already exists.");
        }

        try
        {
            queue = new AMQQueue(queueName, durable, owner, autoDelete, _queueRegistry);
            if (queue.isDurable() && !queue.isAutoDelete())
            {
                _messageStore.createQueue(queue);
            }
            _queueRegistry.registerQueue(queue);
        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex,"Error in creating queue " + queueName);
        }
    }

    /**
     * Deletes the queue from queue registry and persistant storage.
     *
     * @param queueName
     * @throws JMException
     */
    public void deleteQueue(String queueName) throws JMException
    {
        AMQQueue queue = _queueRegistry.getQueue(queueName);
        if (queue == null)
        {
            throw new JMException("The Queue " + queueName + " is not a registerd queue.");
        }

        try
        {
            queue.delete();
            _messageStore.removeQueue(queueName);

        }
        catch (AMQException ex)
        {
            throw new MBeanException(ex, ex.toString());
        }
    }

    public ObjectName getObjectName() throws MalformedObjectNameException
    {
        StringBuffer objectName = new StringBuffer(ManagedObject.DOMAIN);
        objectName.append(":type=").append(getType());

        return new ObjectName(objectName.toString());
    }
} // End of MBean class
