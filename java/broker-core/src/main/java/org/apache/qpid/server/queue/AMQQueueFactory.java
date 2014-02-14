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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.exchange.AMQUnknownExchangeType;
import org.apache.qpid.server.security.QpidSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.exchange.DefaultExchangeFactory;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.store.DurableConfigurationStoreHelper;
import org.apache.qpid.server.util.ConnectionScopedRuntimeException;
import org.apache.qpid.server.util.ServerScopedRuntimeException;
import org.apache.qpid.server.virtualhost.ExchangeExistsException;
import org.apache.qpid.server.virtualhost.ReservedExchangeNameException;
import org.apache.qpid.server.virtualhost.UnknownExchangeException;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.virtualhost.QueueExistsException;

public class AMQQueueFactory implements QueueFactory
{
    public static final String QPID_DEFAULT_LVQ_KEY = "qpid.LVQ_key";


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

    private abstract static class QueueProperty
    {

        private final String _argumentName;


        public QueueProperty(String argumentName)
        {
            _argumentName = argumentName;
        }

        public String getArgumentName()
        {
            return _argumentName;
        }


        public abstract void setPropertyValue(AMQQueue queue, Object value);

    }

    private abstract static class QueueLongProperty extends QueueProperty
    {

        public QueueLongProperty(String argumentName)
        {
            super(argumentName);
        }

        public void setPropertyValue(AMQQueue queue, Object value)
        {
            if(value instanceof Number)
            {
                setPropertyValue(queue, ((Number)value).longValue());
            }

        }

        abstract void setPropertyValue(AMQQueue queue, long value);


    }

    private abstract static class QueueIntegerProperty extends QueueProperty
    {
        public QueueIntegerProperty(String argumentName)
        {
            super(argumentName);
        }

        public void setPropertyValue(AMQQueue queue, Object value)
        {
            if(value instanceof Number)
            {
                setPropertyValue(queue, ((Number)value).intValue());
            }

        }
        abstract void setPropertyValue(AMQQueue queue, int value);
    }

