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
import org.apache.qpid.protocol.AMQConstant;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class AMQQueueFactory
{
    public static final AMQShortString X_QPID_PRIORITIES = new AMQShortString("x-qpid-priorities");
    public static final AMQShortString QPID_MAX_COUNT = new AMQShortString("qpid.max_count");
    public static final AMQShortString QPID_MAX_SIZE = new AMQShortString("qpid.max_size");
    public static final AMQShortString QPID_POLICY_TYPE = new AMQShortString("qpid.policy_type");
    public static final String QPID_FLOW_TO_DISK = "flow_to_disk";

    public static AMQQueue createAMQQueueImpl(AMQShortString name,
                                              boolean durable,
                                              AMQShortString owner,
                                              boolean autoDelete,
                                              VirtualHost virtualHost, final FieldTable arguments)
            throws AMQException
    {

        int priorities = 1;

        if (arguments != null && arguments.containsKey(X_QPID_PRIORITIES))
        {
            Integer priority = arguments.getInteger(X_QPID_PRIORITIES);

            if (priority != null)
            {
                priorities = priority.intValue();
            }
            else
            {
                throw new AMQException(AMQConstant.INVALID_ARGUMENT,
                                       "Queue create request with non integer value for :" + X_QPID_PRIORITIES + "=" + arguments.get(X_QPID_PRIORITIES), null);
            }

        }

        AMQQueue q = null;
        if (priorities > 1)
        {
            q = new AMQPriorityQueue(name, durable, owner, autoDelete, virtualHost, priorities);
        }
        else
        {
            q = new SimpleAMQQueue(name, durable, owner, autoDelete, virtualHost);
        }

        final String queuePolicyType = arguments == null ? null :
                                       arguments.containsKey(QPID_POLICY_TYPE) ? arguments.getString(QPID_POLICY_TYPE) : null;

        if (queuePolicyType != null)
        {
            if (queuePolicyType.equals(QPID_FLOW_TO_DISK))
            {
                if (arguments.containsKey(QPID_MAX_SIZE))
                {

                    final long queueSize = arguments.getInteger(QPID_MAX_SIZE);

                    if (queueSize < 0)
                    {
                        throw new AMQException(AMQConstant.INVALID_ARGUMENT,
                                               "Queue create request with negative size:" + queueSize, null);
                    }

                    q.setMemoryUsageMaximum(queueSize);
                }
                else
                {
                    throw new AMQException(AMQConstant.INVALID_ARGUMENT,
                                           "Queue create request with no qpid.max_size value,", null);
                }
            }
            else
            {
                throw new AMQException(AMQConstant.NOT_IMPLEMENTED,
                                       "Queue create request with unknown Policy Type:" + queuePolicyType, null);
            }

        }

        //Register the new queue
        virtualHost.getQueueRegistry().registerQueue(q);
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
        if (priority || priorities > 0)
        {
            if (arguments == null)
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
}
