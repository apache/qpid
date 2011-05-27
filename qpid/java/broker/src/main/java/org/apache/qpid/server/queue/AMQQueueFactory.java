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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.configuration.QueueConfiguration;

import java.util.Map;
import java.util.HashMap;

public class AMQQueueFactory
{
    public static final AMQShortString X_QPID_PRIORITIES = new AMQShortString("x-qpid-priorities");
    public static final String QPID_LVQ_KEY = "qpid.LVQ_key";
    public static final String QPID_LAST_VALUE_QUEUE = "qpid.last_value_queue";
    public static final String QPID_LAST_VALUE_QUEUE_KEY = "qpid.last_value_queue_key";

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
                                              VirtualHost virtualHost, Map<String, Object> arguments) throws AMQSecurityException
    {
        // Access check
        if (!virtualHost.getSecurityManager().authoriseCreateQueue(autoDelete, durable, exclusive, null, null, new AMQShortString(queueName), owner))
        {
            String description = "Permission denied: queue-name '" + queueName + "'";
            throw new AMQSecurityException(description);
        }
        
        int priorities = 1;
        String conflationKey = null;
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
            else if(arguments.containsKey(X_QPID_PRIORITIES.toString()))
            {
                Object prioritiesObj = arguments.get(X_QPID_PRIORITIES.toString());
                if(prioritiesObj instanceof Number)
                {
                    priorities = ((Number)prioritiesObj).intValue();
                }
            }
        }

        AMQQueue q;
        if(conflationKey != null)
        {
            q = new ConflationQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, arguments, conflationKey);
        }
        else if(priorities > 1)
        {
            q = new AMQPriorityQueue(queueName, durable, owner, autoDelete, exclusive, virtualHost, priorities, arguments);
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
        else
        {
            boolean priority = config.getPriority();
            int priorities = config.getPriorities();
            if(priority || priorities > 0)
            {
                arguments = new HashMap<String,Object>();
                if (priorities < 0)
                {
                    priorities = 10;
                }
                arguments.put("x-qpid-priorities", priorities);
            }
        }

        AMQQueue q = createAMQQueueImpl(queueName, durable, owner, autodelete, exclusive, host, arguments);
        q.configure(config);
        return q;
    }

}
