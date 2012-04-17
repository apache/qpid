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

import org.apache.qpid.AMQException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.exchange.ExchangeDefaults;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.configuration.ServerConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.exchange.ExchangeFactory;
import org.apache.qpid.server.exchange.ExchangeRegistry;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.HashMap;
import java.util.Map;

public class AMQQueueFactory
{
    public static final String X_QPID_PRIORITIES = "x-qpid-priorities";
    public static final String QPID_LVQ_KEY = "qpid.LVQ_key";
    public static final String QPID_LAST_VALUE_QUEUE = "qpid.last_value_queue";
    public static final String QPID_LAST_VALUE_QUEUE_KEY = "qpid.last_value_queue_key";
    public static final String QPID_QUEUE_SORT_KEY = "qpid.queue_sort_key";

    public static final String DLQ_ROUTING_KEY = "dlq";
    public static final String X_QPID_DLQ_ENABLED = "x-qpid-dlq-enabled";
    public static final String X_QPID_MAXIMUM_DELIVERY_COUNT = "x-qpid-maximum-delivery-count";
    public static final String DEFAULT_DLQ_NAME_SUFFIX = "_DLQ";

    private AMQQueueFactory()
    {
    }

    private abstract static class QueueProperty
    {

        private final AMQShortString _argumentName;


        public QueueProperty(String argumentName)
        {
            _argumentName = new AMQShortString(argumentName);
        }

        public AMQShortString getArgumentName()
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

