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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.MapValueConverter;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.UnknownExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public class AMQQueueFactory implements QueueFactory
{


    public static final String DEFAULT_DLQ_NAME_SUFFIX = "_DLQ";
    public static final String DLQ_ROUTING_KEY = "dlq";
    private static final int MAX_LENGTH = 255;

    private final VirtualHostImpl _virtualHost;
    private final QueueRegistry _queueRegistry;

    public AMQQueueFactory(VirtualHostImpl virtualHost, QueueRegistry queueRegistry)
    {
        _virtualHost = virtualHost;
        _queueRegistry = queueRegistry;
    }

    @Override
    public AMQQueue restoreQueue(Map<String, Object> attributes)
    {
        return createOrRestoreQueue(attributes, false);

    }

    @Override
    public AMQQueue createQueue(Map<String, Object> attributes)
    {
        return createOrRestoreQueue(attributes, true);
    }

    private AMQQueue createOrRestoreQueue(Map<String, Object> attributes, boolean createInStore)
    {
        String queueName = MapValueConverter.getStringAttribute(Queue.NAME,attributes);
        boolean createDLQ = createInStore && shouldCreateDLQ(attributes, _virtualHost.getDefaultDeadLetterQueueEnabled());
        if (createDLQ)
        {
            validateDLNames(queueName);
        }

        AMQQueue queue;

        if(attributes.containsKey(Queue.SORT_KEY))
        {
            queue = new SortedQueue(_virtualHost, attributes);
        }
        else if(attributes.containsKey(Queue.LVQ_KEY))
        {
            queue = new ConflationQueue(_virtualHost, attributes);
        }
        else if(attributes.containsKey(Queue.PRIORITIES))
        {
            queue = new PriorityQueue(_virtualHost, attributes);
        }
        else
        {
            queue = new StandardQueue(_virtualHost, attributes);
        }
        queue.open();
        //Register the new queue
        _queueRegistry.registerQueue(queue);

        if(createDLQ)
        {
            createDLQ(queue);
        }
        else if(attributes != null && attributes.get(Queue.ALTERNATE_EXCHANGE) instanceof String)
        {

            final String altExchangeAttr = (String) attributes.get(Queue.ALTERNATE_EXCHANGE);
            ExchangeImpl altExchange;
            try
            {
                altExchange = _virtualHost.getExchange(UUID.fromString(altExchangeAttr));
            }
            catch(IllegalArgumentException e)
            {
                altExchange = _virtualHost.getExchange(altExchangeAttr);
            }
            queue.setAlternateExchange(altExchange);
        }

        if (createInStore && queue.isDurable() && !(queue.getLifetimePolicy()
                                                    == LifetimePolicy.DELETE_ON_CONNECTION_CLOSE
                                                    || queue.getLifetimePolicy()
                                                       == LifetimePolicy.DELETE_ON_SESSION_END))
        {
            DurableConfigurationStoreHelper.createQueue(_virtualHost.getDurableConfigurationStore(), queue);
        }

        return queue;
    }

    private void createDLQ(final AMQQueue queue)
    {
        final String queueName = queue.getName();
        final String dlExchangeName = getDeadLetterExchangeName(queueName);
        final String dlQueueName = getDeadLetterQueueName(queueName);

        ExchangeImpl dlExchange = null;
        final UUID dlExchangeId = UUIDGenerator.generateExchangeUUID(dlExchangeName, _virtualHost.getName());

        try
        {
            Map<String,Object> attributes = new HashMap<String, Object>();

            attributes.put(org.apache.qpid.server.model.Exchange.ID, dlExchangeId);
            attributes.put(org.apache.qpid.server.model.Exchange.NAME, dlExchangeName);
            attributes.put(org.apache.qpid.server.model.Exchange.TYPE, ExchangeDefaults.FANOUT_EXCHANGE_CLASS);
            attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, true);
            attributes.put(org.apache.qpid.server.model.Exchange.LIFETIME_POLICY,
                           false ? LifetimePolicy.DELETE_ON_NO_LINKS : LifetimePolicy.PERMANENT);
            attributes.put(org.apache.qpid.server.model.Exchange.ALTERNATE_EXCHANGE, null);
            dlExchange = _virtualHost.createExchange(attributes);
        }
        catch(ExchangeExistsException e)
        {
            // We're ok if the exchange already exists
            dlExchange = e.getExistingExchange();
        }
        catch (ReservedExchangeNameException e)
        {
            throw new ConnectionScopedRuntimeException("Attempt to create an alternate exchange for a queue failed",e);
        }
        catch (AMQUnknownExchangeType e)
        {
            throw new ConnectionScopedRuntimeException("Attempt to create an alternate exchange for a queue failed",e);
        }
        catch (UnknownExchangeException e)
        {
            throw new ConnectionScopedRuntimeException("Attempt to create an alternate exchange for a queue failed",e);
        }

        AMQQueue dlQueue = null;

        synchronized(_queueRegistry)
        {
            dlQueue = _queueRegistry.getQueue(dlQueueName);

            if(dlQueue == null)
            {
                //set args to disable DLQ-ing/MDC from the DLQ itself, preventing loops etc
                final Map<String, Object> args = new HashMap<String, Object>();
                args.put(Queue.CREATE_DLQ_ON_CREATION, false);
                args.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, 0);

                try
                {


                    args.put(Queue.ID, UUIDGenerator.generateQueueUUID(dlQueueName, _virtualHost.getName()));
                    args.put(Queue.NAME, dlQueueName);
                    args.put(Queue.DURABLE, true);
                    dlQueue = _virtualHost.createQueue(args);
                }
                catch (QueueExistsException e)
                {
                    throw new ServerScopedRuntimeException("Attempt to create a queue failed because the " +
                                                           "queue already exists, however this occurred within " +
                                                           "a block where the queue existence had previously been " +
                                                           "checked, and no queue creation should have been " +
                                                           "possible from another thread", e);
                }
            }
        }

        //ensure the queue is bound to the exchange
        if(!dlExchange.isBound(DLQ_ROUTING_KEY, dlQueue))
        {
            //actual routing key used does not matter due to use of fanout exchange,
            //but we will make the key 'dlq' as it can be logged at creation.
            dlExchange.addBinding(DLQ_ROUTING_KEY, dlQueue, null);
        }
        queue.setAlternateExchange(dlExchange);
    }

    private static void validateDLNames(String name)
    {
        // check if DLQ name and DLQ exchange name do not exceed 255
        String exchangeName = getDeadLetterExchangeName(name);
        if (exchangeName.length() > MAX_LENGTH)
        {
            throw new IllegalArgumentException("DL exchange name '" + exchangeName
                    + "' length exceeds limit of " + MAX_LENGTH + " characters for queue " + name);
        }
        String queueName = getDeadLetterQueueName(name);
        if (queueName.length() > MAX_LENGTH)
        {
            throw new IllegalArgumentException("DLQ queue name '" + queueName + "' length exceeds limit of "
                    + MAX_LENGTH + " characters for queue " + name);
        }
    }

    private static boolean shouldCreateDLQ(Map<String, Object> arguments, boolean virtualHostDefaultDeadLetterQueueEnabled)
    {
        boolean autoDelete = MapValueConverter.getEnumAttribute(LifetimePolicy.class,
                                                                Queue.LIFETIME_POLICY,
                                                                arguments,
                                                                LifetimePolicy.PERMANENT) != LifetimePolicy.PERMANENT;

        //feature is not to be enabled for temporary queues or when explicitly disabled by argument
        if (!(autoDelete || (arguments != null && arguments.containsKey(Queue.ALTERNATE_EXCHANGE))))
        {
            boolean dlqArgumentPresent = arguments != null
                                         && arguments.containsKey(Queue.CREATE_DLQ_ON_CREATION);
            if (dlqArgumentPresent)
            {
                boolean dlqEnabled = true;
                if (dlqArgumentPresent)
                {
                    Object argument = arguments.get(Queue.CREATE_DLQ_ON_CREATION);
                    dlqEnabled = (argument instanceof Boolean && ((Boolean)argument).booleanValue())
                                || (argument instanceof String && Boolean.parseBoolean(argument.toString()));
                }
                return dlqEnabled;
            }
            return virtualHostDefaultDeadLetterQueueEnabled;
        }
        return false;
    }

    private static String getDeadLetterQueueName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_QUEUE_SUFFIX, DEFAULT_DLQ_NAME_SUFFIX);
    }

    private static String getDeadLetterExchangeName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX, DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
    }

}