    private static final QueueProperty[] DECLARABLE_PROPERTIES = {
            new QueueLongProperty(Queue.ALERT_THRESHOLD_MESSAGE_AGE)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageAge(value);
                }
            },
            new QueueLongProperty(Queue.ALERT_THRESHOLD_MESSAGE_SIZE)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageSize(value);
                }
            },
            new QueueLongProperty(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_MESSAGES)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageCount(value);
                }
            },
            new QueueLongProperty(Queue.ALERT_THRESHOLD_QUEUE_DEPTH_BYTES)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumQueueDepth(value);
                }
            },
            new QueueLongProperty(Queue.ALERT_REPEAT_GAP)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMinimumAlertRepeatGap(value);
                }
            },
            new QueueLongProperty(Queue.QUEUE_FLOW_CONTROL_SIZE_BYTES)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setCapacity(value);
                }
            },
            new QueueLongProperty(Queue.QUEUE_FLOW_RESUME_SIZE_BYTES)
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setFlowResumeCapacity(value);
                }
            },
            new QueueIntegerProperty(Queue.MAXIMUM_DELIVERY_ATTEMPTS)
            {
                public void setPropertyValue(AMQQueue queue, int value)
                {
                    queue.setMaximumDeliveryCount(value);
                }
            }
    };

    @Override
    public AMQQueue restoreQueue(UUID id,
                                 String queueName,
                                 String owner,
                                 boolean autoDelete,
                                 boolean exclusive,
                                 boolean deleteOnNoConsumer,
                                 Map<String, Object> arguments) throws QpidSecurityException
    {
        return createOrRestoreQueue(id, queueName, true, owner, autoDelete, exclusive, deleteOnNoConsumer, arguments, false);

    }

    /**
     * @param id the id to use.
     * @param deleteOnNoConsumer
     */
    @Override
    public AMQQueue createQueue(UUID id,
                                String queueName,
                                boolean durable,
                                String owner,
                                boolean autoDelete,
                                boolean exclusive,
                                boolean deleteOnNoConsumer,
                                Map<String, Object> arguments) throws QpidSecurityException
    {
        return createOrRestoreQueue(id, queueName, durable, owner, autoDelete, exclusive, deleteOnNoConsumer, arguments, true);
    }

    private AMQQueue createOrRestoreQueue(UUID id,
                                          String queueName,
                                          boolean durable,
                                          String owner,
                                          boolean autoDelete,
                                          boolean exclusive,
                                          boolean deleteOnNoConsumer,
                                          Map<String, Object> arguments,
                                          boolean createInStore) throws QpidSecurityException
    {
        if (id == null)
        {
            throw new IllegalArgumentException("Queue id must not be null");
        }
        if (queueName == null)
        {
            throw new IllegalArgumentException("Queue name must not be null");
        }


        QueueConfiguration queueConfiguration = _virtualHost.getConfiguration().getQueueConfiguration(queueName);

        boolean createDLQ = createDLQ(autoDelete, arguments, queueConfiguration);
        if (createDLQ)
        {
            validateDLNames(queueName);
        }

        int priorities = 1;
        String conflationKey = null;
        String sortingKey = null;

        if(arguments != null)
        {
            if(arguments.containsKey(Queue.LVQ_KEY))
            {
                conflationKey = (String) arguments.get(Queue.LVQ_KEY);
                if(conflationKey == null)
                {
                    conflationKey = QPID_DEFAULT_LVQ_KEY;
                }
            }
            else if(arguments.containsKey(Queue.PRIORITIES))
            {
                Object prioritiesObj = arguments.get(Queue.PRIORITIES);
                if(prioritiesObj instanceof Number)
                {
                    priorities = ((Number)prioritiesObj).intValue();
                }
                else if(prioritiesObj instanceof String)
                {
                    try
                    {
                        priorities = Integer.parseInt(prioritiesObj.toString());
                    }
                    catch (NumberFormatException e)
                    {
                        // TODO - should warn here of invalid format
                    }
                }
                else
                {
                    // TODO - should warn here of invalid format
                }
            }
            else if(arguments.containsKey(Queue.SORT_KEY))
            {
                sortingKey = (String)arguments.get(Queue.SORT_KEY);
            }
        }

        AMQQueue q;
        if(sortingKey != null)
        {
            q = new SortedQueue(id, queueName, durable, owner, autoDelete, exclusive, _virtualHost, arguments, sortingKey);
        }
        else if(conflationKey != null)
        {
            q = new ConflationQueue(id, queueName, durable, owner, autoDelete, exclusive, _virtualHost, arguments, conflationKey);
        }
        else if(priorities > 1)
        {
            q = new PriorityQueue(id, queueName, durable, owner, autoDelete, exclusive, _virtualHost, arguments, priorities);
        }
        else
        {
            q = new StandardQueue(id, queueName, durable, owner, autoDelete, exclusive, _virtualHost, arguments);
        }

        q.setDeleteOnNoConsumers(deleteOnNoConsumer);

        //Register the new queue
        _queueRegistry.registerQueue(q);

        q.configure(_virtualHost.getConfiguration().getQueueConfiguration(queueName));

        if(arguments != null)
        {
            for(QueueProperty p : DECLARABLE_PROPERTIES)
            {
                if(arguments.containsKey(p.getArgumentName()))
                {
                    p.setPropertyValue(q, arguments.get(p.getArgumentName()));
                }
            }

            if(arguments.get(Queue.NO_LOCAL) instanceof Boolean)
            {
                q.setNoLocal((Boolean)arguments.get(Queue.NO_LOCAL));
            }

        }

        if(createDLQ)
        {
            final String dlExchangeName = getDeadLetterExchangeName(queueName);
            final String dlQueueName = getDeadLetterQueueName(queueName);

            Exchange dlExchange = null;
            final UUID dlExchangeId = UUIDGenerator.generateExchangeUUID(dlExchangeName, _virtualHost.getName());

            try
            {
                dlExchange = _virtualHost.createExchange(dlExchangeId,
                                                                dlExchangeName,
                                                                ExchangeDefaults.FANOUT_EXCHANGE_CLASS,
                                                                true, false, null);
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
                        dlQueue = _virtualHost.createQueue(UUIDGenerator.generateQueueUUID(dlQueueName, _virtualHost.getName()), dlQueueName, true, owner, false, exclusive,
                                false, args);
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
            q.setAlternateExchange(dlExchange);
        }
        else if(arguments != null && arguments.get(Queue.ALTERNATE_EXCHANGE) instanceof String)
        {

            final String altExchangeAttr = (String) arguments.get(Queue.ALTERNATE_EXCHANGE);
            Exchange altExchange;
            try
            {
                altExchange = _virtualHost.getExchange(UUID.fromString(altExchangeAttr));
            }
            catch(IllegalArgumentException e)
            {
                altExchange = _virtualHost.getExchange(altExchangeAttr);
            }
            q.setAlternateExchange(altExchange);
        }

        if (createInStore && q.isDurable() && !q.isAutoDelete())
        {
            DurableConfigurationStoreHelper.createQueue(_virtualHost.getDurableConfigurationStore(), q);
        }

        return q;
    }

    public AMQQueue createAMQQueueImpl(QueueConfiguration config) throws QpidSecurityException
    {
        String queueName = config.getName();

        boolean durable = config.getDurable();
        boolean autodelete = config.getAutoDelete();
        boolean exclusive = config.getExclusive();
        String owner = config.getOwner();
        Map<String, Object> arguments = createQueueArgumentsFromConfig(config);

        // we need queues that are defined in config to have deterministic ids.
        UUID id = UUIDGenerator.generateQueueUUID(queueName, _virtualHost.getName());

        AMQQueue q = createQueue(id, queueName, durable, owner, autodelete, exclusive, false, arguments);
        q.configure(config);
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
     * @param autoDelete
     *            queue auto-delete flag
     * @param arguments
     *            queue arguments
     * @param qConfig
     *            queue configuration
     * @return true if DLQ enabled
     */
    protected static boolean createDLQ(boolean autoDelete, Map<String, Object> arguments, QueueConfiguration qConfig)
    {
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

    private static Map<String, Object> createQueueArgumentsFromConfig(QueueConfiguration config)
    {
        Map<String,Object> arguments = new HashMap<String,Object>();

        if(config.getArguments() != null && !config.getArguments().isEmpty())
        {
            arguments.putAll(QueueArgumentsConverter.convertWireArgsToModel(new HashMap<String, Object>(config.getArguments())));
        }

        if(config.isLVQ() || config.getLVQKey() != null)
        {
            arguments.put(Queue.LVQ_KEY, config.getLVQKey() == null ? QPID_DEFAULT_LVQ_KEY : config.getLVQKey());
        }
        else if (config.getPriority() || config.getPriorities() > 0)
        {
            arguments.put(Queue.PRIORITIES, config.getPriorities() < 0 ? 10 : config.getPriorities());
        }
        else if (config.getQueueSortKey() != null && !"".equals(config.getQueueSortKey()))
        {
            arguments.put(Queue.SORT_KEY, config.getQueueSortKey());
        }

        if (!config.getAutoDelete() && config.isDeadLetterQueueEnabled())
        {
            arguments.put(Queue.CREATE_DLQ_ON_CREATION, true);
        }

        if (config.getDescription() != null && !"".equals(config.getDescription()))
        {
            arguments.put(Queue.DESCRIPTION, config.getDescription());
        }

        if (arguments.isEmpty())
        {
            return Collections.emptyMap();
        }
        else
        {
            return arguments;
        }
    }

}
