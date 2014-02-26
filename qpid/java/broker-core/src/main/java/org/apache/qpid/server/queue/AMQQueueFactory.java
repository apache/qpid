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
import org.apache.qpid.server.exchange.NonDefaultExchange;
import org.apache.qpid.server.model.ExclusivityPolicy;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.QueueConfiguration;
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
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public class AMQQueueFactory implements QueueFactory
{


    public static final String DEFAULT_DLQ_NAME_SUFFIX = "_DLQ";
    public static final String DLQ_ROUTING_KEY = "dlq";
    private static final int MAX_LENGTH = 255;

    private final VirtualHost _virtualHost;
    private final QueueRegistry _queueRegistry;

    public AMQQueueFactory(VirtualHost virtualHost, QueueRegistry queueRegistry)
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

    private AMQQueue createOrRestoreQueue(Map<String, Object> attributes,
                                          boolean createInStore)
    {


        String queueName = MapValueConverter.getStringAttribute(Queue.NAME,attributes);

        QueueConfiguration config = _virtualHost.getConfiguration().getQueueConfiguration(queueName);

        if (!attributes.containsKey(Queue.ALERT_THRESHOLD_MESSAGE_AGE) && config.getMaximumMessageAge() != 0)
        {
            attributes.put(Queue.ALERT_THRESHOLD_MESSAGE_AGE, config.getMaximumMessageAge());
        }
        if (!attributes.containsKey(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES) && config.getMaximumQueueDepth() != 0)
        {
            attributes.put(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES, config.getMaximumQueueDepth());
        }
        if (!attributes.containsKey(Queue.ALERT_THRESHOLD_MESSAGE_SIZE) && config.getMaximumMessageSize() != 0)
        {
            attributes.put(Queue.ALERT_THRESHOLD_MESSAGE_SIZE, config.getMaximumMessageSize());
        }
        if (!attributes.containsKey(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES) && config.getMaximumMessageCount() != 0)
        {
            attributes.put(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES, config.getMaximumMessageCount());
        }
        if (!attributes.containsKey(Queue.ALERT_REPEAT_GAP) && config.getMinimumAlertRepeatGap() != 0)
        {
            attributes.put(Queue.ALERT_REPEAT_GAP, config.getMinimumAlertRepeatGap());
        }
        if (config.getMaxDeliveryCount() != 0 && !attributes.containsKey(Queue.MAXIMUM_DELIVERY_ATTEMPTS))
        {
            attributes.put(Queue.MAXIMUM_DELIVERY_ATTEMPTS, config.getMaxDeliveryCount());
        }
        if (!attributes.containsKey(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES) && config.getCapacity() != 0)
        {
            attributes.put(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES, config.getCapacity());
        }
        if (!attributes.containsKey(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES) && config.getFlowResumeCapacity() != 0)
        {
            attributes.put(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES, config.getFlowResumeCapacity());
        }


        boolean createDLQ = createDLQ(attributes, config);
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

        //Register the new queue
        _queueRegistry.registerQueue(queue);

        if(createDLQ)
        {
            createDLQ(queue);
        }
        else if(attributes != null && attributes.get(Queue.ALTERNATE_EXCHANGE) instanceof String)
        {

            final String altExchangeAttr = (String) attributes.get(Queue.ALTERNATE_EXCHANGE);
            NonDefaultExchange altExchange;
            try
            {
                altExchange = (NonDefaultExchange) _virtualHost.getExchange(UUID.fromString(altExchangeAttr));
            }
            catch(IllegalArgumentException e)
            {
                altExchange = (NonDefaultExchange) _virtualHost.getExchange(altExchangeAttr);
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

        NonDefaultExchange dlExchange = null;
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
            dlExchange = (NonDefaultExchange) e.getExistingExchange();
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

    public AMQQueue createAMQQueueImpl(QueueConfiguration config)
    {

        Map<String, Object> arguments = createQueueAttributesFromConfig(_virtualHost, config);
        
        AMQQueue q = createOrRestoreQueue(arguments, false);
        return q;
    }

    /**
     * Validates DLQ and DLE names
     * <p>
     * DLQ name and DLQ exchange name need to be validated in order to keep
     * integrity in cases when queue name passes validation check but DLQ name
     * or DL exchange name fails to pass it. Otherwise, we might have situations
     * when queue is created but DL exchange or/and DLQ creation fail.
     * <p>
     *
     * @param name
     *            queue name
     * @throws IllegalArgumentException
     *             thrown if length of queue name or exchange name exceed 255
     */
    protected static void validateDLNames(String name)
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

    /**
     * Checks if DLQ is enabled for the queue.
     *
     * @param arguments
     *            queue arguments
     * @param qConfig
     *            queue configuration
     * @return true if DLQ enabled
     */
    protected static boolean createDLQ(Map<String, Object> arguments, QueueConfiguration qConfig)
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
            if (dlqArgumentPresent || qConfig.isDeadLetterQueueEnabled())
            {
                boolean dlqEnabled = true;
                if (dlqArgumentPresent)
                {
                    Object argument = arguments.get(Queue.CREATE_DLQ_ON_CREATION);
                    dlqEnabled = (argument instanceof Boolean && ((Boolean)argument).booleanValue())
                                || (argument instanceof String && Boolean.parseBoolean(argument.toString()));
                }
                return dlqEnabled ;
            }
        }
        return false;
    }

    /**
     * Generates a dead letter queue name for a given queue name
     *
     * @param name
     *            queue name
     * @return DLQ name
     */
    protected static String getDeadLetterQueueName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_QUEUE_SUFFIX, DEFAULT_DLQ_NAME_SUFFIX);
    }

    /**
     * Generates a dead letter exchange name for a given queue name
     *
     * @param name
     *            queue name
     * @return DL exchange name
     */
    protected static String getDeadLetterExchangeName(String name)
    {
        return name + System.getProperty(BrokerProperties.PROPERTY_DEAD_LETTER_EXCHANGE_SUFFIX, DefaultExchangeFactory.DEFAULT_DLE_NAME_SUFFIX);
    }

    private static Map<String, Object> createQueueAttributesFromConfig(final VirtualHost virtualHost,
                                                                       QueueConfiguration config)
    {
        Map<String,Object> attributes = new HashMap<String,Object>();

        if(config.getArguments() != null && !config.getArguments().isEmpty())
        {
            attributes.putAll(QueueArgumentsConverter.convertWireArgsToModel(new HashMap<String, Object>(config.getArguments())));
        }

        if(config.isLVQ() || config.getLVQKey() != null)
        {
            attributes.put(Queue.LVQ_KEY,
                          config.getLVQKey() == null ? ConflationQueue.DEFAULT_LVQ_KEY : config.getLVQKey());
        }
        else if (config.getPriority() || config.getPriorities() > 0)
        {
            attributes.put(Queue.PRIORITIES, config.getPriorities() < 0 ? 10 : config.getPriorities());
        }
        else if (config.getQueueSortKey() != null && !"".equals(config.getQueueSortKey()))
        {
            attributes.put(Queue.SORT_KEY, config.getQueueSortKey());
        }

        if (!config.getAutoDelete() && config.isDeadLetterQueueEnabled())
        {
            attributes.put(Queue.CREATE_DLQ_ON_CREATION, true);
        }

        if (config.getDescription() != null && !"".equals(config.getDescription()))
        {
            attributes.put(Queue.DESCRIPTION, config.getDescription());
        }

        attributes.put(Queue.DURABLE, config.getDurable());
        attributes.put(Queue.LIFETIME_POLICY,
                      config.getAutoDelete() ? LifetimePolicy.DELETE_ON_NO_OUTBOUND_LINKS : LifetimePolicy.PERMANENT);
        if(config.getExclusive())
        {
            attributes.put(Queue.EXCLUSIVE, ExclusivityPolicy.CONTAINER);
        }
        if(config.getOwner() != null)
        {
            attributes.put(Queue.OWNER, config.getOwner());
        }
        
        attributes.put(Queue.NAME, config.getName());
        
        // we need queues that are defined in config to have deterministic ids.
        attributes.put(Queue.ID, UUIDGenerator.generateQueueUUID(config.getName(), virtualHost.getName()));


        return attributes;
    }

}