    private static final QueueProperty[] DECLAREABLE_PROPERTIES = {
            new QueueLongProperty("x-qpid-maximum-message-age")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageAge(value);
                }
            },
            new QueueLongProperty("x-qpid-maximum-message-size")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageSize(value);
                }
            },
            new QueueLongProperty("x-qpid-maximum-message-count")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMaximumMessageCount(value);
                }
            },
            new QueueLongProperty("x-qpid-minimum-alert-repeat-gap")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setMinimumAlertRepeatGap(value);
                }
            },
            new QueueLongProperty("x-qpid-capacity")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setCapacity(value);
                }
            },
            new QueueLongProperty("x-qpid-flow-resume-capacity")
            {
                public void setPropertyValue(AMQQueue queue, long value)
                {
                    queue.setFlowResumeCapacity(value);
                }
            },
            new QueueIntegerProperty(X_QPID_MAXIMUM_DELIVERY_COUNT)
            {
                public void setPropertyValue(AMQQueue queue, int value)
                {
                    queue.setMaximumDeliveryCount(value);
                }
            }
    };


    /** @see #createAMQQueueImpl(String, boolean, String, boolean, boolean, VirtualHost, Map) */
    public static AMQQueue createAMQQueueImpl(AMQShortString name,
                                              boolean durable,
                                              AMQShortString owner,
                                              boolean autoDelete,
                                              boolean exclusive,
                                              VirtualHost virtualHost, final FieldTable arguments) throws AMQException
    {
        return createAMQQueueImpl(name == null ? null : name.toString(),
                                  durable,
                                  owner == null ? null : owner.toString(),
                                  autoDelete,
                                  exclusive,
                                  virtualHost, FieldTable.convertToMap(arguments));
    }


    public static AMQQueue createAMQQueueImpl(String queueName,
                                              boolean durable,
                                              String owner,
                                              boolean autoDelete,
                                              boolean exclusive,
                                              VirtualHost virtualHost, Map<String, Object> arguments) throws AMQSecurityException, AMQException
    {
        if (queueName == null)
        {
            throw new IllegalArgumentException("Queue name must not be null");
        }

        // Access check
        if (!virtualHost.getSecurityManager().authoriseCreateQueue(autoDelete, durable, exclusive, null, null, new AMQShortString(queueName), owner))
        {
            String description = "Permission denied: queue-name '" + queueName + "'";
            throw new AMQSecurityException(description);
        }

        QueueConfiguration queueConfiguration = virtualHost.getConfiguration().getQueueConfiguration(queueName);
        boolean isDLQEnabled = isDLQEnabled(autoDelete, arguments, queueConfiguration);
        if (isDLQEnabled)
        {
            validateDLNames(queueName);
        }

        int priorities = 1;
        String conflationKey = null;
        String sortingKey = null;

        if(arguments != null)
        {
            if(arguments.containsKey(QPID_LAST_VALUE_QUEUE) || arguments.containsKey(QPID_LAST_VALUE_QUEUE_KEY))
            {
                conflationKey = (String) arguments.get(QPID_LAST_VALUE_QUEUE_KEY);
                if(conflationKey == null)
                {
                    conflationKey = QPID_LVQ_KEY;
                }
            }
            else if(arguments.containsKey(X_QPID_PRIORITIES))
            {
                Object prioritiesObj = arguments.get(X_QPID_PRIORITIES);
                if(prioritiesObj instanceof Number)
                {
                    priorities = ((Number)prioritiesObj).intValue();
                }
            }
            else if(arguments.containsKey(QPID_QUEUE_SORT_KEY))
            {
                sortingKey = (String)arguments.get(QPID_QUEUE_SORT_KEY);
            }
        }

        AMQQueue q;
        if(sortingKey != null)
        {
            q = new SortedQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, arguments, sortingKey);
        }
        else if(conflationKey != null)
        {
            q = new ConflationQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, arguments, conflationKey);
        }
        else if(priorities > 1)
        {
            q = new AMQPriorityQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, arguments, priorities);
        }
        else
        {
            q = new SimpleAMQQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, arguments);
        }

        //Register the new queue
        virtualHost.getQueueRegistry().registerQueue(q);
        q.configure(virtualHost.getConfiguration().getQueueConfiguration(queueName));

        if(arguments != null)
        {
            for(QueueProperty p : DECLAREABLE_PROPERTIES)
            {
                if(arguments.containsKey(p.getArgumentName().toString()))
                {
                    p.setPropertyValue(q, arguments.get(p.getArgumentName().toString()));
                }
            }
        }

        if(isDLQEnabled)
        {
            final String dlExchangeName = getDeadLetterExchangeName(queueName);
            final String dlQueueName = getDeadLetterQueueName(queueName);

            final ExchangeRegistry exchangeRegistry = virtualHost.getExchangeRegistry();
            final ExchangeFactory exchangeFactory = virtualHost.getExchangeFactory();
            final QueueRegistry queueRegistry = virtualHost.getQueueRegistry();

            Exchange dlExchange = null;
            synchronized(exchangeRegistry)
            {
                dlExchange = exchangeRegistry.getExchange(dlExchangeName);

                if(dlExchange == null)
                {
                    dlExchange = exchangeFactory.createExchange(new AMQShortString(dlExchangeName), ExchangeDefaults.FANOUT_EXCHANGE_CLASS, true, false, 0);

                    exchangeRegistry.registerExchange(dlExchange);

                    //enter the dle in the persistent store
                    virtualHost.getMessageStore().createExchange(dlExchange);
                }
            }

            AMQQueue dlQueue = null;

            synchronized(queueRegistry)
            {
                dlQueue = queueRegistry.getQueue(dlQueueName);

                if(dlQueue == null)
                {
                    //set args to disable DLQ'ing/MDC from the DLQ itself, preventing loops etc
                    final Map<String, Object> args = new HashMap<String, Object>();
                    args.put(X_QPID_DLQ_ENABLED, false);
                    args.put(X_QPID_MAXIMUM_DELIVERY_COUNT, 0);

                    dlQueue = createAMQQueueImpl(dlQueueName, true, owner, false, exclusive, virtualHost, args);

                    //enter the dlq in the persistent store
                    virtualHost.getMessageStore().createQueue(dlQueue, FieldTable.convertToFieldTable(args));
                }
            }

            //ensure the queue is bound to the exchange
            if(!dlExchange.isBound(DLQ_ROUTING_KEY, dlQueue))
            {
                //actual routing key used does not matter due to use of fanout exchange,
                //but we will make the key 'dlq' as it can be logged at creation.
                virtualHost.getBindingFactory().addBinding(DLQ_ROUTING_KEY, dlQueue, dlExchange, null);
            }
            q.setAlternateExchange(dlExchange);
        }

        return q;
    }

    public static AMQQueue createAMQQueueImpl(QueueConfiguration config, VirtualHost host) throws AMQException
    {
        String queueName = config.getName();

        boolean durable = config.getDurable();
        boolean autodelete = config.getAutoDelete();
        boolean exclusive = config.getExclusive();
        String owner = config.getOwner();
        Map<String,Object> arguments = null;

        if(config.isLVQ() || config.getLVQKey() != null)
        {
            arguments = new HashMap<String,Object>();
            arguments.put(QPID_LAST_VALUE_QUEUE, 1);
            arguments.put(QPID_LAST_VALUE_QUEUE_KEY, config.getLVQKey() == null ? QPID_LVQ_KEY : config.getLVQKey());
        }
        else if (config.getPriority() || config.getPriorities() > 0)
        {
            arguments = new HashMap<String,Object>();
            arguments.put(X_QPID_PRIORITIES, config.getPriorities() < 0 ? 10 : config.getPriorities());
        }
        else if (config.getQueueSortKey() != null && !"".equals(config.getQueueSortKey()))
        {
            arguments = new HashMap<String,Object>();
            arguments.put(QPID_QUEUE_SORT_KEY, config.getQueueSortKey());
        }
        if (!config.getAutoDelete() && config.isDeadLetterQueueEnabled())
        {
            if (arguments == null)
            {
                arguments = new HashMap<String,Object>();
            }
            arguments.put(X_QPID_DLQ_ENABLED, true);
        }

        AMQQueue q = createAMQQueueImpl(queueName, durable, owner, autodelete, exclusive, host, arguments);
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
        if (exchangeName.length() > AMQShortString.MAX_LENGTH)
        {
            throw new IllegalArgumentException("DL exchange name '" + exchangeName
                    + "' length exceeds limit of " + AMQShortString.MAX_LENGTH + " characters for queue " + name);
        }
        String queueName = getDeadLetterQueueName(name);
        if (queueName.length() > AMQShortString.MAX_LENGTH)
        {
            throw new IllegalArgumentException("DLQ queue name '" + queueName + "' length exceeds limit of "
                    + AMQShortString.MAX_LENGTH + " characters for queue " + name);
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
    protected static boolean isDLQEnabled(boolean autoDelete, Map<String, Object> arguments, QueueConfiguration qConfig)
    {
        //feature is not to be enabled for temporary queues or when explicitly disabled by argument
        if (!autoDelete)
        {
            boolean dlqArgumentPresent = arguments != null && arguments.containsKey(X_QPID_DLQ_ENABLED);
            if (dlqArgumentPresent || qConfig.isDeadLetterQueueEnabled())
            {
                boolean dlqEnabled = true;
                if (dlqArgumentPresent)
                {
                    Object argument = arguments.get(X_QPID_DLQ_ENABLED);
                    dlqEnabled = argument instanceof Boolean && ((Boolean)argument).booleanValue();
                }
                return dlqEnabled;
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
        ServerConfiguration serverConfig = ApplicationRegistry.getInstance().getConfiguration();
        String dlQueueName = name + serverConfig.getDeadLetterQueueSuffix();
        return dlQueueName;
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
        ServerConfiguration serverConfig = ApplicationRegistry.getInstance().getConfiguration();
        String dlExchangeName = name + serverConfig.getDeadLetterExchangeSuffix();
        return dlExchangeName;
    }
}
