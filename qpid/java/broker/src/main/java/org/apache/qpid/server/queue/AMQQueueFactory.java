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
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.virtualhost.VirtualHost;

import java.util.Map;


public class AMQQueueFactory
{
    public static final AMQShortString X_QPID_PRIORITIES = new AMQShortString("x-qpid-priorities");

    public static AMQQueue createAMQQueueImpl(AMQShortString name,
                                              boolean durable,
                                              AMQShortString owner,
                                              boolean autoDelete,
                                              VirtualHost virtualHost, final FieldTable arguments)
            throws AMQException
    {
        final int priorities = arguments == null ? 1 : arguments.containsKey(X_QPID_PRIORITIES) ? arguments.getInteger(X_QPID_PRIORITIES) : 1;

        AMQQueue q = null;
        if(priorities > 1)
        {
            q = new AMQPriorityQueue(name, durable, owner, autoDelete, virtualHost, priorities);
        }
        else
        {
            q = new SimpleAMQQueue(name, durable, owner, autoDelete, virtualHost);
        }

        //Register the new queue
        virtualHost.getQueueRegistry().registerQueue(q);
        q.configure(virtualHost.getConfiguration().getQueueConfiguration(name.asString()));
        return q;
    }

    public static AMQQueue createAMQQueueImpl(QueueConfiguration config, VirtualHost host) throws AMQException
    {
        AMQShortString queueName = new AMQShortString(config.getName());

        boolean durable = config.getDurable();
        boolean autodelete = config.getAutoDelete();
        AMQShortString owner = (config.getOwner() != null) ? new AMQShortString(config.getOwner()) : null;
        FieldTable arguments = null;
        boolean priority = config.getPriority();
        int priorities = config.getPriorities();
        if(priority || priorities > 0)
        {
            if(arguments == null)
            {
                arguments = new FieldTable();
            }
            if (priorities < 0)
            {
                priorities = 10;
            }
            arguments.put(new AMQShortString("x-qpid-priorities"), priorities);
        }

        AMQQueue q = createAMQQueueImpl(queueName, durable, owner, autodelete, host, arguments);
        q.configure(config);
        return q;
    }

    public static AMQQueue createAMQQueueImpl(String queueName,
                                              boolean durable,
                                              String owner,
                                              boolean autoDelete,
                                              VirtualHost virtualHost, Map<String, Object> arguments)
            throws AMQException
    {
        int priorities = 1;
        if(arguments.containsKey(X_QPID_PRIORITIES))
        {
            Object prioritiesObj = arguments.get(X_QPID_PRIORITIES);
            if(prioritiesObj instanceof Number)
            {
                priorities = ((Number)prioritiesObj).intValue();
            }
        }


        AMQQueue q = null;
        if(priorities > 1)
        {
            q = new AMQPriorityQueue(queueName, durable, owner, autoDelete, virtualHost, priorities);
        }
        else
        {
            q = new SimpleAMQQueue(queueName, durable, owner, autoDelete, virtualHost);
        }

        //Register the new queue
        virtualHost.getQueueRegistry().registerQueue(q);
        q.configure(virtualHost.getConfiguration().getQueueConfiguration(queueName));
        return q;

    }
}
